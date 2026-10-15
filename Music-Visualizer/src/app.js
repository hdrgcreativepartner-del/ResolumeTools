'use strict';
const $ = id => document.getElementById(id);
const canvas = $('visual'), ctx = canvas.getContext('2d');
let audio = $('audio');
const decks = [audio, document.createElement('audio')];
decks[1].preload = 'auto'; document.body.append(decks[1]);
const deckGains = new Map();
let masterGain, eqFilters = [];
let ac, analyser, source, dest, bins, wave, logo = null, bg = null, isRecording = false, recorder = null, chunks = [];
let style = 'radial', variant = 'halo';

const STYLE_VARIANTS = {
  bars: [
    { id: 'clean', name: 'Clean Bars', hint: 'Premium linear spectrum with smooth glow and balanced spacing.' },
    { id: 'split', name: 'Split Center', hint: 'Bars split from the center line for a broadcast-music feel.' },
    { id: 'mirror', name: 'Mirror Stack', hint: 'Mirrored top-bottom bars with a club-style energy.' },
    { id: 'stairs', name: 'Stairs', hint: 'Stepped spectrum blocks for a punchier graphic look.' },
    { id: 'prism', name: 'Prism', hint: 'Tapered bars and sharp highlights for a more futuristic identity.' },
    { id: 'skyline', name: 'Skyline', hint: 'A city-light skyline silhouette driven by audio intensity.' }
  ],
  radial: [
    { id: 'halo', name: 'Halo Ring', hint: 'Classic circular halo around the logo — the strongest core look.' },
    { id: 'lotus', name: 'Lotus Bloom', hint: 'Alternating short and long petals for a more decorative radial form.' },
    { id: 'polygon', name: 'Polygon Core', hint: 'Hex-polygon reactive ring inspired by geometric template styles.' },
    { id: 'dual', name: 'Dual Ring', hint: 'Two independent rings for layered bass and treble motion.' },
    { id: 'comet', name: 'Comet Arc', hint: 'Broken radial arc with motion gaps and accent clusters.' },
    { id: 'orbit', name: 'Orbit Dots', hint: 'Floating orbit particles plus fine radial lines around the logo.' }
  ],
  wave: [
    { id: 'classic', name: 'Classic Line', hint: 'Single clean waveform with cinematic softness.' },
    { id: 'double', name: 'Dual Wave', hint: 'Two synchronized waves for a richer stereo-style motion.' },
    { id: 'ribbon', name: 'Ribbon Fill', hint: 'A filled ribbon band that breathes with the music.' },
    { id: 'stacked', name: 'Stacked Flow', hint: 'Three flowing waveform layers for more texture.' },
    { id: 'scope', name: 'Oscilloscope', hint: 'Sharp scope-inspired waveform with subtle guide grid.' },
    { id: 'mirror', name: 'Mirror Wave', hint: 'Mirrored top/bottom wave motion for ambient and EDM tracks.' }
  ],
  pulse: [
    { id: 'soft', name: 'Soft Pulse', hint: 'Minimal expanding circles with a premium soft aura.' },
    { id: 'flare', name: 'Flare Core', hint: 'Bright bloom pulse focused around the center logo.' },
    { id: 'rings', name: 'Impact Rings', hint: 'Thicker ring bursts with more visible beat accents.' },
    { id: 'sonar', name: 'Sonar Sweep', hint: 'Scanning arcs and pulse rings for a tech / sci-fi look.' },
    { id: 'target', name: 'Target Lock', hint: 'Concentric targeting circles with radial ticks.' },
    { id: 'bloom', name: 'Bloom Disc', hint: 'Large translucent bloom disc with soft beat breathing.' }
  ],
  particles: [
    { id: 'float', name: 'Float', hint: 'Floating particles that stay within frame and swell on beat.' },
    { id: 'orbit', name: 'Orbit', hint: 'Particles orbit the center while pulsing in size.' },
    { id: 'drift', name: 'Drift', hint: 'Slow directional drift with subtle parallax and beat bounce.' },
    { id: 'spark', name: 'Spark', hint: 'Sharper particle reactions for brighter, more rhythmic motion.' },
    { id: 'cluster', name: 'Cluster', hint: 'Particle cloud concentrated near center with bass pressure.' },
    { id: 'nebula', name: 'Nebula', hint: 'Soft diffuse particle field with dreamy motion and beat bloom.' }
  ],
  tunnel: [
    { id: 'square', name: 'Square', hint: 'Classic square tunnel frames with audio-synced depth.' },
    { id: 'diamond', name: 'Diamond', hint: 'Rotated square tunnel for a sharper angular feel.' },
    { id: 'hex', name: 'Hex Portal', hint: 'Hexagonal portal steps for a more futuristic scene.' },
    { id: 'grid', name: 'Grid Corridor', hint: 'Tunnel plus receding guide grid inspired by motion templates.' },
    { id: 'portal', name: 'Portal Glow', hint: 'Soft portal rings and lens glow with depth motion.' },
    { id: 'vortex', name: 'Vortex', hint: 'Spiral-esque layered rings for an immersive trance effect.' }
  ]
};

const conf = {
  color: '#8a74ff', sensitivity: 1.5, smooth: .75, logoSize: 24, dim: 45,
  intensity: 1, logoFx: .7, logoGlow: .6, parallax: .6, flash: .25,
  cameraShake: .1, logoMode: 'bass', bgMode: 'parallax'
};

function createParticles(count = 210) {
  const margin = 90;
  return Array.from({ length: count }, () => {
    const x = margin + Math.random() * (1280 - margin * 2);
    const y = margin + Math.random() * (720 - margin * 2);
    return {
      x, y,
      baseX: x, baseY: y,
      r: .9 + Math.random() * 2.8,
      sizePulse: .5 + Math.random() * .9,
      speed: .2 + Math.random() * 1.2,
      phase: Math.random() * Math.PI * 2,
      vx: (Math.random() - .5) * .5,
      vy: (Math.random() - .5) * .5,
      orbit: 22 + Math.random() * 180,
      angle: Math.random() * Math.PI * 2,
      glow: .3 + Math.random() * .9,
      twinkle: .5 + Math.random() * .6,
      driftDir: Math.random() > .5 ? 1 : -1
    };
  });
}
const particles = createParticles();

