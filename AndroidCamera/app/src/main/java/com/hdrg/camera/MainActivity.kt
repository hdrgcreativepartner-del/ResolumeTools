package com.hdrg.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
 private lateinit var status:TextView
 private lateinit var preview:TextureView
 private lateinit var interval:Spinner
 private lateinit var quality:Spinner
 private lateinit var audio:CheckBox
 private lateinit var pause:Button
 private var previewCamera:CameraDevice?=null
 private var previewSession:CameraCaptureSession?=null
 private val cameraThread=HandlerThread("HDRGPreview").apply{start()}
 private val cameraHandler=Handler(cameraThread.looper)
 private var recording=false
 private var paused=false
 private val modeLabels=listOf("Normal video","Timelapse · 1 sec","Timelapse · 2 sec","Timelapse · 5 sec","Timelapse · 10 sec","Timelapse · 30 sec")
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(8,16,27)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,26,28,20);setBackgroundColor(Color.rgb(8,16,27))}
  fun label(value:String,size:Float)=TextView(this).apply{text=value;textSize=size;setTextColor(Color.WHITE);setPadding(0,9,0,9)}
  root.addView(label("HDRG CAMERA",27f));root.addView(label("VIDEO  /  TIMELAPSE  /  BACKGROUND",12f))
  preview=TextureView(this)
  root.addView(preview,LinearLayout.LayoutParams(-1,0,1.0f))
  interval=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,modeLabels)}
  quality=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("720p HD","1080p Full HD","2160p 4K (if supported)"))}
  root.addView(label("MODE",12f));root.addView(interval)
  root.addView(label("RESOLUTION",12f));root.addView(quality)
  audio=CheckBox(this).apply{text="Record microphone audio (normal video)";setTextColor(Color.WHITE);isChecked=true}
  root.addView(audio)
  status=label("Ready • Video will appear in Movies/HDRG Camera (Android 10+)",13f);root.addView(status)
  val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val start=Button(this).apply{text="● RECORD";setOnClickListener{startRecord()}}
  pause=Button(this).apply{text="PAUSE";isEnabled=false;setOnClickListener{togglePause()}}
  val stop=Button(this).apply{text="■ STOP";setOnClickListener{stopRecord()}}
  for(b in listOf(start,pause,stop))buttons.addView(b,LinearLayout.LayoutParams(0,-2,1f))
  root.addView(buttons)
  root.addView(label("When recording continues in the background, Android displays an ongoing notification with a Stop action.",11f))
  setContentView(root)
  preview.surfaceTextureListener=object:TextureView.SurfaceTextureListener{
   override fun onSurfaceTextureAvailable(surface:SurfaceTexture,w:Int,h:Int){startPreview()}
   override fun onSurfaceTextureSizeChanged(surface:SurfaceTexture,w:Int,h:Int){}
   override fun onSurfaceTextureDestroyed(surface:SurfaceTexture):Boolean{closePreview();return true}
   override fun onSurfaceTextureUpdated(surface:SurfaceTexture){}
  }
  requestNeededPermissions()
 }
 private fun requestNeededPermissions(){
   val permissions=mutableListOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
   if(Build.VERSION.SDK_INT>=33)permissions.add(Manifest.permission.POST_NOTIFICATIONS)
   val missing=permissions.filter{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED}
   if(missing.isNotEmpty())ActivityCompat.requestPermissions(this,missing.toTypedArray(),109)
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(requestCode,permissions,results);if(preview.isAvailable)startPreview()}
 private fun startPreview(){
  if(recording||!preview.isAvailable||previewCamera!=null)return
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return
  val manager=getSystemService(CAMERA_SERVICE) as CameraManager
  try{
   val id=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK} ?:return
   manager.openCamera(id,object:CameraDevice.StateCallback(){
    override fun onOpened(device:CameraDevice){
      previewCamera=device
      try{
       val texture=preview.surfaceTexture ?:return
       texture.setDefaultBufferSize(1280,720)
       val surface=Surface(texture)
       val req=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply{addTarget(surface)}.build()
       @Suppress("DEPRECATION")
       device.createCaptureSession(listOf(surface),object:CameraCaptureSession.StateCallback(){
        override fun onConfigured(session:CameraCaptureSession){
         previewSession=session
         try{session.setRepeatingRequest(req,null,cameraHandler)}catch(_:Exception){}
        }
        override fun onConfigureFailed(session:CameraCaptureSession){closePreview()}
       },cameraHandler)
      }catch(_:Exception){closePreview()}
    }
    override fun onDisconnected(device:CameraDevice){closePreview()}
    override fun onError(device:CameraDevice,error:Int){closePreview()}
   },cameraHandler)
  }catch(_:Exception){status.text="Preview unavailable on this device"}
 }
 private fun closePreview(){
  try{previewSession?.close()}catch(_:Exception){}
  previewSession=null
  try{previewCamera?.close()}catch(_:Exception){}
  previewCamera=null
 }
 private fun startRecord(){
  if(recording)return
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestNeededPermissions();return}
  val includeAudio=audio.isChecked&&interval.selectedItemPosition==0
  if(includeAudio&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestNeededPermissions();status.text="Grant microphone permission first";return}
  val seconds=when(interval.selectedItemPosition){1->1;2->2;3->5;4->10;5->30;else->0}
  val sizes=intArrayOf(720,1080,2160)
  closePreview()
  val intent=Intent(this,CaptureService::class.java).apply{
    action=CaptureService.START;putExtra("interval",seconds);putExtra("height",sizes[quality.selectedItemPosition]);putExtra("audio",includeAudio)
  }
  try{
    ContextCompat.startForegroundService(this,intent)
    recording=true;paused=false;pause.isEnabled=true;status.text="Recording started • Notification remains visible"
  }catch(e:Exception){recording=false;status.text="Cannot start: ${e.message}";startPreview()}
 }
 private fun togglePause(){
  if(!recording)return
  val action=if(paused)CaptureService.RESUME else CaptureService.PAUSE
  startService(Intent(this,CaptureService::class.java).apply{this.action=action})
  paused=!paused
  pause.text=if(paused)"RESUME" else "PAUSE"
  status.text=if(paused)"Pause requested" else "Resume requested"
 }
 private fun stopRecord(){
  startService(Intent(this,CaptureService::class.java).apply{action=CaptureService.STOP})
  recording=false;paused=false;pause.isEnabled=false;pause.text="PAUSE"
  status.text="Stopping • Saving recording to Movies/HDRG Camera"
  Handler(Looper.getMainLooper()).postDelayed({startPreview()},1300)
 }
 override fun onResume(){super.onResume();if(preview.isAvailable&&!recording)startPreview()}
 override fun onPause(){super.onPause();closePreview()}
 override fun onDestroy(){closePreview();cameraThread.quitSafely();super.onDestroy()}
}
