package com.intec.immichfire.setup

/**
 * HTML templates served by [ConfigServer]. Themed to match the on-TV Compose UI: cream
 * background (#F6F6F4), Immich indigo primary (#4250AF), white cards with purple borders.
 * The form defaults to the Sign-in tab so the typical path is email + password; the API-key
 * tab stays available for people who have already minted their own key.
 */
internal object SetupHtml {

    private const val CSS = """
        :root{
          --bg:#F6F6F4;
          --surface:#FFFFFF;
          --surface-alt:#FBFBFC;
          --primary:#4250AF;
          --primary-focus:#2E3A8C;
          --primary-subtle:#E4E5F5;
          --primary-border:#ACB1DC;
          --on-surface:#1F1F1F;
          --on-surface-secondary:#6B6B6B;
          --on-surface-muted:#9A9A9A;
          --divider:#E6E6EA;
          --ok:#1F8A3B;
          --error:#B00020;
        }
        *{box-sizing:border-box}
        html,body{margin:0}
        body{
          font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,system-ui,sans-serif;
          background:var(--bg);
          color:var(--on-surface);
          padding:32px 16px;
          min-height:100vh;
        }
        .wrap{max-width:460px;margin:0 auto}
        .brand{display:flex;align-items:center;gap:10px;margin-bottom:18px}
        .brand-mark{width:28px;height:28px;flex:0 0 auto}
        .brand-name{color:var(--primary);font-weight:600;font-size:20px;letter-spacing:.2px}
        .card{
          background:var(--surface);
          border:1px solid var(--primary-border);
          border-radius:14px;
          padding:22px;
          box-shadow:0 1px 2px rgba(66,80,175,.04);
        }
        h1{
          font-size:22px;
          font-weight:600;
          color:var(--primary);
          margin:0 0 6px;
        }
        .lead{color:var(--on-surface-secondary);font-size:14px;margin:0 0 18px;line-height:1.45}
        label{
          display:block;
          margin-top:16px;
          font-size:13px;
          color:var(--on-surface-secondary);
          font-weight:500;
        }
        input{
          width:100%;
          margin-top:6px;
          padding:11px 12px;
          font-size:15px;
          border:1px solid var(--primary-border);
          border-radius:10px;
          background:var(--surface-alt);
          color:var(--on-surface);
          outline:none;
          transition:border-color .15s,box-shadow .15s;
        }
        input:focus{
          border-color:var(--primary);
          box-shadow:0 0 0 3px rgba(66,80,175,.12);
          background:var(--surface);
        }
        input[readonly]{background:var(--primary-subtle);cursor:not-allowed}
        button{
          font-family:inherit;
          cursor:pointer;
          border:0;
          font-weight:600;
          transition:background .15s,color .15s,border-color .15s;
        }
        .btn-primary{
          margin-top:18px;
          width:100%;
          padding:13px;
          font-size:15px;
          border-radius:10px;
          background:var(--primary);
          color:#fff;
        }
        .btn-primary:hover,.btn-primary:focus{background:var(--primary-focus)}
        .btn-secondary{
          padding:11px 14px;
          font-size:14px;
          border-radius:10px;
          background:var(--primary-subtle);
          color:var(--primary);
          border:1px solid var(--primary-border);
        }
        .btn-secondary:hover,.btn-secondary:focus{
          background:#fff;
          border-color:var(--primary);
        }
        .tabs{
          display:flex;
          gap:6px;
          padding:4px;
          background:var(--primary-subtle);
          border-radius:12px;
          border:1px solid var(--primary-border);
          margin-top:4px;
        }
        .tab{
          flex:1;
          padding:10px 8px;
          border:0;
          background:transparent;
          color:var(--primary);
          border-radius:9px;
          font-size:14px;
          font-weight:600;
        }
        .tab.active{background:var(--primary);color:#fff}
        .row{display:flex;gap:10px;align-items:stretch;margin-top:6px}
        .row input{flex:1}
        .row .btn-secondary{margin:0;flex:0 0 auto}
        .actions{display:flex;gap:10px;margin-top:20px}
        .actions .btn-primary,.actions .btn-secondary{margin:0;flex:1}
        .hint{font-size:12px;color:var(--on-surface-muted);margin-top:8px;line-height:1.4}
        #status{margin-top:12px;font-size:14px;min-height:20px}
        #status.ok{color:var(--ok)}
        #status.err{color:var(--error)}
        .found{margin-top:10px;display:flex;flex-direction:column;gap:6px}
        .found button{
          margin:0;
          width:100%;
          padding:10px 12px;
          font-size:13px;
          background:var(--primary-subtle);
          color:var(--primary);
          border:1px solid var(--primary-border);
          border-radius:10px;
          text-align:left;
        }
        .found button:hover{background:#fff;border-color:var(--primary)}
        .hidden{display:none}
    """

