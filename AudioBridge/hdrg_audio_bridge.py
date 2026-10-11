"""HDRG Audio Bridge 1.3 — source-folder FLAC converter."""
from __future__ import annotations
import os, sys, time, queue, shutil, subprocess, threading
from pathlib import Path
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
try:
    from tkinterdnd2 import TkinterDnD, DND_FILES
    Base = TkinterDnD.Tk
except ImportError:
    Base = tk.Tk

AUDIO={'.mp3','.wav','.m4a','.aac','.ogg','.opus','.wma','.aif','.aiff'}
VIDEO={'.mp4','.mov','.mkv','.avi','.webm','.wmv','.mpeg','.mpg','.m4v','.ts','.mts','.flv','.3gp'}
SUPPORTED=AUDIO|VIDEO
BG='#111823'; PANEL='#1c2938'; TEXT='#eef5fa'; CYAN='#12bbcf'
def find_binary(name):
    suffix='.exe' if os.name=='nt' else ''
    paths=[]
    if getattr(sys,'frozen',False):
        paths += [Path(sys.executable).parent,Path(getattr(sys,'_MEIPASS',sys.executable)).joinpath('vendor')]
    paths += [Path(__file__).resolve().parent/'vendor',Path(__file__).resolve().parent]
    for folder in paths:
        p=folder/(name+suffix)
        if p.is_file(): return str(p)
    return shutil.which(name)

