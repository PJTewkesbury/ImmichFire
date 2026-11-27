package com.intec.immichfire.setup

/**
 * HTML templates served by [ConfigServer]. Pulled out so the router doesn't share a file
 * with 60+ lines of CSS and JS.
 */
internal object SetupHtml {

    fun indexPage(): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>ImmichFire Setup</title>
        <style>
          body{font-family:system-ui;max-width:460px;margin:24px auto;padding:0 16px;background:#111;color:#eee}
          h1{font-size:20px}
          label{display:block;margin-top:16px;font-size:14px;color:#aaa}
          input{width:100%;padding:12px;font-size:16px;border:1px solid #444;border-radius:8px;background:#222;color:#fff;box-sizing:border-box}
          button{margin-top:20px;width:100%;padding:14px;font-size:16px;border:0;border-radius:8px;background:#ff6600;color:#fff;font-weight:600}
          .secondary{background:#333}
          .row{display:flex;gap:8px}
          .row button{flex:1}
          .inlineRow{display:flex;gap:8px;align-items:stretch;margin-top:6px}
          .inlineRow input{flex:1}
          .inlineRow button{margin:0;width:auto;padding:0 14px;font-size:14px}
          #status{margin-top:12px;font-size:14px;min-height:20px}
          .found{margin-top:8px;display:flex;flex-direction:column;gap:6px}
          .found button{margin:0;width:100%;padding:10px;font-size:14px;background:#263;color:#cfc;text-align:left}
        </style></head><body>
          <h1>ImmichFire Setup</h1>
          <p>Enter your Immich details below. The pairing code is shown on the TV.</p>
          <form method="POST" action="/submit" id="f">
            <label>Pairing code (6 digits)</label>
            <input name="code" inputmode="numeric" pattern="\d{6}" required>
            <label>Immich Server URL</label>
            <div class="inlineRow">
              <input name="url" id="url" type="url" placeholder="http://192.168.0.100:2283" required>
              <button type="button" class="secondary" onclick="scan()">Scan LAN</button>
            </div>
            <div id="found" class="found"></div>
            <div style="font-size:12px;color:#888;margin-top:6px">Scan uses mDNS (<code>_immich._tcp.local.</code>) and a /24 subnet probe in parallel.</div>
            <label>API Key</label>
            <input name="apiKey" required>
            <div id="status"></div>
            <div class="row">
              <button type="button" class="secondary" onclick="test()">Test connection</button>
              <button type="submit">Save</button>
            </div>
          </form>
          <script>
            async function test(){
              const f=document.getElementById('f');
              const fd=new FormData(f);
              const s=document.getElementById('status');
              s.textContent='Testing…';
              const r=await fetch('/test',{method:'POST',body:new URLSearchParams(fd)});
              const t=await r.text();
              s.textContent=t==='OK'?'Connected OK ✓':('✗ '+t);
              s.style.color=t==='OK'?'#6c6':'#f66';
            }
            async function scan(){
              const s=document.getElementById('status');
              const found=document.getElementById('found');
              found.innerHTML='';
              s.textContent='Scanning local network…';
              s.style.color='#ccc';
              try{
                const r=await fetch('/discover');
                const j=await r.json();
                const items=j.results||[];
                if(items.length===0){ s.textContent='No Immich servers found on LAN.'; s.style.color='#f66'; return; }
                s.textContent='Found '+items.length+' server(s) — tap one to use it:';
                s.style.color='#6c6';
                items.sort((a,b)=>{
                  if(a.source===b.source) return a.url.localeCompare(b.url);
                  return a.source==='mdns'?-1:1;
                });
                for(const it of items){
                  const b=document.createElement('button');
                  b.type='button';
                  const badge=it.source==='mdns'?'[mDNS] ':'[scan] ';
                  b.textContent=badge+it.url+'  —  '+it.name;
                  b.onclick=()=>{ document.getElementById('url').value=it.url; s.textContent='Using '+it.url; };
                  found.appendChild(b);
                }
              } catch(e) { s.textContent='Scan failed: '+e; s.style.color='#f66'; }
            }
          </script>
        </body></html>
    """.trimIndent()

    fun resultPage(success: Boolean, message: String): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>ImmichFire Setup</title>
        <style>body{font-family:system-ui;max-width:460px;margin:60px auto;padding:0 16px;background:#111;color:#eee;text-align:center}
        .ok{color:#6c6}.bad{color:#f66}a{color:#ff6600}</style></head><body>
        <h1 class="${if (success) "ok" else "bad"}">${if (success) "Saved ✓" else "Error"}</h1>
        <p>$message</p>
        ${if (!success) "<p><a href='/'>Try again</a></p>" else "<p>You can close this page and return to the TV.</p>"}
        </body></html>
    """.trimIndent()
}
