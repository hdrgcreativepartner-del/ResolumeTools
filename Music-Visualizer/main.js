const {app, BrowserWindow, ipcMain, dialog, session} = require('electron');
const path = require('path');
const fs = require('fs/promises');
let win;
function createWindow(){
  win = new BrowserWindow({width:1480,height:960,minWidth:1000,minHeight:690,backgroundColor:'#101113',autoHideMenuBar:true,webPreferences:{preload:path.join(__dirname,'src','preload.js'),contextIsolation:true,nodeIntegration:false,sandbox:true,backgroundThrottling:false}});
  win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
  win.webContents.on('will-navigate',event=>event.preventDefault());
  if (process.argv.includes('--smoke-test')) {
    const timeout = setTimeout(() => app.exit(2), 30000);
    win.webContents.on('did-fail-load', () => app.exit(3));
    win.webContents.once('did-finish-load', async () => {
      try {
        const ok = await win.webContents.executeJavaScript(`typeof PlayerCore === 'object' && typeof togglePlayback === 'function' && STYLE_VARIANTS.radial.length === 6 && document.querySelectorAll('.player-panels details').length === 2 && !!window.hdrgDesktop.findLyrics`);
        clearTimeout(timeout); app.exit(ok ? 0 : 4);
      } catch { app.exit(5); }
    });
  }
  win.loadFile(path.join(__dirname,'src','index.html'));
}
app.whenReady().then(()=>{
  session.defaultSession.setPermissionRequestHandler((_wc,_permission,callback)=>callback(false));
  createWindow();app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)createWindow()});
});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});
function trusted(event){ if (!win || event.sender !== win.webContents || event.senderFrame !== win.webContents.mainFrame) throw new Error('Untrusted request'); }
ipcMain.handle('save-video', async(event, bytes, name)=>{
  trusted(event);
  if (!(bytes instanceof Uint8Array)) throw new Error('Invalid video');
  const {canceled,filePath}=await dialog.showSaveDialog(win,{title:'Export visualizer recording',defaultPath:path.basename(String(name)),filters:[{name:'WebM Video',extensions:['webm']}]});
  if(canceled||!filePath)return null;
  await fs.writeFile(filePath, Buffer.from(bytes));return filePath;
});
const lyricCache = new Map();
ipcMain.handle('find-lyrics', async(event, query)=>{
  trusted(event);
  const title=String(query?.title||'').trim().slice(0,200),artist=String(query?.artist||'').trim().slice(0,200);
  if(!title||!artist)return {error:'Title and artist required'};
  const key=title.toLowerCase()+'\0'+artist.toLowerCase();
  if(lyricCache.has(key))return lyricCache.get(key);
  const url=new URL('https://lrclib.net/api/get');url.searchParams.set('track_name',title);url.searchParams.set('artist_name',artist);
  if(Number.isFinite(query.duration)&&query.duration>0)url.searchParams.set('duration',String(Math.round(query.duration)));
  try{
    const response=await fetch(url,{headers:{'User-Agent':'HDRG-Music-Visualizer/0.5.0 (https://github.com/hdrgcreativepartner-del/ResolumeTools)'},signal:AbortSignal.timeout(12000)});
    if(response.status===404)return {};
    if(!response.ok)return {error:'Lyrics service returned '+response.status};
    const data=await response.json();
    const result={syncedLyrics:typeof data.syncedLyrics==='string'?data.syncedLyrics.slice(0,500000):'',plainLyrics:typeof data.plainLyrics==='string'?data.plainLyrics.slice(0,500000):'',instrumental:!!data.instrumental};
    if(lyricCache.size>200)lyricCache.clear();lyricCache.set(key,result);return result;
  }catch{return {error:'Network unavailable or lookup timed out'};}
});
