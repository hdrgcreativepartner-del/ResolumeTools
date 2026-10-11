# ResolumeTools — HDRG Creative Partner

Kumpulan tool untuk Resolume Arena 7. Masing-masing project memiliki folder dan build sendiri.

## Projects
- **AudioBridge/** — MP3/WAV/audio lain ke FLAC; ekstrak audio dari MOV/MP4/video; drag & drop, batch, Auto-Watch.
- **FX-Pack/** — rencana pengembangan efek visual.
- **Slice-Tools/** — rencana pengembangan utility LED dan slice mapping.

## HDRG Audio Bridge 1.2
File FLAC **selalu** disimpan di lokasi yang sama dengan file sumber (contoh: `D:/Music/Opening.mp3` menjadi `D:/Music/Opening.flac`). Tidak ada opsi folder output. Pilihan folder Auto-Watch tetap tersedia karena itu **folder input**, bukan output. Sumber tidak dihapus.

**Bukan plugin internal Resolume**: drag MP3/WAV ke slot Resolume tidak otomatis diintersep. Gunakan HDRG Audio Bridge untuk konversi dan masukkan hasil FLAC ke Resolume.

### Windows EXE
Buka **Actions** → **Build HDRG Audio Bridge EXE** → **Run workflow**, tunggu sukses, unduh artifact `HDRG-Audio-Bridge-Windows`. Build Windows menggunakan PyInstaller; FFmpeg dan ffprobe disertakan. EXE perlu diuji pada Windows/Resolume yang digunakan.

### Menjalankan dari source
Instal Python dan `pip install tkinterdnd2`. Siapkan ffmpeg + ffprobe di PATH atau `AudioBridge/vendor/`. Jalankan `python AudioBridge/hdrg_audio_bridge.py`.

Perhatian: codec FLAC bersifat lossless untuk PCM yang sudah didekode, tetapi mengubah MP3 ke FLAC **tidak memulihkan** kualitas audio yang hilang dari kompresi MP3.
