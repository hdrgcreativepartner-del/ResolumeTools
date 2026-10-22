export const sdk = `// Server must deduplicate operation IDs and acknowledge only committed writes.
export function createHybridStore({ endpoint = '', token = () => '', namespace = 'hdrg-hybrid' } = {}) {
 const uuid = () => crypto.randomUUID ? crypto.randomUUID() : Array.from(crypto.getRandomValues(new Uint8Array(16)),x=>x.toString(16).padStart(2,'0')).join('');
 let running = false;
 const read = () => JSON.parse(localStorage.getItem(namespace) || '{"records":{},"queue":[]}');
 const write = state => localStorage.setItem(namespace, JSON.stringify(state));
 return {
  list: () => Object.values(read().records).filter(x => !x.deleted),
  pending: () => read().queue.length,
  put(record) {
   const state = read(); const id = record.id || uuid();
   const data = {...record, id, updatedAt: new Date().toISOString()};
   state.records[id] = data; state.queue.push({id:uuid(), type:'put', record:data});
   write(state); return data;
  },
  async sync() {
   if (!endpoint) throw new Error('Endpoint sinkronisasi belum dikonfigurasi.');
   if (running) return {busy:true};
   running = true;
   try {
    const batch = read().queue.slice(0,100); if (!batch.length) return {sent:0};
    const auth = await token();
    const response = await fetch(endpoint, {method:'POST', headers:{'Content-Type':'application/json', ...(auth ? {Authorization:'Bearer '+auth}: {})}, body:JSON.stringify({operations:batch}), signal:AbortSignal.timeout(15000)});
    if (!response.ok) throw new Error('Sync HTTP '+response.status);
    const result = await response.json();
    if (!Array.isArray(result.acknowledgedIds)) throw new Error('Respons server tidak valid.');
    const sent = new Set(batch.map(x=>x.id));
    const ack = new Set(result.acknowledgedIds.filter(id=>sent.has(id)));
    const latest = read(); latest.queue = latest.queue.filter(x=>!ack.has(x.id)); write(latest);
    return {sent:ack.size,pending:latest.queue.length,conflicts:result.conflicts||[]};
   } finally { running = false; }
  }
 };
}
`;
export function makeProject(c, assets={}) {
 const json=x=>JSON.stringify(x,null,2), f={};
 const slug=c.name.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'')||'hybrid-app';
 f['package.json']=json({name:slug,version:c.version,private:true,scripts:{tauri:'tauri','build:windows':'tauri build --bundles nsis,msi','init:android':'tauri android init','build:android':'tauri android build --debug --apk --target aarch64'},devDependencies:{'@tauri-apps/cli':'^2.0.0'}});
 f['.gitignore']='node_modules/\nsrc-tauri/target/\nsrc-tauri/gen/\n*.keystore\n.env\n';
 f['src-tauri/Cargo.toml']='[package]\nname = "app"\nversion = '+JSON.stringify(c.version)+'\nedition = "2021"\n\n[lib]\nname = "app_lib"\ncrate-type = ["staticlib", "cdylib", "rlib"]\n\n[build-dependencies]\ntauri-build = { version = "2", features = [] }\n\n[dependencies]\ntauri = { version = "2", features = [] }\n';
 f['src-tauri/build.rs']='fn main() { tauri_build::build() }\n';
 f['src-tauri/src/main.rs']='#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]\nfn main() { app_lib::run(); }\n';
 f['src-tauri/src/lib.rs']='#[cfg_attr(mobile, tauri::mobile_entry_point)]\npub fn run() { tauri::Builder::default().run(tauri::generate_context!()).expect("error running app"); }\n';
 f['src-tauri/tauri.conf.json']=json({$schema:'https://schema.tauri.app/config/2',productName:c.name,version:c.version,identifier:c.identifier,build:{frontendDist:'../web'},app:{windows:[{label:'main',title:c.name,resizable:true,maximized:true}],security:{csp:null}},bundle:{active:true,targets:c.windows?['nsis','msi']:[],icon:['icons/32x32.png','icons/128x128.png','icons/128x128@2x.png','icons/icon.icns','icons/icon.ico']}});
 f['src-tauri/capabilities/default.json']=json({identifier:'default',description:'Local application only; no remote native access',windows:['main'],permissions:['core:default']});
 f['web/forge/hybrid.js']=sdk;
 f['web/forge/config.json']=json({endpoint:c.endpoint,mode:'hybrid',name:c.name});
 if(Object.keys(assets).length) for(const [name,data] of Object.entries(assets)) f['web/'+name]=data;
 else {
 f['web/index.html']='<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>Hybrid App</title><style>*{box-sizing:border-box}html{min-height:100%;width:100%}body{font:clamp(14px,2vw,16px) system-ui;background:#11151a;color:white;width:100%;max-width:850px;min-height:100dvh;margin:0 auto;padding:max(24px,env(safe-area-inset-top)) max(20px,env(safe-area-inset-right)) max(24px,env(safe-area-inset-bottom)) max(20px,env(safe-area-inset-left));overflow-wrap:anywhere}h1{font-size:40px}input,button{font:inherit;padding:14px;border-radius:8px;border:0;margin:8px 0}input{width:90%}button{background:'+c.color+';cursor:pointer}li{padding:12px}small,p{color:#a7b2c1}</style></head><body><small>HDRG · HYBRID DEMO</small><h1 id="title"></h1><p>Catatan disimpan lokal dan tetap tersedia setelah aplikasi ditutup.</p><form><input id="text" required placeholder="Tulis catatan…"><button>Simpan lokal</button></form><button id="sync">Sinkronkan</button><p id="status" role="status"></p><ul id="notes"></ul><script type="module" src="demo.js"></script></body></html>';
 f['web/demo.js']=`import {createHybridStore} from './forge/hybrid.js';
const config=await (await fetch('./forge/config.json')).json();
const store=createHybridStore({endpoint:config.endpoint});
document.querySelector('#title').textContent=config.name;
function render(){const list=document.querySelector('#notes');list.replaceChildren();for(const note of store.list()){const li=document.createElement('li');li.textContent=note.text;list.append(li);}document.querySelector('#status').textContent=store.pending()+' perubahan menunggu sinkronisasi.';}
document.querySelector('form').onsubmit=e=>{e.preventDefault();const input=document.querySelector('#text');store.put({text:input.value});input.value='';render();};
document.querySelector('#sync').onclick=async()=>{try{await store.sync();render();}catch(error){document.querySelector('#status').textContent=error.message;}};render();`;
 }
 f['README.md']=`# ${c.name} — Hybrid project\n\nGenerated by HDRG AppForge 0.2. Source template, not a compiled installer.\n\n## Run/build\nInstall Node.js LTS, Rust stable, and Tauri platform prerequisites: https://v2.tauri.app/start/prerequisites/\n\n1. npm install\n2. npx tauri icon app-icon.png\n3. Desktop development: npm run tauri dev\n4. On Windows: npm run build:windows\n5. Android: install Android Studio, SDK and NDK, set JAVA_HOME, ANDROID_HOME and NDK_HOME, then npm run init:android and npm run build:android.\n\nGitHub Actions workflow is manual: push this entire project to your repository and open Actions → Build hybrid app → Run workflow. Android output is a debug APK (ARM64) for testing, not a signed commercial release. Windows output is unsigned EXE/MSI. Pin dependency versions and keep lockfiles after your first validated build.\n\n## Adaptive display\nWindows opens maximized and remains resizable. No fixed width or height is configured. Android uses its device viewport without an orientation lock. The demo uses responsive CSS and safe-area padding. Imported websites must provide responsive CSS themselves; fixed layouts cannot be made responsive by the native shell alone.\n\n## Hybrid behavior\nThe web folder is bundled locally, not fetched from a website. Supply compiled static HTML/CSS/JS, not raw React or server source. Use relative asset paths. External fonts/CDNs/API calls still need internet. Changes to the bundled interface require rebuilding this version.\n\nThe included demo persists records and a queue in localStorage. It is a single-window starter: no multi-tab locking, encryption, user partitioning, pull synchronization or automatic conflict merging. Production financial data should use transactional storage, explicit backup, authentication and a server-defined conflict policy.\n\nFor imported applications, integrate web/forge/hybrid.js explicitly; the generator does not rewrite your data layer. Existing window.median calls are not provided. Native camera, barcode, printer and filesystem integrations require corresponding plugins and permissions.\n\n## Sync server contract\nPOST the configured HTTPS endpoint with {operations:[{id,type:'put',record}]} and optional bearer token (supply a token callback to createHybridStore). Return {acknowledgedIds:[operationId],conflicts:[]}. Acknowledge only durable commits. Deduplicate operation IDs on the server. Failed/unacknowledged operations stay in the local queue. No backend is provisioned. The endpoint must allow the Tauri local origin via CORS. Do not embed permanent secrets.\n\nReview CSP and asset dependencies before release; the compatibility template currently leaves CSP unset. No native permissions are granted to remote origins.\n`;
 const steps='      - uses: actions/checkout@v4\n      - uses: actions/setup-node@v4\n        with:\n          node-version: 22\n      - uses: dtolnay/rust-toolchain@stable\n      - run: npm install\n      - run: npx tauri icon app-icon.png\n';
 let workflow='name: Build hybrid app\non: workflow_dispatch\npermissions:\n  contents: read\njobs:\n';
 if(c.windows)workflow+='  windows:\n    runs-on: windows-latest\n    steps:\n'+steps+'      - run: npm run build:windows\n      - uses: actions/upload-artifact@v4\n        with:\n          name: windows-installers\n          path: |\n            src-tauri/target/release/bundle/nsis/*.exe\n            src-tauri/target/release/bundle/msi/*.msi\n          if-no-files-found: error\n';
 if(c.android)workflow+='  android:\n    runs-on: ubuntu-latest\n    steps:\n'+steps+'      - uses: actions/setup-java@v4\n        with:\n          distribution: temurin\n          java-version: 17\n      - uses: android-actions/setup-android@v3\n      - run: sdkmanager "ndk;27.2.12479018" "platforms;android-35" "build-tools;35.0.0"\n      - run: echo "NDK_HOME=$ANDROID_HOME/ndk/27.2.12479018" >> "$GITHUB_ENV"\n      - run: rustup target add aarch64-linux-android\n      - run: npm run init:android -- --ci\n      - run: npm run build:android\n      - uses: actions/upload-artifact@v4\n        with:\n          name: android-debug-apk\n          path: src-tauri/gen/android/app/build/outputs/apk/**/*.apk\n          if-no-files-found: error\n';
 f['.github/workflows/build.yml']=workflow;
 return f;
}
// Uncompressed ZIP, UTF-8 filenames, CRC32; no external runtime dependency.
export function zip(files){const enc=new TextEncoder(),chunks=[],central=[];let offset=0;const crc=b=>{let c=0xffffffff;for(const x of b){c^=x;for(let k=0;k<8;k++)c=(c>>>1)^((c&1)?0xedb88320:0);}return (c^0xffffffff)>>>0;};
for(const [name,value] of Object.entries(files)){const n=enc.encode(name),b=typeof value==='string'?enc.encode(value):value,c=crc(b);const h=new Uint8Array(30+n.length),v=new DataView(h.buffer);v.setUint32(0,0x04034b50,true);v.setUint16(4,20,true);v.setUint16(6,0x800,true);v.setUint32(14,c,true);v.setUint32(18,b.length,true);v.setUint32(22,b.length,true);v.setUint16(26,n.length,true);h.set(n,30);chunks.push(h,b);const d=new Uint8Array(46+n.length),w=new DataView(d.buffer);w.setUint32(0,0x02014b50,true);w.setUint16(4,20,true);w.setUint16(6,20,true);w.setUint16(8,0x800,true);w.setUint32(16,c,true);w.setUint32(20,b.length,true);w.setUint32(24,b.length,true);w.setUint16(28,n.length,true);w.setUint32(42,offset,true);d.set(n,46);central.push(d);offset+=h.length+b.length;}
const size=central.reduce((a,b)=>a+b.length,0),end=new Uint8Array(22),v=new DataView(end.buffer);v.setUint32(0,0x06054b50,true);v.setUint16(8,central.length,true);v.setUint16(10,central.length,true);v.setUint32(12,size,true);v.setUint32(16,offset,true);return new Blob([...chunks,...central,end],{type:'application/zip'});}
