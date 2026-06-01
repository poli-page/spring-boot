package page.poli.example.mvc;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves a single-page HTML dashboard at {@code GET /} demonstrating every endpoint exposed by the
 * example app. Plain string return — no template engine dependency.
 *
 * <p>Aesthetic shared with the symfony-bundle / nestjs / laravel example apps: white surface,
 * indigo accent (#4f5d99), Manrope display font, IBM Plex Sans body, JetBrains Mono for code.
 */
@RestController
public class DemoController {

  @GetMapping("/")
  public ResponseEntity<String> dashboard() {
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(HTML);
  }

  private static final String HTML =
      """
      <!doctype html>
      <html lang="en">
      <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width,initial-scale=1">
      <title>Poli Page · Spring Boot Starter Demo</title>
      <link rel="preconnect" href="https://fonts.googleapis.com">
      <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
      <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@500;700&family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
      <style>
        :root {
          --indigo: #4f5d99;
          --indigo-light: #e8ecf6;
          --ink: #1f2937;
          --muted: #6b7280;
          --border: #e5e7eb;
          --bg: #f9fafb;
          --ok: #16a34a;
          --warn: #ca8a04;
          --err: #dc2626;
        }
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; }
        body {
          font-family: 'IBM Plex Sans', system-ui, sans-serif;
          color: var(--ink);
          background: var(--bg);
          line-height: 1.5;
        }
        header {
          background: white;
          border-bottom: 1px solid var(--border);
          padding: 24px 48px;
        }
        header h1 { font-family: 'Manrope', sans-serif; font-weight: 700; margin: 0; font-size: 28px; }
        header h1 .accent { color: var(--indigo); }
        header .subtitle { color: var(--muted); margin-top: 4px; font-size: 14px; }
        main { max-width: 1100px; margin: 32px auto; padding: 0 24px; display: grid; gap: 24px; }
        .card {
          background: white;
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 24px;
        }
        .card h2 {
          font-family: 'Manrope', sans-serif;
          font-weight: 700;
          margin: 0 0 4px;
          font-size: 18px;
          color: var(--indigo);
        }
        .card .lede { color: var(--muted); margin: 0 0 16px; font-size: 14px; }
        .actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
        button {
          font: 500 14px 'IBM Plex Sans', system-ui, sans-serif;
          background: var(--indigo);
          color: white;
          border: none;
          padding: 8px 14px;
          border-radius: 6px;
          cursor: pointer;
          transition: background 0.15s;
        }
        button:hover:not(:disabled) { background: #3d4a82; }
        button:disabled { background: #d1d5db; cursor: not-allowed; }
        button.ghost { background: white; color: var(--indigo); border: 1px solid var(--indigo); }
        button.ghost:hover:not(:disabled) { background: var(--indigo-light); }
        .preview-frame { width: 100%; min-height: 320px; border: 1px solid var(--border); border-radius: 6px; background: white; }
        pre, code { font-family: 'JetBrains Mono', monospace; font-size: 13px; }
        pre.output {
          background: #f3f4f6;
          padding: 12px;
          border-radius: 6px;
          margin: 0;
          overflow-x: auto;
          color: var(--ink);
          min-height: 24px;
          white-space: pre-wrap;
          word-break: break-all;
        }
        .doc-state {
          margin: 12px 0;
          font-size: 14px;
          color: var(--muted);
        }
        .doc-state code {
          padding: 2px 8px;
          background: var(--indigo-light);
          color: var(--indigo);
          border-radius: 4px;
          font-weight: 500;
        }
        .thumbs { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
        .thumbs img { border: 1px solid var(--border); border-radius: 4px; max-height: 200px; }
        footer { text-align: center; color: var(--muted); padding: 32px; font-size: 13px; }
        footer a { color: var(--indigo); text-decoration: none; }
        .status { display: inline-block; font-size: 12px; padding: 1px 6px; border-radius: 4px; margin-left: 8px; }
        .status.ok { background: #dcfce7; color: var(--ok); }
        .status.err { background: #fee2e2; color: var(--err); }
      </style>
      </head>
      <body>
      <header>
        <h1>Poli Page <span class="accent">Spring Boot</span> demo</h1>
        <p class="subtitle">Ten SDK methods, one Spring Boot app. The starter wires <code>PoliPageClient</code> and the response helpers automatically.</p>
      </header>
      <main>

        <section class="card">
          <h2>1 · Render PDF (bytes)</h2>
          <p class="lede"><code>client.render().pdf(input)</code> → <code>responses.bytes(pdf, "welcome.pdf", true)</code></p>
          <div class="actions"><button data-render="pdf">Render</button><span id="pdf-status"></span></div>
          <iframe id="pdf-frame" class="preview-frame" srcdoc="<em style='color:#6b7280;font-family:sans-serif;padding:12px;display:block'>Click <strong>Render</strong> above</em>"></iframe>
        </section>

        <section class="card">
          <h2>2 · Render PDF (streamed)</h2>
          <p class="lede"><code>client.render().pdfStream(input)</code> → <code>responses.stream(...)</code> using <code>StreamingResponseBody</code></p>
          <div class="actions"><button data-render="stream">Render streamed</button><span id="stream-status"></span></div>
          <iframe id="stream-frame" class="preview-frame" srcdoc="<em style='color:#6b7280;font-family:sans-serif;padding:12px;display:block'>Click above to stream</em>"></iframe>
        </section>

        <section class="card">
          <h2>3 · Render to file (CLI runner)</h2>
          <p class="lede"><code>client.renderToFile(input, path)</code> — invoked at app boot when launched with <code>--render-to-file=PATH</code>. Not exposed via HTTP.</p>
          <pre class="output">./gradlew :example-app:mvc:bootRun --args='--render-to-file=./welcome.pdf'</pre>
        </section>

        <section class="card">
          <h2>4 · HTML preview</h2>
          <p class="lede"><code>client.render().preview(input)</code> → <code>responses.preview(preview)</code></p>
          <div class="actions"><button data-render="preview">Preview HTML</button><span id="preview-status"></span></div>
          <iframe id="preview-frame" class="preview-frame" srcdoc="<em style='color:#6b7280;font-family:sans-serif;padding:12px;display:block'>Click above</em>"></iframe>
        </section>

        <section class="card">
          <h2>5–9 · Document lifecycle</h2>
          <p class="lede">Store → fetch descriptor → thumbnails → preview → delete.</p>
          <div class="actions">
            <button data-doc="create">5 · Store</button>
            <button data-doc="get" disabled>6 · Descriptor</button>
            <button data-doc="thumbs" disabled>7 · Thumbnails</button>
            <button data-doc="preview" disabled>8 · Preview</button>
            <button data-doc="delete" disabled>9 · Delete</button>
            <button class="ghost" data-doc="open" disabled>Open PDF</button>
          </div>
          <p class="doc-state">Document id: <code id="doc-id">— (none)</code></p>
          <pre id="doc-output" class="output">// Output appears here</pre>
          <div id="thumbs" class="thumbs"></div>
        </section>

        <section class="card">
          <h2>10 · Error surface</h2>
          <p class="lede">Trigger a deliberate <code>INVALID_VERSION_FORMAT</code> and inspect the typed exception JSON.</p>
          <div class="actions"><button data-action="error">Trigger 400</button><span id="error-status"></span></div>
          <pre id="error-output" class="output">// Exception details appear here</pre>
        </section>

      </main>
      <footer>
        <p>Powered by <code>page.poli:poli-page-spring-boot-starter</code>. Source at <a href="https://github.com/poli-page/spring-boot">github.com/poli-page/spring-boot</a>.</p>
      </footer>
      <script>
        const $ = (id) => document.getElementById(id);
        const setStatus = (id, status, ok) => {
          const el = $(id);
          if (!el) return;
          el.textContent = status;
          el.className = 'status ' + (ok ? 'ok' : 'err');
        };

        async function fetchBlobInto(frameId, statusId, url) {
          setStatus(statusId, '…', true);
          const res = await fetch(url);
          if (!res.ok) { setStatus(statusId, 'HTTP ' + res.status, false); return; }
          const blob = await res.blob();
          $(frameId).src = URL.createObjectURL(blob);
          setStatus(statusId, 'HTTP ' + res.status + ' · ' + (blob.size / 1024).toFixed(1) + ' KB', true);
        }

        async function fetchHtmlInto(frameId, statusId, url) {
          setStatus(statusId, '…', true);
          const res = await fetch(url);
          const text = await res.text();
          if (!res.ok) { setStatus(statusId, 'HTTP ' + res.status, false); return; }
          $(frameId).srcdoc = text;
          setStatus(statusId, 'HTTP ' + res.status + ' · ' + (text.length / 1024).toFixed(1) + ' KB', true);
        }

        async function fetchJson(url, opts) {
          const res = await fetch(url, opts);
          const text = await res.text();
          let body;
          try { body = JSON.parse(text); } catch { body = text; }
          return { status: res.status, ok: res.ok, body };
        }

        document.querySelector('button[data-render="pdf"]').addEventListener('click', () => fetchBlobInto('pdf-frame', 'pdf-status', '/render/pdf'));
        document.querySelector('button[data-render="stream"]').addEventListener('click', () => fetchBlobInto('stream-frame', 'stream-status', '/render/stream'));
        document.querySelector('button[data-render="preview"]').addEventListener('click', () => fetchHtmlInto('preview-frame', 'preview-status', '/render/preview'));

        let currentDocId = null;
        const docButtons = (en) => document.querySelectorAll('[data-doc]').forEach(b => { if (b.dataset.doc !== 'create') b.disabled = !en; });

        document.querySelector('button[data-doc="create"]').addEventListener('click', async () => {
          const r = await fetchJson('/documents', { method: 'POST' });
          $('doc-output').textContent = JSON.stringify(r.body, null, 2);
          if (r.ok && r.body && r.body.documentId) {
            currentDocId = r.body.documentId;
            $('doc-id').textContent = currentDocId;
            docButtons(true);
            $('thumbs').innerHTML = '';
          }
        });

        document.querySelector('button[data-doc="get"]').addEventListener('click', async () => {
          const r = await fetchJson('/documents/' + currentDocId + '/raw');
          $('doc-output').textContent = JSON.stringify(r.body, null, 2);
        });

        document.querySelector('button[data-doc="thumbs"]').addEventListener('click', async () => {
          const r = await fetchJson('/documents/' + currentDocId + '/thumbnails');
          $('doc-output').textContent = JSON.stringify({ count: Array.isArray(r.body) ? r.body.length : 0 }, null, 2);
          const container = $('thumbs');
          container.innerHTML = '';
          if (Array.isArray(r.body)) {
            r.body.forEach(t => {
              const img = document.createElement('img');
              img.src = 'data:' + t.contentType + ';base64,' + t.data;
              img.alt = 'page ' + t.page;
              container.appendChild(img);
            });
          }
        });

        document.querySelector('button[data-doc="preview"]').addEventListener('click', async () => {
          const res = await fetch('/documents/' + currentDocId + '/preview');
          const html = await res.text();
          const w = window.open('', '_blank');
          w.document.write(html);
          w.document.close();
        });

        document.querySelector('button[data-doc="delete"]').addEventListener('click', async () => {
          const res = await fetch('/documents/' + currentDocId, { method: 'DELETE' });
          $('doc-output').textContent = 'DELETE → ' + res.status;
          if (res.ok) {
            currentDocId = null;
            $('doc-id').textContent = '— (deleted)';
            docButtons(false);
            $('thumbs').innerHTML = '';
          }
        });

        document.querySelector('button[data-doc="open"]').addEventListener('click', () => {
          window.open('/documents/' + currentDocId, '_blank');
        });

        document.querySelector('button[data-action="error"]').addEventListener('click', async () => {
          const r = await fetchJson('/errors/bad-version');
          setStatus('error-status', 'HTTP ' + r.status, r.status >= 400);
          $('error-output').textContent = JSON.stringify(r.body, null, 2);
        });
      </script>
      </body>
      </html>
      """;
}
