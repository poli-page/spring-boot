#!/usr/bin/env bash
# Checks the Maven Central bundle that `./gradlew centralBundle` writes to
# build/central/central-bundle.zip, before it is uploaded.
#
#   .github/scripts/check-central-bundle.sh <bundle.zip> <version> <signed|unsigned>
#
# The bundle must hold exactly the three published modules at <version>: for each, the jar, the
# sources and javadoc jars, the POM and the Gradle module file, each with its md5/sha1/sha256/sha512
# checksums and, when signed, its .asc signature (with checksums too). Any other file fails the
# check: test fixtures, the example apps, a second version, maven-metadata.xml. Each main jar must
# also hold only classes and META-INF resources.
set -euo pipefail

if [[ $# -ne 3 || ($3 != signed && $3 != unsigned) ]]; then
  echo "usage: $0 <bundle.zip> <version> <signed|unsigned>" >&2
  exit 2
fi
bundle=$1
version=$2
mode=$3

modules=(poli-page-spring-boot-autoconfigure poli-page-spring-boot-starter poli-page-spring-boot-starter-webflux)

expected=()
for m in "${modules[@]}"; do
  dir="page/poli/$m/$version"
  for f in "$m-$version.jar" "$m-$version-sources.jar" "$m-$version-javadoc.jar" "$m-$version.pom" "$m-$version.module"; do
    files=("$f")
    [[ $mode == signed ]] && files+=("$f.asc")
    for g in "${files[@]}"; do
      expected+=("$dir/$g")
      for c in md5 sha1 sha256 sha512; do
        expected+=("$dir/$g.$c")
      done
    done
  done
done

want=$(printf '%s\n' "${expected[@]}" | LC_ALL=C sort)
actual=$(unzip -Z1 "$bundle" | grep -v '/$' | LC_ALL=C sort)
if [[ "$actual" != "$want" ]]; then
  echo "::error::$bundle does not hold exactly the expected files (< expected, > found):" >&2
  diff <(echo "$want") <(echo "$actual") >&2 || true
  exit 1
fi

scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
for m in "${modules[@]}"; do
  jar="page/poli/$m/$version/$m-$version.jar"
  unzip -q -o "$bundle" "$jar" -d "$scratch"
  stray=$(unzip -Z1 "$scratch/$jar" | grep -v '/$' | grep -Ev '^(META-INF/.+|page/poli/sdk/spring/.+\.class)$' || true)
  if [[ -n "$stray" ]]; then
    echo "::error::$m-$version.jar holds files outside the library's classes and META-INF:" >&2
    echo "$stray" >&2
    exit 1
  fi
done

echo "$bundle: ${#modules[@]} modules at $version, $(echo "$actual" | wc -l | tr -d ' ') files, $mode."
