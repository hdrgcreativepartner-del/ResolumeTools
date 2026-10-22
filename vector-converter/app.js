'use strict';
const checks = [...document.querySelectorAll('.check')];
function update() {
  const total = checks.filter(input => input.checked).length;
  document.querySelector('#progress').textContent = `${total} dari 3 persiapan selesai`;
  document.querySelector('#ready').textContent = total === 3
    ? 'Persiapan dikonfirmasi. Lanjutkan ekspor PDF di CorelDRAW. Ini bukan hasil pemeriksaan file otomatis.'
    : 'Lengkapi persiapan, lalu ikuti alur ekspor di bawah.';
}
checks.forEach(input => input.addEventListener('change', update));
document.querySelector('#reset').addEventListener('click', () => { checks.forEach(input => { input.checked = false; }); update(); });
update();
