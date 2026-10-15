const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('hdrgDesktop', {
  saveVideo:(data,name)=>ipcRenderer.invoke('save-video',data,name),
  findLyrics:query=>ipcRenderer.invoke('find-lyrics',query)
});