function notify(msg) {
  $('toast').textContent = msg;
  $('toast').style.display = 'block';
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => $('toast').style.display = 'none', 3800);
}

function setupAudio() {
  if (ac) return;
  ac = new (window.AudioContext || window.webkitAudioContext)();
  analyser = ac.createAnalyser(); analyser.fftSize = 2048; analyser.smoothingTimeConstant = conf.smooth;
  bins = new Uint8Array(analyser.frequencyBinCount);
  wave = new Uint8Array(analyser.fftSize);
  dest = ac.createMediaStreamDestination();
  for (const deck of decks) {
    const node = ac.createMediaElementSource(deck), gain = ac.createGain();
    node.connect(gain); gain.connect(analyser); deckGains.set(deck, gain);
  }
  eqFilters = [100, 1000, 8000].map((hz, i) => {
    const f = ac.createBiquadFilter(); f.type = ['lowshelf', 'peaking', 'highshelf'][i]; f.frequency.value = hz; f.Q.value = .7; return f;
  });
  masterGain = ac.createGain(); masterGain.gain.value = Number($('volume').value);
  analyser.connect(eqFilters[0]); eqFilters[0].connect(eqFilters[1]); eqFilters[1].connect(eqFilters[2]);
  eqFilters[2].connect(masterGain); masterGain.connect(ac.destination); masterGain.connect(dest);
  eqFilters.forEach((f, i) => f.gain.value = Number($('eq' + i)?.value || 0));
}

function readFileImage(input, cb, nameId) {
  let f = input.files?.[0]; if (!f) return;
  const fr = new FileReader();
  fr.onload = () => { const img = new Image(); img.onload = () => { cb(img); $(nameId).textContent = f.name; notify('Asset loaded: ' + f.name) }; img.onerror = () => notify('Image could not be decoded'); img.src = fr.result; };
  fr.readAsDataURL(f);
}