    // Immich 5-petal flower, inline SVG (no network fetch; renders even before any asset
    // served from the APK could load). Mirrors the ic_immich_mark drawable.
    private const val BRAND_MARK = """
        <svg class="brand-mark" viewBox="0 0 792 792" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <path fill="#FA2921" d="M375.48,267.63c38.64,34.21,69.78,70.87,89.82,105.42c34.42-61.56,57.42-134.71,57.71-181.3c0-0.33,0-0.63,0-0.91c0-68.94-68.77-95.77-128.01-95.77s-128.01,26.83-128.01,95.77c0,0.94,0,2.2,0,3.72C300.01,209.24,339.15,235.47,375.48,267.63z"/>
          <path fill="#ED79B5" d="M164.7,455.63c24.15-26.87,61.2-55.99,103.01-80.61c44.48-26.18,88.97-44.47,128.02-52.84c-47.91-51.76-110.37-96.24-154.6-110.91c-0.31-0.1-0.6-0.19-0.86-0.28c-65.57-21.3-112.34,35.81-130.64,92.15c-18.3,56.34-14.04,130.04,51.53,151.34C162.05,454.77,163.25,455.16,164.7,455.63z"/>
          <path fill="#FFB400" d="M681.07,302.19c-18.3-56.34-65.07-113.45-130.64-92.15c-0.9,0.29-2.1,0.68-3.54,1.15c-3.75,35.93-16.6,81.27-35.96,125.76c-20.59,47.32-45.84,88.27-72.51,118c69.18,13.72,145.86,12.98,190.26-1.14c0.31-0.1,0.6-0.2,0.86-0.28C695.11,432.22,699.37,358.52,681.07,302.19z"/>
          <path fill="#1E83F7" d="M336.54,510.71c-11.15-50.39-14.8-98.36-10.7-138.08c-64.03,29.57-125.63,75.23-153.26,112.76c-0.19,0.26-0.37,0.51-0.53,0.73c-40.52,55.78-0.66,117.91,47.27,152.72c47.92,34.82,119.33,53.54,159.86-2.24c0.56-0.76,1.3-1.78,2.19-3.01C363.28,602.32,347.02,558.08,336.54,510.71z"/>
          <path fill="#18C249" d="M617.57,482.52c-35.33,7.54-82.42,9.33-130.72,4.66c-51.37-4.96-98.11-16.32-134.63-32.5c8.33,70.03,32.73,142.73,59.88,180.6c0.19,0.26,0.37,0.51,0.53,0.73c40.52,55.78,111.93,37.06,159.86,2.24c47.92-34.82,87.79-96.95,47.27-152.72C619.2,484.77,618.46,483.75,617.57,482.52z"/>
        </svg>
    """