class App(Base):
    def __init__(self):
        super().__init__()
        self.title('HDRG Audio Bridge 1.3 | ResolumeTools')
        self.geometry('950x680'); self.minsize(770,530); self.configure(bg=BG)
        self.files=[]; self.events=queue.Queue(); self.worker=None; self.process=None
        self.cancel=threading.Event()
        self.watch=tk.BooleanVar(value=False); self.recursive=tk.BooleanVar(value=False)
        self.watch_path=tk.StringVar(value='Belum memilih folder monitor')
        self.depth=tk.StringVar(value='16-bit'); self.rate=tk.StringVar(value='Original')
        self.output_mode=tk.StringVar(value='source'); self.output_dir=tk.StringVar(value=''); self.overwrite=tk.BooleanVar(value=False); self.status=tk.StringVar(value='Siap • FLAC selalu disimpan bersama file sumber')
        self.seen={}
        self.create_ui()
        if hasattr(self,'drop_target_register'):
            for widget in (self,self.tree):
                widget.drop_target_register(DND_FILES)
                widget.dnd_bind('<<Drop>>',self.on_drop)
        self.after(120,self.poll); self.after(1800,self.watch_tick)
        self.protocol('WM_DELETE_WINDOW',self.close)

    def button(self,parent,title,fn):
        b=tk.Button(parent,text=title,command=fn,bg=PANEL,fg=TEXT,relief='flat',padx=13,pady=9,cursor='hand2')
        b.pack(side='left',padx=(0,8)); return b

    def create_ui(self):
        tk.Label(self,text='HDRG  AUDIO BRIDGE',font=('Segoe UI Semibold',23),fg=CYAN,bg=BG).pack(anchor='w',padx=26,pady=(19,3))
        tk.Label(self,text='ResolumeTools  •  Audio / Video → FLAC  •  Output otomatis di folder sumber',fg=TEXT,bg=BG,font=('Segoe UI',10)).pack(anchor='w',padx=28,pady=(0,14))
        top=tk.Frame(self,bg=BG); top.pack(fill='x',padx=26)
        self.button(top,'+ Tambah File',self.add_files)
        self.button(top,'+ Tambah Folder',self.add_folder)
        self.button(top,'Hapus Pilihan',self.remove_selected)
        self.button(top,'Bersihkan',self.clear)
        style=ttk.Style(self); style.theme_use('clam')
        style.configure('Treeview',background=PANEL,foreground=TEXT,fieldbackground=PANEL,rowheight=30)
        style.configure('Treeview.Heading',background='#324357',foreground=TEXT)
        style.configure('TProgressbar',background=CYAN,troughcolor=PANEL)
        pane=tk.Frame(self,bg=PANEL); pane.pack(fill='both',expand=True,padx=26,pady=(12,6))
        self.tree=ttk.Treeview(pane,columns=('file','type','state'),show='headings',selectmode='extended')
        for name,title,width in [('file','FILE',510),('type','TIPE',100),('state','STATUS',200)]:
            self.tree.heading(name,text=title);self.tree.column(name,width=width,anchor='w')
        self.tree.pack(fill='both',expand=True,padx=7,pady=7)
        prefs=tk.Frame(self,bg=BG);prefs.pack(fill='x',padx=26,pady=10)
        tk.Label(prefs,text='Sample rate',bg=BG,fg=TEXT).pack(side='left')
        ttk.Combobox(prefs,textvariable=self.rate,values=['Original','44.1 kHz','48 kHz'],width=11,state='readonly').pack(side='left',padx=(8,20))
        tk.Label(prefs,text='Bit depth',bg=BG,fg=TEXT).pack(side='left')
        ttk.Combobox(prefs,textvariable=self.depth,values=['16-bit','24-bit'],width=9,state='readonly').pack(side='left',padx=(8,20))
        tk.Checkbutton(prefs,text='Timpa FLAC yang sudah ada',variable=self.overwrite,bg=BG,fg=TEXT,selectcolor=PANEL,activebackground=BG).pack(side='left')
        output=tk.Frame(self,bg=PANEL);output.pack(fill='x',padx=26,pady=3)
        tk.Radiobutton(output,text='Folder sumber',variable=self.output_mode,value='source',command=self.refresh_output,bg=PANEL,fg=TEXT,selectcolor=BG).pack(side='left',padx=7)
        tk.Radiobutton(output,text='Folder pilihan',variable=self.output_mode,value='custom',command=self.refresh_output,bg=PANEL,fg=TEXT,selectcolor=BG).pack(side='left')
        self.button(output,'Pilih Folder Output',self.choose_output)
        tk.Label(self,textvariable=self.output_dir,bg=BG,fg='#b9c8d9',anchor='w').pack(fill='x',padx=28)
        monitor=tk.Frame(self,bg=PANEL);monitor.pack(fill='x',padx=26,pady=3)
        tk.Checkbutton(monitor,text='Auto-Watch',variable=self.watch,bg=PANEL,fg=TEXT,selectcolor=BG,activebackground=PANEL).pack(side='left',padx=7)
        self.button(monitor,'Pilih Folder Monitor',self.choose_watch)
        tk.Checkbutton(monitor,text='Subfolder',variable=self.recursive,bg=PANEL,fg=TEXT,selectcolor=BG,activebackground=PANEL).pack(side='left')
        tk.Label(self,textvariable=self.watch_path,bg=BG,fg='#b9c8d9',anchor='w').pack(fill='x',padx=28,pady=(5,0))
        tk.Label(self,textvariable=self.status,bg=BG,fg=TEXT,anchor='w').pack(fill='x',padx=28,pady=(8,0))
        self.progress=ttk.Progressbar(self,maximum=100);self.progress.pack(fill='x',padx=28,pady=(8,11))
        bottom=tk.Frame(self,bg=BG);bottom.pack(fill='x',padx=26,pady=(0,16))
        self.button(bottom,'▶ CONVERT TO FLAC',self.start)
        self.button(bottom,'■ BATALKAN',self.stop)
        self.button(bottom,'Buka Lokasi File Terpilih',self.open_selected_folder)

    def on_drop(self,event):
        paths=[]
        for raw in self.tk.splitlist(event.data):
            p=Path(raw)
            if p.is_dir():
                try: paths.extend(x for x in p.rglob('*') if x.is_file() and x.suffix.lower() in SUPPORTED)
                except OSError: pass
            elif p.is_file(): paths.append(p)
        self.add_paths(paths)
        return 'copy'

    def add_paths(self,paths):
        existing={str(p).casefold() for p in self.files}
        for raw in paths:
            p=Path(raw)
            if not p.is_file() or p.suffix.lower() not in SUPPORTED or str(p).casefold() in existing: continue
            self.files.append(p);existing.add(str(p).casefold())
            self.tree.insert('', 'end',iid=str(p),values=(p.name,'VIDEO' if p.suffix.lower() in VIDEO else 'AUDIO','Menunggu'))
        self.status.set(f'{len(self.files)} file dalam antrean • Output mengikuti lokasi sumber')

    def add_files(self):
        paths=filedialog.askopenfilenames(filetypes=[('Audio / Video',' '.join('*'+x for x in sorted(SUPPORTED))),('All','*.*')])
        self.add_paths(paths)

    def add_folder(self):
        folder=filedialog.askdirectory(title='Pilih folder berisi audio / video')
        if folder:self.add_paths(p for p in Path(folder).rglob('*') if p.is_file())

    def remove_selected(self):
        if self.is_busy():return
        selected=set(self.tree.selection())
        self.files=[p for p in self.files if str(p) not in selected]
        for p in selected:self.tree.delete(p)

    def clear(self):
        if self.is_busy():return
        self.files.clear()
        for row in self.tree.get_children():self.tree.delete(row)
        self.progress['value']=0

    def choose_output(self):
        folder=filedialog.askdirectory(title='Pilih folder hasil FLAC')
        if folder:self.output_dir.set(folder);self.output_mode.set('custom');self.refresh_output()

    def refresh_output(self):
        self.status.set('Output: '+(self.output_dir.get() if self.output_mode.get()=='custom' else 'folder sumber'))

    def choose_watch(self):
        folder=filedialog.askdirectory(title='Pilih folder yang dipantau untuk MP3/WAV')
        if folder:
            self.watch_path.set(folder);self.watch.set(True);self.seen.clear()

    def watch_tick(self):
        try:
            if self.watch.get() and Path(self.watch_path.get()).is_dir():
                root=Path(self.watch_path.get())
                iterator=root.rglob('*') if self.recursive.get() else root.iterdir()
                ready=[];present=set()
                for p in iterator:
                    if not p.is_file() or p.suffix.lower() not in {'.mp3','.wav'}:continue
                    key=str(p);present.add(key)
                    try: sig=(p.stat().st_size,p.stat().st_mtime_ns)
                    except OSError:continue
                    old=self.seen.get(key)
                    count=(old[1]+1) if old and old[0]==sig else 0
                    self.seen[key]=(sig,count)
                    if count>=2 and not p.with_suffix('.flac').exists():ready.append(p)
                for k in set(self.seen)-present:self.seen.pop(k,None)
                if ready and not self.is_busy():
                    self.add_paths(ready);self.start(ready)
        except OSError as err:self.status.set(str(err))
        self.after(1800,self.watch_tick)

    def is_busy(self):return self.worker is not None and self.worker.is_alive()

    def start(self,only=None):
        if self.is_busy():return
        files=list(only) if isinstance(only,list) else list(self.files)
        if not files:
            messagebox.showinfo('HDRG Audio Bridge','Tambahkan file terlebih dahulu.');return
        ffmpeg=find_binary('ffmpeg')
        if not ffmpeg:
            messagebox.showerror('FFmpeg tidak ditemukan','FFmpeg belum ada. Gunakan EXE hasil GitHub Actions yang sudah menyertakan FFmpeg.');return
        if self.output_mode.get()=='custom' and not Path(self.output_dir.get()).is_dir():
            messagebox.showerror('Output folder','Pilih folder output yang valid terlebih dahulu.');return
        self.cancel.clear()
        opts=(ffmpeg,files,self.depth.get(),self.rate.get(),self.overwrite.get(),self.output_mode.get(),self.output_dir.get())
        self.worker=threading.Thread(target=self.convert,args=(opts,),daemon=True);self.worker.start()

    def convert(self,opts):
        ffmpeg,files,depth,rate,overwrite,mode,outdir=opts;ok=fail=skip=0
        for index,src in enumerate(files):
            if self.cancel.is_set():break
            dest=(Path(outdir)/ (src.stem+'.flac')) if mode=='custom' else src.with_suffix('.flac')
            if src.resolve()==dest.resolve():
                self.events.put(('row',str(src),'Sudah FLAC'));skip+=1;continue
            if dest.exists() and not overwrite:
                self.events.put(('row',str(src),'FLAC sudah ada'));skip+=1;continue
            # Atomic output: never leave partial .flac at its final path.
            temp=dest.with_name(dest.stem+'.hdrg-partial.flac')
            cmd=[ffmpeg,'-hide_banner','-loglevel','error','-nostdin','-y','-i',str(src),'-map','0:a:0','-vn','-sn','-dn','-c:a','flac','-sample_fmt','s16' if depth=='16-bit' else 's32']
            if depth=='24-bit':cmd+=['-bits_per_raw_sample','24']
            if rate!='Original':cmd+=['-ar','44100' if rate=='44.1 kHz' else '48000']
            cmd.append(str(temp))
            self.events.put(('row',str(src),'Mengonversi...'))
            try:
                self.process=subprocess.Popen(cmd,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,text=True,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
                _,err=self.process.communicate()
                if self.cancel.is_set():
                    self.events.put(('row',str(src),'Dibatalkan'));break
                if self.process.returncode!=0 or not temp.exists() or temp.stat().st_size==0:
                    raise RuntimeError((err or 'Tidak ada audio stream')[-300:])
                os.replace(temp,dest)
                ok+=1;self.events.put(('row',str(src),'Selesai'))
            except (OSError,RuntimeError) as exc:
                fail+=1;self.events.put(('row',str(src),'Gagal'));self.events.put(('status',f'{src.name}: {exc}'))
            finally:
                try:temp.unlink(missing_ok=True)
                except OSError:pass
            self.events.put(('progress',round(100*(index+1)/len(files),1)))
        self.process=None
        self.events.put(('done',ok,fail,skip))

    def stop(self):
        self.cancel.set()
        if self.process and self.process.poll() is None:
            try:self.process.terminate()
            except OSError:pass

    def open_selected_folder(self):
        selection=self.tree.selection()
        if not selection:
            messagebox.showinfo('Lokasi file','Pilih satu file di daftar terlebih dahulu.');return
        folder=(Path(self.output_dir.get()) if self.output_mode.get()=='custom' and self.output_dir.get() else Path(selection[0]).parent)
        if sys.platform=='win32':os.startfile(str(folder))
        elif sys.platform=='darwin':subprocess.Popen(['open',str(folder)])
        else:subprocess.Popen(['xdg-open',str(folder)])

    def poll(self):
        try:
            while True:
                item=self.events.get_nowait()
                if item[0]=='row' and self.tree.exists(item[1]):self.tree.set(item[1],'state',item[2])
                elif item[0]=='progress':self.progress['value']=item[1]
                elif item[0]=='status':self.status.set(item[1])
                elif item[0]=='done':self.status.set(f'Selesai: {item[1]} berhasil, {item[2]} gagal, {item[3]} dilewati. FLAC tersimpan di folder sumber.')
        except queue.Empty:pass
        self.after(120,self.poll)

    def close(self):
        self.stop();self.destroy()

if __name__=='__main__':App().mainloop()