const tracks = []; let selectedTrack = -1; let playMode = 'sequential'; let templates = []; let activeTemplate = null;
const templateStore = 'hdrg-music-visualizer-templates-v04';
const baseLook = () => ({ style, variant, conf: { ...conf } });
function saveTemplateStore() { try { localStorage.setItem(templateStore, JSON.stringify(templates)) } catch (e) { notify('Unable to save templates: ' + e.message) } }
function refreshTemplates() {
  const sel = $('templateSelect'), previous = activeTemplate; sel.replaceChildren();
  for (const t of templates) { let o = document.createElement('option'); o.value = t.id; o.textContent = t.name; sel.append(o); }
  if (previous && templates.some(t => t.id === previous)) sel.value = previous; else activeTemplate = templates[0]?.id || null;
  if (activeTemplate) sel.value = activeTemplate;
  refreshPlaylist();
}
function updateStyleUI() {
  document.querySelectorAll('[data-style]').forEach(x => x.classList.toggle('active', x.dataset.style === style));
}
function populateVariantOptions() {
  const sel = $('variantSelect');
  sel.replaceChildren();
  const variants = STYLE_VARIANTS[style] || [];
  if (!variants.some(v => v.id === variant)) variant = variants[0]?.id || '';
  for (const v of variants) {
    const o = document.createElement('option');
    o.value = v.id; o.textContent = v.name; sel.append(o);
  }
  sel.value = variant;
  $('variantHint').textContent = variants.find(v => v.id === variant)?.hint || 'Select a category, then choose one of several visual variations.';
}
function loadLook(t) {
  if (!t) return;
  style = t.look.style || 'radial';
  variant = t.look.variant || (STYLE_VARIANTS[style]?.[0]?.id || 'halo');
  updateStyleUI(); populateVariantOptions();
  for (const [k, v] of Object.entries(t.look.conf || {})) {
    if (!(k in conf)) continue;
    conf[k] = v;
    const el = $(k);
    if (el) { el.value = v; el.dispatchEvent(new Event(k.endsWith('Mode') ? 'change' : 'input')); }
  }
}
function refreshPlaylist() {
  const el = $('playlist'); el.replaceChildren(); $('trackCount').textContent = tracks.length + ' track' + (tracks.length === 1 ? '' : 's');
  if (!tracks.length) { const empty = document.createElement('div'); empty.className = 'empty'; empty.textContent = 'Import multiple songs to start a playlist'; el.append(empty); return; }
  tracks.forEach((t, i) => {
    const r = document.createElement('div'); r.className = 'track' + (i === selectedTrack ? ' playing' : '');
    if ($('trackSearch')?.value && !(t.title + ' ' + t.artist + ' ' + t.file.name).toLowerCase().includes($('trackSearch').value.toLowerCase())) return;
    const play = document.createElement('button'); play.className = 'trackPlay'; play.textContent = i === selectedTrack && !audio.paused ? '❚❚' : '▶'; play.title = 'Play this track'; play.onclick = () => i === selectedTrack ? togglePlayback() : selectTrack(i, true);
    const body = document.createElement('div'); body.className = 'trackBody';
    const name = document.createElement('strong'); name.textContent = (i + 1) + '. ' + (t.title || t.file.name); name.title = t.file.name;
    const assigned = templates.find(x => x.id === t.template);
    const detail = document.createElement('small'); detail.textContent = assigned ? `${assigned.name} · ${assigned.look.style}/${assigned.look.variant || '-'}` : 'Unassigned';
    if (t.artist) detail.textContent = t.artist + ' · ' + detail.textContent;
    body.append(name, detail); body.onclick = () => selectTrack(i, true);
    const assign = document.createElement('button'); assign.className = 'trackAssign'; assign.textContent = 'Style ▾'; assign.title = 'Assign selected template'; assign.onclick = () => { t.template = activeTemplate; refreshPlaylist(); notify('Template assigned to ' + t.file.name) };
    const del = document.createElement('button'); del.className = 'trackDelete'; del.textContent = '×'; del.title = 'Remove track'; del.onclick = () => removeTrack(i);
    const up = document.createElement('button'); up.textContent = '↑'; up.title = 'Move up'; up.disabled = i === 0; up.onclick = () => moveTrack(i, i - 1);
    r.append(play, body, up, assign, del); el.append(r);
  });
}
function selectTrack(i, autoplay = false, crossfade = false) {
  if (i < 0 || i >= tracks.length) return;
  if (!crossfade) cancelFade();
  selectedTrack = i; audio.src = tracks[i].url; audio.load(); $('audioName').textContent = tracks[i].file.name;
  const tid = tracks[i].template; const template = templates.find(x => x.id === tid);
  if (template) { activeTemplate = tid; $('templateSelect').value = tid; loadLook(template); }
  setupAudio(); refreshPlaylist(); onTrackChanged();
  if (autoplay) { ac.resume().then(() => audio.play()).catch(e => notify('Audio playback failed: ' + e.message)); }
}
function removeTrack(i) {
  cancelFade();
  const wasSelected = i === selectedTrack, old = tracks.splice(i, 1)[0];
  if (wasSelected) { audio.pause(); audio.removeAttribute('src'); audio.load(); selectedTrack = -1; }
  URL.revokeObjectURL(old.url); if (i < selectedTrack) selectedTrack--;
  if (wasSelected && tracks.length) selectTrack(Math.min(i, tracks.length - 1), false);
  if (!tracks.length) { $('audioName').textContent = 'No audio loaded'; onTrackChanged(); } refreshPlaylist();
}
$('audioInput').addEventListener('change', e => {
  const list = Array.from(e.target.files || []).filter(f => f.type.startsWith('audio/') || /\.(mp3|wav|ogg|flac|m4a|aac|opus)$/i.test(f.name));
  if (!list.length) return;
  for (const f of list) tracks.push(makeTrack(f));
  if (selectedTrack < 0) selectTrack(0, false);
  refreshPlaylist(); notify(list.length + ' tracks added to playlist'); e.target.value = '';
});
$('prevBtn').onclick = () => { if (!tracks.length) return; selectTrack((selectedTrack - 1 + tracks.length) % tracks.length, true) };
$('nextBtn').onclick = () => nextTrack(true);
$('clearPlaylistBtn').onclick = () => { cancelFade(); audio.pause(); audio.removeAttribute('src'); audio.load(); tracks.forEach(t => URL.revokeObjectURL(t.url)); tracks.length = 0; selectedTrack = -1; $('audioName').textContent = 'No audio loaded'; onTrackChanged(); refreshPlaylist(); };
$('playMode').onchange = e => playMode = e.target.value;
function nextTrack(manual = false) {
  if (!tracks.length) return;
  if (!manual && playMode === 'sequential' && selectedTrack === tracks.length - 1) return;
  if (playMode === 'shuffle' && tracks.length > 1) { let i; do { i = Math.floor(Math.random() * tracks.length) } while (i === selectedTrack); selectTrack(i, true); }
  else if (playMode === 'repeat' || manual || selectedTrack < tracks.length - 1) selectTrack((selectedTrack + 1) % tracks.length, true);
}
try { const saved = JSON.parse(localStorage.getItem(templateStore) || '[]'); if (Array.isArray(saved)) templates = saved.filter(t => t && t.id && t.look && t.name); } catch (e) { console.warn('Invalid template data:', e); }
if (!templates.length) { templates = [{ id: 'default-radial', name: 'Radial Stage', look: baseLook() }]; saveTemplateStore(); }
activeTemplate = templates[0].id; refreshTemplates(); updateStyleUI(); populateVariantOptions(); loadLook(templates[0]);
$('templateSelect').onchange = e => { activeTemplate = e.target.value; loadLook(templates.find(t => t.id === activeTemplate)); notify('Template previewed — apply to song(s) if needed') };
$('saveTemplateBtn').onclick = () => { const name = $('templateName').value.trim() || 'Custom Style ' + (templates.length + 1); const id = 'look-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6); templates.push({ id, name, look: baseLook() }); activeTemplate = id; saveTemplateStore(); refreshTemplates(); $('templateName').value = ''; notify('Template saved: ' + name) };
$('assignTrackBtn').onclick = () => { if (selectedTrack < 0) return notify('Select a track first'); tracks[selectedTrack].template = activeTemplate; refreshPlaylist(); notify('Template applied to selected song') };
$('assignAllBtn').onclick = () => { tracks.forEach(t => t.template = activeTemplate); refreshPlaylist(); notify('Template applied to all ' + tracks.length + ' songs') };
$('deleteTemplateBtn').onclick = () => { if (templates.length <= 1) return notify('Keep at least one template'); const old = activeTemplate; templates = templates.filter(t => t.id !== old); tracks.forEach(t => { if (t.template === old) t.template = templates[0].id }); activeTemplate = templates[0].id; saveTemplateStore(); refreshTemplates(); loadLook(templates[0]); notify('Template deleted') };
$('logoInput').addEventListener('change', e => readFileImage(e.target, i => logo = i, 'logoName'));
$('bgInput').addEventListener('change', e => readFileImage(e.target, i => bg = i, 'bgName'));
$('styleGrid').addEventListener('click', e => { const b = e.target.closest('[data-style]'); if (!b) return; style = b.dataset.style; variant = STYLE_VARIANTS[style]?.[0]?.id || variant; updateStyleUI(); populateVariantOptions(); });
$('variantSelect').addEventListener('change', e => { variant = e.target.value; populateVariantOptions(); });
for (const key of ['color', 'sensitivity', 'smooth', 'logoSize', 'dim', 'intensity', 'logoFx', 'logoGlow', 'parallax', 'flash', 'cameraShake']) {
  $(key).addEventListener('input', e => {
    conf[key] = key === 'color' ? e.target.value : Number(e.target.value);
    if (key === 'smooth' && analyser) analyser.smoothingTimeConstant = conf.smooth;
    const o = $(key + 'Val'); if (o) o.textContent = (key === 'dim' || key === 'logoSize') ? conf[key] + '%' : conf[key].toFixed(key === 'smooth' ? 2 : 1);
  });
}
for (const key of ['logoMode', 'bgMode']) $(key).addEventListener('change', e => conf[key] = e.target.value);
const presets = {
  club: { color: '#8a74ff', logoMode: 'bass', logoFx: .8, logoGlow: .9, bgMode: 'parallax', parallax: .7, flash: .2, cameraShake: .1 },
  cinema: { color: '#d9a65b', logoMode: 'breathe', logoFx: .4, logoGlow: .25, bgMode: 'slow', parallax: .4, flash: .08, cameraShake: 0 },
  rave: { color: '#00f5dc', logoMode: 'shake', logoFx: 1.2, logoGlow: 1.5, bgMode: 'bass', parallax: 1, flash: .7, cameraShake: .4 },
  ambient: { color: '#69aafa', logoMode: 'rotate', logoFx: .2, logoGlow: .45, bgMode: 'parallax', parallax: .35, flash: 0, cameraShake: 0 }
};
$('presetGrid').addEventListener('click', e => {
  const b = e.target.closest('[data-preset]'); if (!b) return;
  for (const [k, v] of Object.entries(presets[b.dataset.preset])) { conf[k] = v; const el = $(k); if (el) { el.value = v; el.dispatchEvent(new Event(k.endsWith('Mode') ? 'change' : 'input')); } }
  document.querySelectorAll('.preset').forEach(x => x.classList.toggle('active', x === b)); notify('Preset: ' + b.textContent);
});
$('playBtn').addEventListener('click', togglePlayback);
const fmt = s => { if (!Number.isFinite(s)) return '00:00'; return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(Math.floor(s % 60)).padStart(2, '0')}` };
for (const deck of decks) {
  deck.addEventListener('play', () => { if (deck === audio) { $('playBtn').textContent = 'Ⅱ'; refreshPlaylist(); } });
  deck.addEventListener('pause', () => { if (deck === audio) { $('playBtn').textContent = '▶'; refreshPlaylist(); } });
  deck.addEventListener('timeupdate', () => { if (deck !== audio) return; $('currentTime').textContent = fmt(audio.currentTime); $('seek').value = audio.duration ? 1000 * audio.currentTime / audio.duration : 0; });
  deck.addEventListener('loadedmetadata', () => { if (deck !== audio) return; $('duration').textContent = fmt(audio.duration); if (tracks[selectedTrack]) tracks[selectedTrack].duration = audio.duration; });
  deck.addEventListener('error', () => { if (deck === audio) { cancelFade(); notify('This audio cannot be played. Try MP3, WAV, FLAC or OGG.'); } });
  deck.addEventListener('ended', () => { if (deck !== audio) return; if (isRecording && recorder?.state === 'recording') { recorder.stop(); return; } nextTrack(false); });
}
$('seek').addEventListener('input', e => { cancelFade(); if (Number.isFinite(audio.duration)) audio.currentTime = audio.duration * Number(e.target.value) / 1000; });
$('volume').addEventListener('input', e => { if (masterGain) masterGain.gain.value = Number(e.target.value); });
$('fullscreenBtn').addEventListener('click', async () => { try { await $('stage').requestFullscreen() } catch { notify('Fullscreen is not supported on this display') } }); $('exitFullscreen').addEventListener('click', () => document.exitFullscreen());
$('resetBtn').addEventListener('click', () => {
  for (const [key, val] of Object.entries({ sensitivity: 1.5, smooth: .75, logoSize: 24, dim: 45, intensity: 1, color: '#8a74ff', logoFx: .7, logoGlow: .6, parallax: .6, flash: .25, cameraShake: .1 })) { conf[key] = val; $(key).value = val; $(key).dispatchEvent(new Event('input')); }
  style = 'radial'; variant = 'halo'; updateStyleUI(); populateVariantOptions(); if (analyser) analyser.smoothingTimeConstant = .75; notify('Parameters reset');
});
function avg(a, b) {
  if (!bins) return 0; const hz = (ac?.sampleRate || 48000) / 2048; let lo = Math.max(0, Math.floor(a / hz)), hi = Math.min(bins.length, Math.ceil(b / hz)), sum = 0;
  for (let i = lo; i < hi; i++) sum += bins[i]; return hi > lo ? sum / (hi - lo) / 255 : 0;
}
function roundRect(x, y, w, h, r) { ctx.beginPath(); ctx.roundRect(x, y, w, h, r); }
function drawPoly(cx, cy, radius, sides, rotation = 0) {
  ctx.beginPath();
  for (let i = 0; i < sides; i++) {
    const a = rotation + Math.PI * 2 * i / sides - Math.PI / 2;
    const x = cx + Math.cos(a) * radius, y = cy + Math.sin(a) * radius;
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  }
  ctx.closePath();
}
function drawGrid(bgAlpha=.08) {
  ctx.save(); ctx.strokeStyle = `rgba(255,255,255,${bgAlpha})`; ctx.lineWidth = 1;
  for (let x = 120; x <= 1160; x += 80) { ctx.beginPath(); ctx.moveTo(x, 160); ctx.lineTo(x, 560); ctx.stroke(); }
  for (let y = 160; y <= 560; y += 80) { ctx.beginPath(); ctx.moveTo(120, y); ctx.lineTo(1160, y); ctx.stroke(); }
  ctx.restore();
}
let beatPulse = 0, previousBass = 0;
function render(now) {
  requestAnimationFrame(render);
  const w = 1280, h = 720, t = now / 1000;
  if (analyser) { analyser.getByteFrequencyData(bins); analyser.getByteTimeDomainData(wave); }
  const bass = avg(35, 180) * conf.sensitivity, mid = avg(180, 2000) * conf.sensitivity, treble = avg(2000, 10000) * conf.sensitivity;
  const beat = Math.max(.02, Math.min(1.7, bass * conf.intensity)); const onset = Math.max(0, bass - previousBass * .94); previousBass = previousBass * .7 + bass * .3; beatPulse = Math.max(beatPulse * .88, Math.min(1, onset * 2.6)); const kick = Math.min(1, beatPulse + Math.max(0, beat - .45) * .3);
  for (const [id, val] of [['bassMeter', bass], ['midMeter', mid], ['trebleMeter', treble]]) $(id).style.width = Math.min(100, val * 100) + '%';

  ctx.clearRect(0, 0, w, h); ctx.fillStyle = '#070813'; ctx.fillRect(0, 0, w, h); ctx.save();
  const shake = conf.cameraShake * kick * 14; ctx.translate(Math.sin(t * 59) * shake, Math.cos(t * 47) * shake);

  if (bg) {
    const zoom = conf.bgMode === 'bass' ? kick * .13 * conf.parallax : conf.bgMode === 'slow' ? (Math.sin(t * .3) * .045 + .045) * conf.parallax : conf.bgMode === 'parallax' ? .035 * conf.parallax : 0;
    const r = Math.max(w / bg.width, h / bg.height) * (1 + zoom + .06 * conf.parallax), dw = bg.width * r, dh = bg.height * r;
    const drift = conf.bgMode === 'parallax' ? conf.parallax : 0;
    ctx.drawImage(bg, (w - dw) / 2 + Math.sin(t * .38) * 25 * drift, (h - dh) / 2 + Math.cos(t * .29) * 20 * drift, dw, dh);
    ctx.fillStyle = `rgba(2,3,12,${conf.dim / 100})`; ctx.fillRect(0, 0, w, h);
  } else {
    const g = ctx.createRadialGradient(640, 360, 10, 640, 360, 850);
    g.addColorStop(0, '#252044'); g.addColorStop(.5, '#101126'); g.addColorStop(1, '#05060c'); ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
  }

  const color = conf.color;
  ctx.save(); ctx.strokeStyle = color; ctx.fillStyle = color; ctx.shadowColor = color; ctx.shadowBlur = 16 + beat * 30;

  if (style === 'bars') {
    const n = variant === 'clean' ? 80 : variant === 'prism' ? 64 : 92;
    const gap = variant === 'stairs' ? 4 : 3;
    const bw = 1000 / n - gap, left = 140;
    const baseY = variant === 'split' ? 360 : variant === 'mirror' ? 360 : 530;
    for (let i = 0; i < n; i++) {
      const ratio = i / n;
      const idx = Math.floor(Math.pow(ratio, variant === 'skyline' ? 1.2 : 1.65) * (bins?.length || 1) * .42);
      const v = (bins ? bins[idx] / 255 : (.18 + .1 * Math.sin(t * 3 + i * .23))) * conf.sensitivity * conf.intensity;
      const bh = Math.min(330, 8 + v * 250 + kick * 28);
      const x = left + i * (bw + gap);
      const grad = ctx.createLinearGradient(0, baseY - bh, 0, baseY + bh);
      grad.addColorStop(0, '#ffffff'); grad.addColorStop(.22, color); grad.addColorStop(1, color);
      ctx.fillStyle = grad; ctx.globalAlpha = .42 + Math.min(.58, v * .55);
      if (variant === 'split') {
        roundRect(x, 360 - bh, bw, bh, 3); ctx.fill();
        roundRect(x, 360, bw, bh, 3); ctx.fill();
      } else if (variant === 'mirror') {
        roundRect(x, 320 - bh * .75, bw, bh * .75, 3); ctx.fill();
        roundRect(x, 400, bw, bh * .75, 3); ctx.fill();
      } else if (variant === 'stairs') {
        const step = Math.max(3, Math.floor(bh / 18));
        for (let s = 0; s < 6; s++) { roundRect(x, 530 - step * (s + 1) * 3, bw, step * 2.3, 2); ctx.fill(); }
      } else if (variant === 'prism') {
        ctx.beginPath(); ctx.moveTo(x, 530); ctx.lineTo(x + bw, 530); ctx.lineTo(x + bw * .7, 530 - bh); ctx.lineTo(x + bw * .3, 530 - bh); ctx.closePath(); ctx.fill();
      } else if (variant === 'skyline') {
        roundRect(x, 530 - bh, bw, bh, 2); ctx.fill();
        if (i % 7 === 0) { ctx.globalAlpha *= .8; roundRect(x + bw * .35, 530 - bh - 16, bw * .3, 16, 1); ctx.fill(); }
      } else {
        roundRect(x, 530 - bh, bw, bh, 3); ctx.fill();
      }
      ctx.globalAlpha = 1;
    }
  }
  else if (style === 'radial') {
    const n = variant === 'orbit' ? 120 : 160;
    if (variant === 'dual') {
      for (let i = 0; i < n; i++) {
        let a = Math.PI * 2 * i / n - Math.PI / 2;
        let idx = Math.floor(i / n * (bins?.length || 1) * .7);
        let bassV = (bins ? bins[idx] / 255 : .15 + .12 * Math.sin(t * 2 + i)) * conf.sensitivity * conf.intensity;
        let r1 = 145 + beat * 18, len1 = 10 + Math.min(110, bassV * 100);
        ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(640 + Math.cos(a) * r1, 360 + Math.sin(a) * r1); ctx.lineTo(640 + Math.cos(a) * (r1 + len1), 360 + Math.sin(a) * (r1 + len1)); ctx.stroke();
        let r2 = 205 + beat * 8, len2 = 6 + Math.min(60, (treble * .9 + bassV * .2) * 55);
        ctx.globalAlpha = .55; ctx.beginPath(); ctx.moveTo(640 + Math.cos(a) * r2, 360 + Math.sin(a) * r2); ctx.lineTo(640 + Math.cos(a) * (r2 + len2), 360 + Math.sin(a) * (r2 + len2)); ctx.stroke(); ctx.globalAlpha = 1;
      }
      ctx.beginPath(); ctx.arc(640, 360, 148 + beat * 9, 0, 7); ctx.stroke();
      ctx.beginPath(); ctx.arc(640, 360, 208 + beat * 4, 0, 7); ctx.globalAlpha = .4; ctx.stroke(); ctx.globalAlpha = 1;
    } else if (variant === 'polygon') {
      const sides = 6, core = 148 + beat * 20;
      for (let i = 0; i < n; i++) {
        const a = Math.PI * 2 * i / n - Math.PI / 2;
        const idx = Math.floor(i / n * (bins?.length || 1) * .75);
        const v = (bins ? bins[idx] / 255 : .2) * conf.sensitivity * conf.intensity;
        const r = core + (i % (n / sides)) * .25;
        const len = 10 + Math.min(120, v * 120);
        ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(640 + Math.cos(a) * r, 360 + Math.sin(a) * r); ctx.lineTo(640 + Math.cos(a) * (r + len), 360 + Math.sin(a) * (r + len)); ctx.stroke();
      }
      ctx.lineWidth = 2.5; drawPoly(640, 360, core - 12, 6, t * .12); ctx.stroke();
      ctx.globalAlpha = .35; drawPoly(640, 360, core + 16, 6, -t * .08); ctx.stroke(); ctx.globalAlpha = 1;
    } else if (variant === 'lotus') {
      for (let i = 0; i < n; i++) {
        let a = Math.PI * 2 * i / n - Math.PI / 2, idx = Math.floor(i / n * (bins?.length || 1) * .75), v = (bins ? bins[idx] / 255 : .15) * conf.sensitivity * conf.intensity;
        let r = 148 + beat * 20 + (i % 2 ? 6 : 0), len = (i % 2 ? 24 : 12) + Math.min(150, v * 135);
        ctx.lineWidth = i % 2 ? 3 : 2; ctx.beginPath(); ctx.moveTo(640 + Math.cos(a) * r, 360 + Math.sin(a) * r); ctx.lineTo(640 + Math.cos(a) * (r + len), 360 + Math.sin(a) * (r + len)); ctx.stroke();
      }
      ctx.beginPath(); ctx.arc(640, 360, 146 + beat * 16, 0, 7); ctx.stroke();
    } else if (variant === 'comet') {
      for (let i = 0; i < n; i++) {
        if ((i + Math.floor(t * 30)) % 13 < 3) continue;
        let a = Math.PI * 2 * i / n - Math.PI / 2, idx = Math.floor(i / n * (bins?.length || 1) * .75), v = (bins ? bins[idx] / 255 : .15) * conf.sensitivity * conf.intensity;
        let r = 154 + beat * 17, len = 14 + Math.min(180, v * 150);
        ctx.lineWidth = 3; ctx.globalAlpha = .35 + ((i % 18) / 18); ctx.beginPath(); ctx.moveTo(640 + Math.cos(a) * r, 360 + Math.sin(a) * r); ctx.lineTo(640 + Math.cos(a) * (r + len), 360 + Math.sin(a) * (r + len)); ctx.stroke();
      }
      ctx.globalAlpha = 1;
      ctx.beginPath(); ctx.arc(640, 360, 151 + beat * 15, t * .4, t * .4 + Math.PI * 1.7); ctx.stroke();
    } else if (variant === 'orbit') {
      for (let i = 0; i < n; i++) {
        let a = Math.PI * 2 * i / n - Math.PI / 2, idx = Math.floor(i / n * (bins?.length || 1) * .7), v = (bins ? bins[idx] / 255 : .15) * conf.sensitivity * conf.intensity;
        let r = 160 + beat * 18, len = 10 + Math.min(85, v * 90);
        ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(640 + Math.cos(a) * r, 360 + Math.sin(a) * r); ctx.lineTo(640 + Math.cos(a) * (r + len), 360 + Math.sin(a) * (r + len)); ctx.stroke();
      }
      const orbitR = 234 + Math.sin(t * .5) * 8;
      for (let i = 0; i < 28; i++) {
        const a = t * .3 + i / 28 * Math.PI * 2;
        const px = 640 + Math.cos(a) * orbitR, py = 360 + Math.sin(a) * orbitR;
        const sz = 2.5 + kick * 3 + (i % 3) * .6;
        ctx.globalAlpha = .3 + (i % 5) * .08; ctx.beginPath(); ctx.arc(px, py, sz, 0, Math.PI * 2); ctx.fill();
      }
      ctx.globalAlpha = 1; ctx.beginPath(); ctx.arc(640, 360, 152 + beat * 16, 0, 7); ctx.stroke();
    } else {
      for (let i = 0; i < n; i++) {
        let a = Math.PI * 2 * i / n - Math.PI / 2, idx = Math.floor(i / n * (bins?.length || 1) * .75), v = (bins ? bins[idx] / 255 : .15 + .12 * Math.sin(t * 2 + i)) * conf.sensitivity * conf.intensity;
        let r = 160 + beat * 18, len = 12 + Math.min(190, v * 160); ctx.lineWidth = 3;
        ctx.beginPath(); ctx.moveTo(640 + Math.cos(a) * r, 360 + Math.sin(a) * r); ctx.lineTo(640 + Math.cos(a) * (r + len), 360 + Math.sin(a) * (r + len)); ctx.stroke();
      }
      ctx.beginPath(); ctx.arc(640, 360, 147 + beat * 18, 0, 7); ctx.lineWidth = 2; ctx.stroke();
    }
  }
  else if (style === 'wave') {
    if (variant === 'scope') drawGrid(.06);
    const layers = variant === 'stacked' ? 3 : variant === 'double' ? 2 : 1;
    const baseAmp = variant === 'ribbon' ? 150 : 180;
    for (let line = 0; line < layers; line++) {
      ctx.beginPath(); ctx.lineWidth = variant === 'scope' ? 2 : 3 + line; ctx.globalAlpha = 1 - line * .22;
      const upper = [], lower = [];
      for (let i = 0; i < 1024; i++) {
        let x = i / 1023 * 1120 + 80;
        let s = wave ? (wave[i * 2] - 128) / 128 : Math.sin(i * .03 + t * 4) * .2;
        let y = 360 + s * (baseAmp + beat * 70) * (1 - line * .16) + Math.sin(i * .009 + t * 2 + line) * 12;
        if (variant === 'mirror') {
          const yTop = 360 - Math.abs(s) * (145 + beat * 60) - line * 12;
          const yBottom = 360 + Math.abs(s) * (145 + beat * 60) + line * 12;
          if (i === 0) ctx.moveTo(x, yTop); else ctx.lineTo(x, yTop);
          upper.push([x, yTop]); lower.push([x, yBottom]);
        } else if (variant === 'ribbon') {
          upper.push([x, y - 20 - line * 4]); lower.push([x, y + 20 + line * 4]);
          if (i === 0) ctx.moveTo(x, y - 20); else ctx.lineTo(x, y - 20);
        } else {
          if (i === 0) ctx.moveTo(x, y + (variant === 'double' && line === 1 ? 70 : 0)); else ctx.lineTo(x, y + (variant === 'double' && line === 1 ? 70 : 0));
        }
      }
      if (variant === 'ribbon') {
        for (let i = lower.length - 1; i >= 0; i--) ctx.lineTo(lower[i][0], lower[i][1]);
        ctx.closePath(); ctx.globalAlpha = .15 + kick * .15; ctx.fillStyle = color; ctx.fill(); ctx.globalAlpha = .95; ctx.stroke();
      } else if (variant === 'mirror') {
        for (let i = lower.length - 1; i >= 0; i--) ctx.lineTo(lower[i][0], lower[i][1]);
        ctx.globalAlpha = .1; ctx.fillStyle = color; ctx.fill(); ctx.globalAlpha = .95; ctx.stroke();
      } else {
        ctx.stroke();
      }
    }
    if (variant === 'double') {
      ctx.beginPath(); ctx.lineWidth = 3; ctx.globalAlpha = .65;
      for (let i = 0; i < 1024; i++) {
        let x = i / 1023 * 1120 + 80, s = wave ? (wave[i * 2] - 128) / 128 : Math.sin(i * .03 + t * 4) * .2, y = 290 + s * (110 + beat * 35);
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.stroke(); ctx.globalAlpha = 1;
    }
  }
  else if (style === 'pulse') {
    if (variant === 'sonar') {
      for (let i = 5; i >= 0; i--) {
        let r = 88 + i * 52 + beat * 75, alpha = Math.max(.06, .65 - i * .1);
        ctx.globalAlpha = alpha; ctx.lineWidth = 2 + i * .8; ctx.beginPath(); ctx.arc(640, 360, r, t * .55, t * .55 + Math.PI * 1.1); ctx.stroke();
      }
    } else if (variant === 'target') {
      for (let i = 0; i < 6; i++) { let r = 95 + i * 40 + beat * 22; ctx.globalAlpha = .17 + i * .07; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(640, 360, r, 0, Math.PI * 2); ctx.stroke(); }
      for (let i = 0; i < 24; i++) { let a = Math.PI * 2 * i / 24; ctx.globalAlpha = .5; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(640 + Math.cos(a) * 235, 360 + Math.sin(a) * 235); ctx.lineTo(640 + Math.cos(a) * 255, 360 + Math.sin(a) * 255); ctx.stroke(); }
    } else {
      for (let i = 5; i >= 0; i--) {
        let r = 80 + i * (variant === 'rings' ? 56 : 65) + beat * (variant === 'flare' ? 100 : 90), alpha = Math.max(.08, .7 - i * .11);
        ctx.globalAlpha = alpha; ctx.lineWidth = variant === 'rings' ? 4 + i * .7 : 3 + i; ctx.beginPath(); ctx.arc(640, 360, r, 0, Math.PI * 2); ctx.stroke();
      }
    }
    ctx.globalAlpha = variant === 'bloom' ? .26 : variant === 'flare' ? .22 : .15;
    ctx.beginPath(); ctx.arc(640, 360, 110 + beat * 80, 0, Math.PI * 2); ctx.fillStyle = color; ctx.fill();
    ctx.globalAlpha = 1;
  }
  else if (style === 'particles') {
    const bounds = { left: 90, right: 1190, top: 90, bottom: 630 };
    ctx.save();
    if (variant === 'nebula') {
      ctx.globalAlpha = .08 + kick * .06;
      const neb = ctx.createRadialGradient(640, 360, 60, 640, 360, 360);
      neb.addColorStop(0, color); neb.addColorStop(1, 'transparent');
      ctx.fillStyle = neb; ctx.fillRect(280, 40, 720, 640);
    }
    for (const p of particles) {
      const energy = Math.min(1.8, beat * .7 + kick * 1.1);
      if (variant === 'orbit') {
        p.angle += .0035 * p.speed + kick * .01;
        p.x = 640 + Math.cos(p.angle + p.phase) * p.orbit;
        p.y = 360 + Math.sin(p.angle * 1.05 + p.phase) * (p.orbit * .62);
      } else {
        const driftX = variant === 'drift' ? 0.06 * p.driftDir : 0;
        const centerPull = variant === 'cluster' ? .0025 : variant === 'spark' ? .0012 : .0009;
        const wobbleX = Math.sin(t * (.7 + p.speed) + p.phase) * (variant === 'nebula' ? .24 : .16);
        const wobbleY = Math.cos(t * (.8 + p.speed) + p.phase) * (variant === 'nebula' ? .22 : .14);
        p.vx += wobbleX + driftX + (p.baseX - p.x) * centerPull + (640 - p.x) * (variant === 'cluster' ? .00035 : 0) + Math.sin(t * 3 + p.phase) * kick * .03;
        p.vy += wobbleY + (p.baseY - p.y) * centerPull + (360 - p.y) * (variant === 'cluster' ? .00035 : 0) - Math.cos(t * 3 + p.phase) * kick * .018;
        p.vx *= variant === 'spark' ? .92 : .96; p.vy *= variant === 'spark' ? .92 : .96;
        p.x += p.vx * (1 + energy * .35); p.y += p.vy * (1 + energy * .35);
        if (p.x < bounds.left) { p.x = bounds.left; p.vx *= -0.85; }
        if (p.x > bounds.right) { p.x = bounds.right; p.vx *= -0.85; }
        if (p.y < bounds.top) { p.y = bounds.top; p.vy *= -0.85; }
        if (p.y > bounds.bottom) { p.y = bounds.bottom; p.vy *= -0.85; }
      }
      const tw = (Math.sin(t * (1.5 + p.twinkle) + p.phase) + 1) * .5;
      const scale = 1 + kick * (1.2 + p.sizePulse) + treble * .5 + tw * (variant === 'spark' ? .7 : .35);
      const sz = p.r * scale;
      ctx.globalAlpha = variant === 'nebula' ? .12 + p.glow * .18 + tw * .08 : .18 + p.glow * .28 + tw * .1;
      ctx.shadowBlur = sz * (variant === 'spark' ? 7 : 5) + kick * 16;
      ctx.beginPath(); ctx.arc(p.x, p.y, sz, 0, Math.PI * 2); ctx.fill();
    }
    ctx.shadowBlur = 0;
    ctx.globalAlpha = .18 + kick * .2; ctx.lineWidth = 1.5;
    if (variant === 'float' || variant === 'cluster' || variant === 'nebula') { ctx.beginPath(); ctx.arc(640, 360, 154 + kick * 35, 0, Math.PI * 2); ctx.stroke(); }
    ctx.globalAlpha = 1; ctx.restore();
  }
  else if (style === 'tunnel') {
    if (variant === 'grid') drawGrid(.05);
    for (let i = 0; i < 16; i++) {
      let z = ((i / 16 + t * .17) % 1), s = 36 + z * z * 1000;
      ctx.save(); ctx.translate(640, 360);
      let rot = t * .09 + z * .3;
      if (variant === 'diamond') rot += Math.PI / 4;
      if (variant === 'vortex') rot += z * 1.4 + t * .4;
      ctx.rotate(rot); ctx.globalAlpha = .07 + z * .7; ctx.lineWidth = 2 + z * 4;
      if (variant === 'hex') drawPoly(0, 0, s * .46 * (1 + beat * .15), 6, rot * .2), ctx.stroke();
      else if (variant === 'portal') { ctx.beginPath(); ctx.arc(0, 0, s * .35 * (1 + beat * .12), 0, Math.PI * 2); ctx.stroke(); }
      else ctx.strokeRect(-s / 2 * (1 + beat * .15), -s / 2 * (1 + beat * .15), s * (1 + beat * .15), s * (1 + beat * .15));
      ctx.restore();
    }
  }
  ctx.restore();

  if (conf.flash > 0) {
    ctx.save(); ctx.globalCompositeOperation = 'screen'; ctx.fillStyle = conf.color; ctx.globalAlpha = Math.min(.65, kick * conf.flash * .58); ctx.fillRect(0, 0, w, h); ctx.restore();
  }
  const vignette = ctx.createRadialGradient(w / 2, h / 2, 100, w / 2, h / 2, 740); vignette.addColorStop(0, 'rgba(0,0,0,0)'); vignette.addColorStop(1, 'rgba(0,0,0,.75)'); ctx.fillStyle = vignette; ctx.fillRect(0, 0, w, h);
  if (logo) {
    const maxW = w * conf.logoSize / 100, maxH = h * .48, scale = Math.min(maxW / logo.width, maxH / logo.height); const lw = logo.width * scale, lh = logo.height * scale;
    ctx.save(); const f = conf.logoFx; let sc = 1, rot = 0, dx = 0, dy = 0;
    if (conf.logoMode === 'bass') sc += Math.min(.4, kick * .22 * f); if (conf.logoMode === 'breathe') sc += Math.sin(t * 1.5) * .08 * f; if (conf.logoMode === 'shake') { dx = Math.sin(t * 47) * kick * 13 * f; dy = Math.cos(t * 37) * kick * 10 * f; } if (conf.logoMode === 'rotate') rot = Math.sin(t * .8) * f * .13;
    ctx.translate(w / 2 + dx, h / 2 + dy); ctx.rotate(rot); ctx.scale(sc, sc); ctx.shadowColor = conf.color; ctx.shadowBlur = 8 + conf.logoGlow * (20 + kick * 45); ctx.drawImage(logo, -lw / 2, -lh / 2, lw, lh); ctx.restore();
  } else {
    ctx.textAlign = 'center'; ctx.fillStyle = '#ffffff'; ctx.font = 'bold 65px Segoe UI, Arial'; ctx.shadowColor = color; ctx.shadowBlur = 22; ctx.fillText('HDRG', 640, 370); ctx.shadowBlur = 0; ctx.fillStyle = '#b9b8d0'; ctx.font = '500 16px Segoe UI, Arial'; ctx.fillText('MUSIC VISUALIZER', 640, 410);
  }
  ctx.restore(); drawPlayerOverlay(ctx); render.last = now;
}
requestAnimationFrame(render);
$('recordBtn').addEventListener('click', async () => {
  if (isRecording) { recorder.stop(); return }
  if (!tracks.length) { notify('Import audio before recording'); return }
  if (!window.MediaRecorder) { notify('MediaRecorder not available'); return }
  setupAudio(); await ac.resume();
  const stream = canvas.captureStream(30); for (const track of dest.stream.getAudioTracks()) stream.addTrack(track);
  const mime = ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm'].find(x => MediaRecorder.isTypeSupported(x));
  if (!mime) { notify('WebM recording not supported'); return }
  try {
    recorder = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 8000000 }); chunks = [];
    recorder.ondataavailable = e => { if (e.data.size) chunks.push(e.data) };
    recorder.onstop = async () => {
      isRecording = false; $('recordBtn').classList.remove('recording'); $('recordBtn').textContent = '●  Record Video';
      const blob = new Blob(chunks, { type: mime }); const bytes = new Uint8Array(await blob.arrayBuffer());
      if (window.hdrgDesktop) { const result = await window.hdrgDesktop.saveVideo(bytes, 'HDRG-Visualizer.webm'); notify(result ? 'Saved: ' + result : 'Save cancelled'); }
      else { const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'HDRG-Visualizer.webm'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 15000); }
      stream.getTracks().filter(t => t.kind === 'video').forEach(t => t.stop());
    };
    recorder.start(1000); isRecording = true; $('recordBtn').classList.add('recording'); $('recordBtn').textContent = '■  Stop Recording'; cancelFade(); audio.currentTime = 0; await audio.play(); notify('Recording started. Stop to export WebM');
  } catch (e) { notify('Recording error: ' + e.message) }
});
