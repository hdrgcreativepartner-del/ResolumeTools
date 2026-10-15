/* Shared pure helpers, usable by renderer and Node tests. */
(function(root) {
  const Core = {
    parseLrc(text) {
      const offset = Number(text.match(/\[offset:\s*(-?\d+)\]/i)?.[1] || 0) / 1000;
      const lines = [];
      for (const line of text.split(/\r?\n/)) {
        const stamps = [...line.matchAll(/\[(\d{1,3}):([0-5]\d)(?:[.:](\d{1,3}))?\]/g)];
        const words = line.replace(/\[[^\]]*\]/g, '').trim();
        for (const m of stamps) lines.push({time: Math.max(0, +m[1] * 60 + +m[2] + Number('0.' + (m[3] || '0')) + offset), text: words});
      }
      return lines.sort((a,b) => a.time - b.time);
    },
    lyricAt(lines, seconds, offset = 0) {
      let lo = 0, hi = lines.length - 1, result = -1;
      while (lo <= hi) { const mid = (lo + hi) >> 1; if (lines[mid].time + offset <= seconds) {result = mid; lo = mid + 1;} else hi = mid - 1; }
      return result;
    },
    metadata(name) {
      const stem = name.replace(/\.[^.]+$/, '');
      const parts = stem.split(' - ');
      return parts.length > 1 ? {artist: parts.shift().trim(), title: parts.join(' - ').trim()} : {artist: '', title: stem};
    },
    nextIndex(index, count, mode, random = Math.random) {
      if (count < 1) return -1;
      if (mode === 'shuffle' && count > 1) { const n = Math.floor(random() * (count - 1)); return n >= index ? n + 1 : n; }
      return index < count - 1 ? index + 1 : mode === 'repeat' ? 0 : -1;
    },
    fadeDuration(duration, requested) { return Number.isFinite(duration) && duration > 0 ? Math.min(Math.max(1, requested), duration / 3) : 0; },
    fadeGains(progress) { const x = Math.min(1, Math.max(0, progress)); return [Math.cos(x * Math.PI / 2), Math.sin(x * Math.PI / 2)]; }
  };
  if (typeof module !== 'undefined') module.exports = Core; else root.PlayerCore = Core;
})(globalThis);
