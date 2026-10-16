"""Build Windows MSI with cx_Freeze, bundling FFmpeg from imageio-ffmpeg."""
import uuid
import imageio_ffmpeg
from cx_Freeze import setup, Executable
from pathlib import Path

binary=Path(imageio_ffmpeg.get_ffmpeg_exe())
assert binary.is_file(), "FFmpeg executable missing"
setup(
 name="HDRG Audio Bridge",
 version="2.0.0",
 description="HDRG Audio Bridge WAV and FLAC Converter",
 author="HDRG Creative Partner",
 options={
   "build_exe":{"include_files":[(str(binary),"ffmpeg.exe")],"packages":["tkinter"]},
   "bdist_msi":{
     "upgrade_code":"3e53ef92-e432-44a5-a516-aed9ef3f930d",
     "all_users":True,
     "initial_target_dir":r"[ProgramFiles64Folder]HDRG Creative Partner\\Audio Bridge",
     "summary_data":{"author":"HDRG Creative Partner","comments":"HDRG Audio Bridge WAV / FLAC"}
   }
 },
 executables=[Executable("AudioBridge/audio_bridge_msi.py",base="gui",target_name="HDRG-Audio-Bridge.exe",shortcut_name="HDRG Audio Bridge",shortcut_dir="ProgramMenuFolder")]
)
