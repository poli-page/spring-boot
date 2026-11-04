#!/usr/bin/env bash
# .github/scripts/check-release-versions.sh: a release's major.minor must be the
# pinned API contract's and the required page.poli:sdk's.
#
# Usage: .github/scripts/check-release-versions.sh X.Y.Z   (or vX.Y.Z, or a prerelease)
#
# The starters share their major.minor with the Poli Page API, and starter 0.N
# requires page.poli:sdk 0.N.x. This script:
#   - fails unless every page.poli:sdk dependency in the Gradle build files is
#     a plain version X.Y.Z (no range, no dynamic or SNAPSHOT version) with the
#     release's major.minor: the published POM and Gradle module file carry
#     that version;
#   - reads the contract commit pinned in .github/workflows/ci.yml and
#     .github/workflows/release.yml (never its `# vX.Y.Z` comment), requires the
#     two pins to be the same commit, lists the contract repository's tags, and
#     fails unless that commit carries a stable tag vX.Y.Z with the release's
#     major.minor.
# Patch numbers are free.
#
# Run by the Release workflow. API_CONTRACT_REMOTE overrides the contract
# repository (to check against a local one).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REMOTE="${API_CONTRACT_REMOTE:-https://github.com/poli-page/api-contract.git}"

fail() { echo "check-release-versions: $1" >&2; exit 1; }

[ "$#" -eq 1 ] || fail "usage: $(basename "$0") X.Y.Z"
version="${1#v}"
if ! [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]; then
    fail "release version '$1' is not X.Y.Z"
fi
release_minor="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"

# page.poli:sdk: X.Y.Z of the release's major.minor, in every Gradle build file
# that declares it.
declarations="$(
    cd "$REPO_ROOT" &&
        find . -name '*.gradle.kts' -not -path '*/build/*' -not -path './.gradle/*' -print0 |
        xargs -0 grep -HnE '"page\.poli:sdk[:"]' || true
)"
[ -n "$declarations" ] || fail "no Gradle build file declares page.poli:sdk"
while IFS= read -r line; do
    where="${line%%:*}"
    where="${where#./}"
    if ! [[ "$line" =~ \"page\.poli:sdk:([^\"]*)\" ]]; then
        fail "release v$version needs page.poli:sdk $release_minor.x, but $where declares it without a version: $line"
    fi
    sdk="${BASH_REMATCH[1]}"
    if ! [[ "$sdk" =~ ^([0-9]+)\.([0-9]+)\.[0-9]+$ ]]; then
        fail "release v$version needs page.poli:sdk $release_minor.x as a plain X.Y.Z version, but $where requires '$sdk'"
    fi
    sdk_minor="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"
    if [ "$sdk_minor" != "$release_minor" ]; then
        fail "release v$version and the required page.poli:sdk $sdk ($where) differ in major.minor: require page.poli:sdk $release_minor.x, or release v$sdk_minor.x"
    fi
    echo "page.poli:sdk $sdk is required ($where), matching release v$version"
done <<<"$declarations"

# The API contract: one commit, pinned in both workflows.
contract_sha=""
for workflow in .github/workflows/ci.yml .github/workflows/release.yml; do
    pins="$(sed -n 's|.*uses: poli-page/api-contract@\([0-9a-f]\{40\}\).*|\1|p' "$REPO_ROOT/$workflow" | sort -u)"
    [ -n "$pins" ] || fail "no poli-page/api-contract pin found in $workflow"
    [ "$(wc -l <<<"$pins")" -eq 1 ] || fail "$workflow pins poli-page/api-contract at more than one commit: $(tr '\n' ' ' <<<"$pins")"
    if [ -z "$contract_sha" ]; then
        contract_sha="$pins"
    elif [ "$pins" != "$contract_sha" ]; then
        fail ".github/workflows/ci.yml pins poli-page/api-contract at ${contract_sha:0:8} but $workflow at ${pins:0:8}: pin the same commit in both"
    fi
done

tags="$(git ls-remote --tags "$REMOTE")" || fail "cannot list the tags of $REMOTE"

# An annotated tag is listed twice: its own object, then `^{}` with the commit
# it points at. A lightweight tag is listed once, with its commit.
stable_tags="$(
    awk -v sha="$contract_sha" '$1 == sha { ref = $2; sub(/^refs\/tags\//, "", ref); sub(/\^\{\}$/, "", ref); print ref }' <<<"$tags" \
        | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -u -V || true
)"
short_sha="${contract_sha:0:8}"
if [ -z "$stable_tags" ]; then
    fail "release v$version needs the API contract v$release_minor.x, but the pinned contract commit $short_sha has no stable vX.Y.Z tag: pin a released contract"
fi

while IFS= read -r tag; do
    if [[ "$tag" =~ ^v([0-9]+)\.([0-9]+)\. ]] && [ "${BASH_REMATCH[1]}.${BASH_REMATCH[2]}" = "$release_minor" ]; then
        echo "API contract $tag ($short_sha) matches release v$version"
        exit 0
    fi
done <<<"$stable_tags"

pinned="$(tr '\n' ' ' <<<"$stable_tags" | sed 's/ $//')"
# The newest of the commit's tags, which may carry two (an unchanged contract).
[[ "$(tail -n 1 <<<"$stable_tags")" =~ ^v([0-9]+)\.([0-9]+)\. ]]
fail "release v$version and the pinned API contract $pinned ($short_sha) differ in major.minor: pin the contract v$release_minor.x, or release v${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.x"
