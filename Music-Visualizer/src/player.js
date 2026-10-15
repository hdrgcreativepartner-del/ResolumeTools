'use strict';
let fade = null, fadeStarting = false, fadeGeneration = 0, lookupGeneration = 0;
let lastLyric = '', energyClock = 0, libraryBusy = false;
function makeTrack(file) {
  return {file, url: URL.createObjectURL(file), template: activeTemplate, ...PlayerCore.metadata(file.name), lrc: '', lyrics: [], offset: 0};
}
function cancelFade() {
  fadeGeneration++; fadeStarting = false;
  for (const deck of decks) {
    if (deck !== audio) { deck.pause(); deck.removeAttribute('src'); deck.load(); }
    const gain = deckGains.get(deck); if (gain) { gain.gain.cancelScheduledValues(ac.currentTime); gain.gain.value = 1; }
  }
  fade = null;
  if ($('mixStatus')) $('mixStatus').textContent = $('autoDj').checked ? 'AUTO DJ READY' : 'AUTO DJ OFF';
}
async function togglePlayback() {
  if (!tracks.length) return notify('Add audio tracks first');
  if (selectedTrack < 0) selectTrack(0);
  setupAudio();
  try {
    if (audio.paused) {
      await ac.resume();
      await audio.play();
      if (fade) { await fade.outgoing.play(); fade.started = ac.currentTime - fade.elapsed; }
    } else { audio.pause(); if (fade) fade.outgoing.pause(); }
  } catch (e) { cancelFade(); notify('Playback failed: ' + e.message); }
}
function moveTrack(from, to) {
  if (to < 0 || to >= tracks.length) return;
  cancelFade(); const current = tracks[selectedTrack]; const [item] = tracks.splice(from, 1); tracks.splice(to, 0, item);
  selectedTrack = tracks.indexOf(current); refreshPlaylist();
}
function onTrackChanged() {
  lookupGeneration++;
  const track = tracks[selectedTrack];
  $('nowTitle').textContent = track?.title || 'Make room for your music.';
  $('nowArtist').textContent = track?.artist || (track ? 'Local audio · HDRG Music Visualizer' : 'Add tracks to begin. Your visual follows the sound.');
  $('trackTitle').value = track?.title || ''; $('trackArtist').value = track?.artist || '';
  $('lyricOffset').value = track?.offset || 0;
  $('lyricPreview').textContent = ''; lastLyric = '';
  $('currentTime').textContent = '00:00'; $('duration').textContent = '00:00'; $('seek').value = 0;
  $('lyricStatus').textContent = track?.lyrics?.length ? 'Synced lyrics ready' : 'Import LRC or enter title and artist to search.';
  if (!track) return;
  const auto = $('autoVisual').value;
  if (auto === 'cycle' && templates.length) loadLook(templates[selectedTrack % templates.length]);
  if ($('autoLyrics').checked && !track.lrc) lookupLyrics(true);
  if ('mediaSession' in navigator) navigator.mediaSession.metadata = new MediaMetadata({title:track.title, artist:track.artist || 'HDRG'});
}
async function beginFade(seconds) {
  const index = PlayerCore.nextIndex(selectedTrack, tracks.length, playMode);
  if (index < 0 || fadeStarting || fade) return;
  const generation = ++fadeGeneration; fadeStarting = true;
  const outgoing = audio, incoming = decks.find(d => d !== outgoing), track = tracks[index];
  incoming.src = track.url; incoming.load(); deckGains.get(incoming).gain.value = 0;
  try {
    await incoming.play();
    if (generation !== fadeGeneration) return;
    audio = incoming;
    selectedTrack = index;
    const template = templates.find(t => t.id === track.template); if (template) loadLook(template);
    fade = {outgoing, incoming, started: ac.currentTime, elapsed:0, seconds};
    onTrackChanged(); refreshPlaylist(); $('playBtn').textContent = 'Ⅱ'; $('duration').textContent = fmt(audio.duration);
    $('audioName').textContent = track.file.name;
    $('mixStatus').textContent = 'CROSSFADING';
  } catch (e) {
    if (generation === fadeGeneration) { cancelFade(); $('autoDj').checked = false; $('mixStatus').textContent = 'AUTO DJ OFF'; notify('Crossfade unavailable for next track. Auto DJ stopped.'); }
  } finally { if (generation === fadeGeneration) fadeStarting = false; }
}
setInterval(() => {
  if (!ac || audio.paused) return;
  if (fade) {
    fade.elapsed = ac.currentTime - fade.started;
    const gains = PlayerCore.fadeGains(fade.elapsed / fade.seconds);
    deckGains.get(fade.outgoing).gain.value = gains[0]; deckGains.get(fade.incoming).gain.value = gains[1];
    if (fade.elapsed >= fade.seconds) cancelFade();
  } else if ($('autoDj').checked && !fadeStarting && !isRecording) {
    const seconds = PlayerCore.fadeDuration(audio.duration, Number($('fadeSeconds').value));
    if (seconds && audio.currentTime > 0 && audio.duration - audio.currentTime <= seconds) beginFade(Math.max(.1, audio.duration - audio.currentTime));
  }
  if ($('autoVisual').value === 'energy' && !fade && ac.currentTime - energyClock > 12) {
    energyClock = ac.currentTime;
    const energy = avg(35, 2000);
    const next = energy > .45 ? 'radial' : energy > .22 ? 'bars' : 'particles';
    if (next !== style) { style = next; variant = STYLE_VARIANTS[style][0].id; updateStyleUI(); populateVariantOptions(); }
  }
}, 40);
function wrapText(text, maxWidth, font) {
  ctx.font = font; const result = []; let line = '';
  for (const word of text.split(/\s+/)) { const next = line ? line + ' ' + word : word; if (ctx.measureText(next).width > maxWidth && line) {result.push(line); line = word;} else line = next; }
  if (line) result.push(line); return result.slice(0, 2);
}
function drawPlayerOverlay(c) {
  const track = tracks[selectedTrack]; if (!track) return;
  const lines = track.lyrics || [], index = PlayerCore.lyricAt(lines, audio.currentTime, track.offset || 0);
  const lyric = index >= 0 ? lines[index].text : '';
  if (lyric !== lastLyric) { $('lyricPreview').textContent = lyric; lastLyric = lyric; }
  c.save(); c.shadowBlur = 0;
  const g = c.createLinearGradient(0, 530, 0, 720); g.addColorStop(0,'#0000');g.addColorStop(1,'#000d');c.fillStyle=g;c.fillRect(0,530,1280,190);
  c.textAlign='left';c.fillStyle='#d3d5df';c.font='500 17px Segoe UI';c.fillText((track.artist ? track.artist + '  /  ' : '') + track.title,40,682,1170);
  if ($('showLyrics').checked && lyric) {
    c.textAlign='center';c.shadowColor='#000';c.shadowBlur=12;c.fillStyle='#fff';
    const wrapped=wrapText(lyric,1120,'600 32px Segoe UI'); wrapped.forEach((line,i)=>c.fillText(line,640,603 + i*39,1120));
  }
  c.restore();
}
function setLyrics(track, text) {
  track.lrc = text.slice(0, 500000); track.lyrics = PlayerCore.parseLrc(track.lrc);
  if (track === tracks[selectedTrack]) {
    lastLyric = '\0';
    $('lyricStatus').textContent = track.lyrics.length ? track.lyrics.length + ' timed lines · synced to playback' : 'Plain lyrics only. Add timestamps in Edit for synced output.';
    if (!track.lyrics.length) $('lyricPreview').textContent = track.lrc;
  }
}
async function lookupLyrics(automatic = false) {
  const track = tracks[selectedTrack]; if (!track) return notify('Select a track first');
  if (!track.title || !track.artist) { $('lyricStatus').textContent = 'Enter track title and artist first. Tip: Artist - Title filenames are detected.'; return; }
  if (!window.hdrgDesktop?.findLyrics) { $('lyricStatus').textContent = 'Online lookup is available in the Windows app. Import LRC here.'; return; }
  const generation = ++lookupGeneration;
  $('lyricStatus').textContent = 'Looking up lyrics on LRCLIB…';
  try {
    const result = await window.hdrgDesktop.findLyrics({title:track.title,artist:track.artist,duration:track.duration || 0});
    if (generation !== lookupGeneration || track !== tracks[selectedTrack]) return;
    if (result.error) throw new Error(result.error);
    if (result.instrumental) { $('lyricStatus').textContent = 'This track is marked instrumental.'; return; }
    if (!result.syncedLyrics && !result.plainLyrics) { $('lyricStatus').textContent = 'No match. Check title/artist or import an LRC file.'; return; }
    setLyrics(track,result.syncedLyrics || result.plainLyrics);
    $('lyricStatus').textContent += ' · LRCLIB';
  } catch (e) { if (generation === lookupGeneration) $('lyricStatus').textContent = 'Lyrics unavailable: ' + e.message + '. You can still import LRC.'; }
}
$('lookupLyrics').onclick = () => lookupLyrics();
$('autoLyrics').onchange = () => { if ($('autoLyrics').checked && !tracks[selectedTrack]?.lrc) lookupLyrics(true); };
for (const [id,key] of [['trackTitle','title'],['trackArtist','artist']]) $(id).onchange = () => {
  const t=tracks[selectedTrack]; if (!t) return; t[key]=$(id).value.trim().slice(0,200); lookupGeneration++; onTrackChanged(); refreshPlaylist();
};
$('lyricOffset').onchange = () => { const t=tracks[selectedTrack]; if(t) t.offset=Math.max(-120,Math.min(120,Number($('lyricOffset').value)||0)); };
$('lrcInput').onchange = async e => { const t=tracks[selectedTrack], f=e.target.files[0]; if(!t||!f)return; if(f.size>500000)return notify('LRC file is too large'); lookupGeneration++; setLyrics(t,await f.text()); e.target.value=''; };
let editingTrack = null;
$('editLyrics').onclick = () => { editingTrack=tracks[selectedTrack]; if(!editingTrack)return notify('Select a track first'); $('lyricsText').value=editingTrack.lrc||''; $('lyricsDialog').showModal(); };
$('closeLyrics').onclick = () => $('lyricsDialog').close();
$('applyLyrics').onclick = () => { if(editingTrack) {lookupGeneration++;setLyrics(editingTrack,$('lyricsText').value);} $('lyricsDialog').close(); };
$('stampLyric').onclick = () => {
  if(editingTrack!==tracks[selectedTrack])return notify('The playing track has changed');
  const t=audio.currentTime, stamp='['+String(Math.floor(t/60)).padStart(2,'0')+':'+(t%60).toFixed(2).padStart(5,'0')+']';
  const el=$('lyricsText'); el.setRangeText(stamp,el.selectionStart,el.selectionEnd,'end'); el.focus();
};
$('exportLyrics').onclick = () => {const url=URL.createObjectURL(new Blob([$('lyricsText').value],{type:'text/plain'}));const a=document.createElement('a');a.href=url;a.download='HDRG-Lyrics.lrc';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
$('trackSearch').oninput = refreshPlaylist;
$('autoDj').onchange = () => { if (!$('autoDj').checked) cancelFade(); $('mixStatus').textContent=$('autoDj').checked?'AUTO DJ READY':'AUTO DJ OFF'; };
$('fadeSeconds').oninput = () => $('fadeValue').textContent=$('fadeSeconds').value+' s';
for(let i=0;i<3;i++) $('eq'+i).oninput=()=>{ if(eqFilters[i])eqFilters[i].gain.value=Number($('eq'+i).value); };
document.querySelectorAll('button[data-mode]').forEach(b=>b.onclick=()=>{document.body.dataset.mode=b.dataset.mode;document.querySelectorAll('button[data-mode]').forEach(x=>x.classList.toggle('active',x===b));});
document.addEventListener('keydown',e=>{if(e.code==='Space'&&!/INPUT|TEXTAREA|SELECT|BUTTON/.test(e.target.tagName)&&!$('lyricsDialog').open){e.preventDefault();togglePlayback();}});
if ('mediaSession' in navigator) {
  navigator.mediaSession.setActionHandler('play',()=>{if(audio.paused)togglePlayback();});
  navigator.mediaSession.setActionHandler('pause',()=>{if(!audio.paused)togglePlayback();});
  navigator.mediaSession.setActionHandler('nexttrack',()=>nextTrack(true));
  navigator.mediaSession.setActionHandler('previoustrack',()=>$('prevBtn').click());
}
// IndexedDB keeps the user-selected audio blobs and artwork offline; no audio uploads.
function openLibrary(){return new Promise((resolve,reject)=>{const r=indexedDB.open('hdrg-player-library',1);r.onupgradeneeded=()=>r.result.createObjectStore('state');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});}
async function saveLibrary(){
  if(libraryBusy)return;libraryBusy=true;
  try{
    const db=await openLibrary();
    const state={tracks:tracks.map(t=>({file:t.file,template:t.template,title:t.title,artist:t.artist,lrc:t.lrc,offset:t.offset})), selectedTrack, playMode, logo:logo?.src,bg:bg?.src,look:baseLook(),templates,settings:{autoDj:$('autoDj').checked,fadeSeconds:$('fadeSeconds').value,autoVisual:$('autoVisual').value,autoLyrics:$('autoLyrics').checked,showLyrics:$('showLyrics').checked}};
    await new Promise((resolve,reject)=>{const tx=db.transaction('state','readwrite');tx.objectStore('state').put(state,'library');tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error);});db.close();notify('Library saved on this device');
  }catch(e){notify('Could not save library: '+e.message);}finally{libraryBusy=false;}
}
async function restoreLibrary(silent=false){
  if(libraryBusy)return;libraryBusy=true;
  try{
    const db=await openLibrary();const saved=await new Promise((resolve,reject)=>{const r=db.transaction('state').objectStore('state').get('library');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});db.close();
    if(!saved){if(!silent)notify('No saved library yet');return;}
    cancelFade();audio.pause();tracks.forEach(t=>URL.revokeObjectURL(t.url));tracks.length=0;selectedTrack=-1;
    if(saved.templates?.length){templates=saved.templates;activeTemplate=templates[0].id;saveTemplateStore();refreshTemplates();}
    for(const t of saved.tracks||[]){const track={...makeTrack(t.file),...t};track.lyrics=PlayerCore.parseLrc(track.lrc||'');tracks.push(track);}
    playMode=saved.playMode||'sequential';$('playMode').value=playMode;
    for(const [key,value] of Object.entries(saved.settings||{})){const el=$(key);if(el){if(el.type==='checkbox')el.checked=value;else el.value=value;}}
    for(const kind of ['logo','bg'])if(saved[kind]){const img=new Image();img.onload=()=>{if(kind==='logo')logo=img;else bg=img;};img.src=saved[kind];}
    if(tracks.length)selectTrack(Math.max(0,Math.min(saved.selectedTrack,tracks.length-1)),false);else onTrackChanged();
    if(saved.look)loadLook({look:saved.look});refreshPlaylist();$('fadeSeconds').oninput();$('autoDj').onchange();
    if(!silent)notify('Saved library restored');
  }catch(e){notify('Could not restore library: '+e.message);}finally{libraryBusy=false;}
}
$('saveLibrary').onclick=saveLibrary;$('restoreLibrary').onclick=()=>restoreLibrary();
restoreLibrary(true);
