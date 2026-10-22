#target illustrator
/* HDRG Vector Converter v0.1 — desktop toolkit, not a CDR parser.
   Export outlined vector PDF from CorelDRAW first. No rasterize/flatten commands.
   Each selected PDF page becomes a separate AI. Test complex effects manually. */
(function () {
    function parsePages(value) {
        if (!value || !/^\s*\d+(\s*-\s*\d+)?(\s*,\s*\d+(\s*-\s*\d+)?)*\s*$/.test(value)) throw new Error('Gunakan nomor seperti 1-3,5.');
        var result = [], seen = {}, chunks = value.split(',');
        for (var i = 0; i < chunks.length; i++) {
            var range = chunks[i].split('-'), start = Number(range[0]), end = range.length > 1 ? Number(range[1]) : start;
            if (start < 1 || end < start || end > 10000 || end - start > 499) throw new Error('Rentang tidak valid; maksimum 500 halaman per PDF.');
            for (var p = start; p <= end; p++) if (!seen[p]) { seen[p] = true; result.push(p); }
        }
        if (result.length > 500) throw new Error('Maksimum 500 halaman per PDF.');
        return result;
    }
    var files = File.openDialog('Pilih PDF vektor hasil ekspor CorelDRAW', '*.pdf', true);
    if (!files) return;
    if (!(files instanceof Array)) files = [files];
    var output = Folder.selectDialog('Pilih folder hasil AI');
    if (!output) return;
    if (!confirm('Pastikan font sumber benar dan PDF sudah diekspor sebagai curve.\nMasukkan hanya nomor halaman yang benar-benar ada.\nHasil: satu AI per halaman; file lama tidak ditimpa.\nLanjutkan?')) return;
    var originalPage = app.preferences.PDFFileOptions.pageToOpen;
    var report = ['HDRG Vector Converter v0.1', 'Periksa warna, ukuran, efek dan path secara manual.', 'Jumlah path/bitmap bukan bukti kesetiaan terhadap CDR.', ''], saved = 0;
    try {
        for (var f = 0; f < files.length; f++) {
            if (!/\.pdf$/i.test(files[f].name)) { report.push('SKIP non-PDF: ' + files[f].name); continue; }
            var pageInput = prompt('Nomor halaman PDF untuk ' + files[f].name + '\nContoh: 1-3,5. Periksa jumlah halaman terlebih dahulu.\nCancel melewati file ini.', '1');
            if (pageInput === null) { report.push('SKIP: ' + files[f].name); continue; }
            var pages;
            try { pages = parsePages(pageInput); } catch (e) { alert(e.message); f--; continue; }
            var stem = files[f].name.replace(/\.pdf$/i, '');
            for (var k = 0; k < pages.length; k++) {
                var doc = null, dest = new File(output.fsName + '/' + stem + '-p' + pages[k] + '.ai');
                if (dest.exists) { report.push('SKIP sudah ada: ' + dest.name); continue; }
                try {
                    app.preferences.PDFFileOptions.pageToOpen = pages[k];
                    doc = app.open(files[f]);
                    // Abort if outlining fails: do not save a partially outlined result.
                    while (doc.textFrames.length > 0) {
                        var before = doc.textFrames.length;
                        doc.textFrames[before - 1].createOutline();
                        if (doc.textFrames.length >= before) throw new Error('Teks tidak dapat di-outline. Periksa locked/hidden text di PDF.');
                    }
                    var paths = doc.pathItems.length, bitmaps = doc.rasterItems.length;
                    var options = new IllustratorSaveOptions();
                    options.pdfCompatible = true;
                    options.compressed = true;
                    doc.saveAs(dest, options);
                    saved++;
                    report.push('SAVED: ' + dest.name + ' | paths=' + paths + ' | rasterItems=' + bitmaps + ' | textFrames=' + doc.textFrames.length + (bitmaps ? ' | REVIEW bitmap/efek' : ''));
                } catch (err) {
                    report.push('ERROR: ' + files[f].name + ' page ' + pages[k] + ' | ' + err.message);
                } finally {
                    if (doc) { try { doc.close(SaveOptions.DONOTSAVECHANGES); } catch (closeError) { report.push('WARNING: Tutup dokumen hasil secara manual: ' + closeError.message); } }
                }
            }
        }
    } finally { app.preferences.PDFFileOptions.pageToOpen = originalPage; }
    var log = new File(output.fsName + '/HDRG-report-' + new Date().getTime() + '.txt');
    log.encoding = 'UTF-8';
    var logged = log.open('w');
    if (logged) { logged = log.write(report.join('\r\n')); log.close(); }
    alert('Selesai: ' + saved + ' AI tersimpan.\n' + (logged ? 'Baca laporan: ' + log.fsName : 'Laporan tidak dapat ditulis.\n' + report.join('\n')) + '\nUji hasil secara visual sebelum digunakan untuk produksi.');
}());
