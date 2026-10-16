"""HDRG Audio Bridge MSI edition: batch audio and video to WAV / FLAC."""
import os, sys, queue, threading, subprocess, shutil
from pathlib import Path
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

EXTS={".mp3",".wav",".flac",".m4a",".aac",".ogg",".opus",".wma",".aif",".aiff",".mp4",".mov",".mkv",".avi",".webm",".wmv",".mpeg",".mpg",".m4v",".ts",".flv",".3gp"}
BG="#0b1420"; PANEL="#1b2a39"; FG="#edf5fa"; CYAN="#48c4d7"

def ffmpeg_binary():
    roots=[Path(sys.executable).resolve().parent,Path(__file__).resolve().parent]
    for root in roots:
        file=root/"ffmpeg.exe"
        if file.exists(): return str(file)
    return shutil.which("ffmpeg")

class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("HDRG Audio Bridge | WAV & FLAC Converter")
        self.geometry("930x670");self.minsize(780,580);self.configure(bg=BG)
        self.files=[];self.events=queue.Queue();self.thread=None;self.proc=None
        self.cancel=threading.Event()
        self.format=tk.StringVar(value="FLAC")
        self.depth=tk.StringVar(value="16-bit")
        self.rate=tk.StringVar(value="Original")
        self.channel=tk.StringVar(value="Original")
        self.folder=tk.StringVar(value="")
        self.status=tk.StringVar(value="Siap. Tambahkan file audio atau video.")
        self.overwrite=tk.BooleanVar(value=False)
        self.build_ui()
        self.after(100,self.poll)
        self.protocol("WM_DELETE_WINDOW",self.close)

    def button(self,parent,label,callback):
        b=tk.Button(parent,text=label,command=callback,bg=PANEL,fg=FG,activebackground="#334a5d",activeforeground=FG,relief="flat",padx=12,pady=9,cursor="hand2")
        b.pack(side="left",padx=(0,9));return b

    def build_ui(self):
        tk.Label(self,text="HDRG  AUDIO BRIDGE",fg=CYAN,bg=BG,font=("Segoe UI Semibold",25)).pack(anchor="w",padx=26,pady=(22,0))
        tk.Label(self,text="AUDIO / VIDEO  →  WAV / FLAC  •  WINDOWS EDITION",fg="#acbfcd",bg=BG,font=("Segoe UI",10)).pack(anchor="w",padx=27,pady=(0,18))
        toolbar=tk.Frame(self,bg=BG);toolbar.pack(fill="x",padx=26)
        self.button(toolbar,"+ Tambah File",self.add_files)
        self.button(toolbar,"+ Tambah Folder",self.add_folder)
        self.button(toolbar,"Hapus Pilihan",self.remove)
        self.button(toolbar,"Bersihkan",self.clear)
        style=ttk.Style(self);style.theme_use("clam")
        style.configure("Treeview",background=PANEL,fieldbackground=PANEL,foreground=FG,rowheight=29)
        style.configure("Treeview.Heading",background="#334659",foreground=FG)
        style.configure("TProgressbar",background=CYAN,troughcolor=PANEL)
        wrap=tk.Frame(self,bg=PANEL);wrap.pack(fill="both",expand=True,padx=26,pady=14)
        self.tree=ttk.Treeview(wrap,columns=("file","status"),show="headings",selectmode="extended")
        self.tree.heading("file",text="FILE AUDIO / VIDEO");self.tree.heading("status",text="STATUS")
        self.tree.column("file",width=650);self.tree.column("status",width=180)
        self.tree.pack(fill="both",expand=True,padx=7,pady=7)
        line=tk.Frame(self,bg=BG);line.pack(fill="x",padx=26)
        for label,var,choices,width in [
            ("Output",self.format,["FLAC","WAV"],7),
            ("Bit depth",self.depth,["16-bit","24-bit"],8),
            ("Sample rate",self.rate,["Original","44.1 kHz","48 kHz","96 kHz"],11),
            ("Channels",self.channel,["Original","Mono","Stereo"],9)
        ]:
            tk.Label(line,text=label,bg=BG,fg=FG).pack(side="left")
            ttk.Combobox(line,textvariable=var,values=choices,width=width,state="readonly").pack(side="left",padx=(7,17))
        dest=tk.Frame(self,bg=BG);dest.pack(fill="x",padx=26,pady=(12,5))
        self.button(dest,"Pilih Folder Output",self.choose_output)
        tk.Label(dest,textvariable=self.folder,bg=BG,fg="#a5bbc8",anchor="w").pack(side="left",fill="x",expand=True)
        tk.Checkbutton(self,text="Timpa file output yang sudah ada",variable=self.overwrite,bg=BG,fg=FG,selectcolor=PANEL,activebackground=BG,activeforeground=FG).pack(anchor="w",padx=26)
        tk.Label(self,textvariable=self.status,bg=BG,fg="#b9ccd8",anchor="w").pack(fill="x",padx=26,pady=(10,0))
        self.progress=ttk.Progressbar(self,maximum=100);self.progress.pack(fill="x",padx=26,pady=(8,14))
        actions=tk.Frame(self,bg=BG);actions.pack(fill="x",padx=26,pady=(0,17))
        self.button(actions,"▶ MULAI KONVERSI",self.start)
        self.button(actions,"■ BATALKAN",self.stop)
        self.button(actions,"Buka Folder Output",self.open_output)

    def busy(self): return self.thread is not None and self.thread.is_alive()

    def add_paths(self,paths):
        known={str(p).casefold() for p in self.files}
        for raw in paths:
            p=Path(raw)
            if not p.is_file() or p.suffix.lower() not in EXTS or str(p).casefold() in known: continue
            self.files.append(p);known.add(str(p).casefold())
            self.tree.insert("","end",iid=str(p),values=(p.name,"Menunggu"))
        self.status.set(f"{len(self.files)} file dalam antrean")

    def add_files(self):
        paths=filedialog.askopenfilenames(title="Pilih file audio / video",filetypes=[("Audio dan video"," ".join("*"+ext for ext in sorted(EXTS))),("Semua File","*.*")])
        self.add_paths(paths)

    def add_folder(self):
        folder=filedialog.askdirectory(title="Pilih folder input")
        if folder:self.add_paths(p for p in Path(folder).rglob("*") if p.is_file())

    def remove(self):
        if self.busy():return
        ids=set(self.tree.selection());self.files=[p for p in self.files if str(p) not in ids]
        for id in ids:self.tree.delete(id)

    def clear(self):
        if self.busy():return
        self.files.clear()
        for id in self.tree.get_children():self.tree.delete(id)
        self.progress["value"]=0

    def choose_output(self):
        selected=filedialog.askdirectory(title="Folder hasil konversi")
        if selected:self.folder.set(selected)

    def open_output(self):
        folder=self.folder.get()
        if folder and Path(folder).is_dir():os.startfile(folder)
        else:messagebox.showinfo("Folder output","Pilih folder output terlebih dahulu.")

    def start(self):
        if self.busy():return
        if not self.files:
            messagebox.showwarning("HDRG Audio Bridge","Tambahkan file sebelum konversi.");return
        if not self.folder.get() or not Path(self.folder.get()).is_dir():
            messagebox.showwarning("Folder Output","Pilih folder output yang valid.");return
        ffmpeg=ffmpeg_binary()
        if not ffmpeg:
            messagebox.showerror("FFmpeg tidak ada","FFmpeg tidak ditemukan dalam instalasi.");return
        self.cancel.clear()
        settings=(list(self.files),self.folder.get(),self.format.get(),self.depth.get(),self.rate.get(),self.channel.get(),self.overwrite.get(),ffmpeg)
        self.thread=threading.Thread(target=self.convert,args=(settings,),daemon=True);self.thread.start()

    def convert(self,settings):
        files,folder,fmt,depth,rate,channel,overwrite,ffmpeg=settings
        ok=failed=skipped=0
        for idx,src in enumerate(files):
            if self.cancel.is_set():break
            dest=Path(folder)/(src.stem+("." +fmt.lower()))
            # Avoid overwriting the source, including if the source is in the output folder.
            if src.resolve()==dest.resolve():
                self.events.put(("row",str(src),"Sumber = Output"));skipped+=1;continue
            if dest.exists() and not overwrite:
                self.events.put(("row",str(src),"Sudah ada"));skipped+=1;continue
            tmp=dest.with_name(dest.stem+".hdrg-partial"+dest.suffix)
            cmd=[ffmpeg,"-hide_banner","-loglevel","error","-nostdin","-y","-i",str(src),"-map","0:a:0","-vn","-sn","-dn"]
            if fmt=="WAV":
                cmd+=["-c:a","pcm_s16le" if depth=="16-bit" else "pcm_s24le"]
            else:
                cmd+=["-c:a","flac","-sample_fmt","s16" if depth=="16-bit" else "s32"]
                if depth=="24-bit":cmd+=["-bits_per_raw_sample","24"]
            if rate!="Original":cmd+=["-ar",str({"44.1 kHz":44100,"48 kHz":48000,"96 kHz":96000}[rate])]
            if channel!="Original":cmd+=["-ac","1" if channel=="Mono" else "2"]
            cmd.append(str(tmp))
            self.events.put(("row",str(src),"Memproses..."))
            try:
                self.proc=subprocess.Popen(cmd,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,text=True,creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))
                _,err=self.proc.communicate()
                if self.cancel.is_set():break
                if self.proc.returncode!=0 or not tmp.exists() or not tmp.stat().st_size:
                    raise RuntimeError((err or "Tidak ada audio stream")[-350:])
                os.replace(tmp,dest);ok+=1
                self.events.put(("row",str(src),"Selesai"))
            except Exception as ex:
                failed+=1;self.events.put(("row",str(src),"Gagal"))
                self.events.put(("status",src.name+": "+str(ex)))
            finally:
                self.proc=None
                try:tmp.unlink(missing_ok=True)
                except OSError:pass
            self.events.put(("progress",round(100*(idx+1)/len(files),1)))
        self.events.put(("done",ok,failed,skipped))

    def poll(self):
        try:
            while True:
                event=self.events.get_nowait()
                if event[0]=="row" and self.tree.exists(event[1]):self.tree.set(event[1],"status",event[2])
                elif event[0]=="status":self.status.set(event[1])
                elif event[0]=="progress":self.progress["value"]=event[1]
                elif event[0]=="done":self.status.set(f"Selesai: {event[1]} berhasil, {event[2]} gagal, {event[3]} dilewati.")
        except queue.Empty:pass
        self.after(100,self.poll)

    def stop(self):
        self.cancel.set()
        if self.proc and self.proc.poll() is None:
            try:self.proc.terminate()
            except OSError:pass
        self.status.set("Pembatalan diminta")

    def close(self):
        if self.busy():self.stop()
        self.destroy()

if __name__=="__main__":App().mainloop()