    fun indexPage(): String = """
        <!doctype html><html lang="en"><head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>ImmichFire Setup</title>
          <style>$CSS</style>
        </head><body>
          <div class="wrap">
            <div class="brand">$BRAND_MARK<div class="brand-name">ImmichFire</div></div>
            <div class="card">
              <h1>Connect your Immich server</h1>
              <p class="lead">Enter your server details below, then sign in with your Immich account — an API key is created for you automatically. You can also paste an existing key.</p>

              <form method="POST" action="/submit" id="f">
                <input type="hidden" name="mode" id="mode" value="login">

                <label>Pairing code (6 digits)</label>
                <input name="code" inputmode="numeric" pattern="\d{6}" required autocomplete="off">

                <label>Immich Server URL</label>
                <div class="row">
                  <input name="url" id="url" type="text" inputmode="url" autocapitalize="off" autocomplete="off" spellcheck="false" placeholder="https://immich.example.com   or   192.168.0.8:2283" required>
                  <button type="button" class="btn-secondary" onclick="scan()">Scan LAN</button>
                </div>
                <div id="found" class="found"></div>
                <div class="hint">Accepts <code>https://immich.example.com</code>, <code>http://192.168.0.8:2283</code>, or <code>http://travelpi.lan:2283</code>. <code>http://</code> is assumed when no scheme is given. Scan uses mDNS (<code>_immich._tcp.local.</code>) and a /24 subnet probe.</div>

                <label style="margin-top:22px">Authentication</label>
                <div class="tabs" role="tablist">
                  <button type="button" class="tab active" id="tab-login" onclick="setMode('login')" role="tab" aria-selected="true">Sign in</button>
                  <button type="button" class="tab" id="tab-apikey" onclick="setMode('apikey')" role="tab" aria-selected="false">API key</button>
                </div>

                <div id="pane-login">
                  <label>Email</label>
                  <input name="email" id="email" type="email" autocomplete="username">
                  <label>Password</label>
                  <input name="password" id="password" type="password" autocomplete="current-password">
                  <div class="hint">Your password is used once to create an API key named for this device. It's never stored on the TV.</div>
                </div>

                <div id="pane-apikey" class="hidden">
                  <label>API key</label>
                  <input name="apiKey" id="apiKey">
                  <div class="hint">Create one in Immich → Account Settings → API Keys.</div>
                </div>

                <div id="status"></div>

                <div class="actions">
                  <button type="button" class="btn-secondary" id="testBtn" onclick="test()">Test connection</button>
                  <button type="submit" class="btn-primary" id="saveBtn">Sign in &amp; Save</button>
                </div>
              </form>
            </div>
          </div>
          <script>
            const modeEl=document.getElementById('mode');
            const tabLogin=document.getElementById('tab-login');
            const tabApi=document.getElementById('tab-apikey');
            const paneLogin=document.getElementById('pane-login');
            const paneApi=document.getElementById('pane-apikey');
            const saveBtn=document.getElementById('saveBtn');
            const testBtn=document.getElementById('testBtn');
            const urlEl=document.getElementById('url');
            // Make "immich.tewky.pw" and "192.168.0.8:2283" valid inputs by auto-prepending
            // http://. Also strips trailing slash so the server can reason about a clean
            // base URL. Already-schemed URLs pass through untouched.
            function normalizeUrl(){
              let v=(urlEl.value||'').trim();
              if(!v) return;
              if(!/^https?:\/\//i.test(v)) v='http://'+v;
              v=v.replace(/\/+$/,'');
              urlEl.value=v;
            }
            urlEl.addEventListener('blur',normalizeUrl);
            document.getElementById('f').addEventListener('submit',normalizeUrl);
            function setMode(m){
              modeEl.value=m;
              const login=m==='login';
              tabLogin.classList.toggle('active',login);
              tabApi.classList.toggle('active',!login);
              tabLogin.setAttribute('aria-selected',login);
              tabApi.setAttribute('aria-selected',!login);
              paneLogin.classList.toggle('hidden',!login);
              paneApi.classList.toggle('hidden',login);
              document.getElementById('email').required=login;
              document.getElementById('password').required=login;
              document.getElementById('apiKey').required=!login;
              saveBtn.textContent=login?'Sign in & Save':'Save';
              // Test with no password would mint a throwaway key; only surface it for API-key mode.
              testBtn.classList.toggle('hidden',login);
            }
            async function test(){
              normalizeUrl();
              const f=document.getElementById('f');
              const fd=new FormData(f);
              const s=document.getElementById('status');
              s.textContent='Testing…';s.className='';
              const r=await fetch('/test',{method:'POST',body:new URLSearchParams(fd)});
              const t=await r.text();
              const ok=t==='OK';
              s.textContent=ok?'Connected OK ✓':('✗ '+t);
              s.className=ok?'ok':'err';
            }
            async function scan(){
              const s=document.getElementById('status');
              const found=document.getElementById('found');
              found.innerHTML='';
              s.textContent='Scanning local network…';s.className='';
              try{
                const r=await fetch('/discover');
                const j=await r.json();
                const items=j.results||[];
                if(items.length===0){ s.textContent='No Immich servers found on LAN.'; s.className='err'; return; }
                s.textContent='Found '+items.length+' server(s) — tap one to use it:';
                s.className='ok';
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
              } catch(e) { s.textContent='Scan failed: '+e; s.className='err'; }
            }
            setMode('login');
          </script>
        </body></html>
    """.trimIndent()

    fun resultPage(success: Boolean, message: String): String = """
        <!doctype html><html lang="en"><head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>ImmichFire Setup</title>
          <style>
            $CSS
            .result-card{text-align:center;padding:36px 22px}
            .result-card h1{margin-top:12px}
            .result-card.ok h1{color:var(--ok)}
            .result-card.err h1{color:var(--error)}
            .result-card p{color:var(--on-surface-secondary);font-size:15px;line-height:1.5}
            .result-card a{color:var(--primary);font-weight:600;text-decoration:none}
            .result-card a:hover{text-decoration:underline}
          </style>
        </head><body>
          <div class="wrap">
            <div class="brand">$BRAND_MARK<div class="brand-name">ImmichFire</div></div>
            <div class="card result-card ${if (success) "ok" else "err"}">
              <h1>${if (success) "Saved ✓" else "Error"}</h1>
              <p>$message</p>
              ${if (!success) "<p><a href='/'>Try again</a></p>" else "<p>You can close this page and return to the TV.</p>"}
            </div>
          </div>
        </body></html>
    """.trimIndent()
}
