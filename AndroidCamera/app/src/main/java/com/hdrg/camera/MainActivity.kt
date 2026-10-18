package com.hdrg.camera

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// UI and recording service share only the preview Surface within this application process.
object CameraPreviewBridge { @Volatile var surface:Surface?=null }

class MainActivity:Activity(){
 private lateinit var preview:TextureView
 private lateinit var shutter:TextView
 private lateinit var state:TextView
 private lateinit var timer:TextView
 private lateinit var pauseButton:TextView
 private lateinit var modeButton:TextView
 private lateinit var qualityButton:TextView
 private lateinit var lensButton:TextView
 private lateinit var gridButton:TextView
 private lateinit var stabilizeButton:TextView
 private lateinit var gridOverlay:GridOverlay
 private var stabilized=true
 private var previewSurface:Surface?=null
 private var idleCamera:CameraDevice?=null
 private var idleSession:CameraCaptureSession?=null
 private val thread=HandlerThread("CameraUI").apply{start()}
 private val handler=Handler(thread.looper)
 private val ui=Handler(Looper.getMainLooper())
 private var lens=CameraCharacteristics.LENS_FACING_BACK
 private var quality=1080
 private var interval=0
 private var audio=true
 private var active=false
 private var paused=false
 private var starting=false
 private var elapsedStart=0L
 private var pausedElapsed=0L
 private var zoomRatio=1f
 private val updater=object:Runnable{override fun run(){
  if(active&&!paused){val s=(SystemClock.elapsedRealtime()-elapsedStart)/1000;timer.text="%02d:%02d".format(s/60,s%60)}
  ui.postDelayed(this,1000)
 }}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
  val root=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
  preview=TextureView(this)
  root.addView(preview,FrameLayout.LayoutParams(-1,-1))
  gridOverlay=GridOverlay(this)
  root.addView(gridOverlay,FrameLayout.LayoutParams(-1,-1))
  // iOS-inspired immersive camera chrome. Keep the existing Camera2 preview and recording pipeline.
  val shade=View(this).apply{setBackgroundColor(Color.argb(75,0,0,0))}
  root.addView(shade,FrameLayout.LayoutParams(-1,dp(100),Gravity.TOP))
  fun action(label:String):TextView=TextView(this).apply{
   text=label;setTextColor(Color.WHITE);textSize=15f;gravity=Gravity.CENTER;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
   setPadding(dp(10),dp(10),dp(10),dp(10));minimumWidth=dp(48);minimumHeight=dp(48)
  }
  val top=LinearLayout(this).apply{
   gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(8))
  }
  val brand=action("HDRG / CAMERA").apply{textSize=13f;letterSpacing=.2f;setTypeface(typeface,1);gravity=Gravity.CENTER_VERTICAL or Gravity.LEFT}
  top.addView(brand,LinearLayout.LayoutParams(0,dp(48),1f))
  gridButton=action("▦").apply{contentDescription="Grid";setOnClickListener{
   gridOverlay.gridVisible=!gridOverlay.gridVisible
   setTextColor(if(gridOverlay.gridVisible)Color.rgb(255,207,73) else Color.WHITE)
  }}
  top.addView(gridButton)
  stabilizeButton=action("STB").apply{contentDescription="Stabilization";textSize=12f;setTextColor(Color.rgb(255,207,73));setOnClickListener{
   if(!active){stabilized=!stabilized;setTextColor(if(stabilized)Color.rgb(255,207,73) else Color.WHITE);closeIdle();openIdle()}
  }}
  top.addView(stabilizeButton)
  qualityButton=action("FHD").apply{contentDescription="Video quality";textSize=13f;setTypeface(typeface,1)}
  top.addView(qualityButton)
  root.addView(top,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
  state=action("READY").apply{textSize=11f;letterSpacing=.12f;setTextColor(Color.rgb(230,230,230))}
  val statusParams=FrameLayout.LayoutParams(-2,dp(38),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{topMargin=dp(75)}
  root.addView(state,statusParams)
  val footer=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL
   setPadding(dp(20),dp(8),dp(20),dp(26))
   background=GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(Color.argb(245,0,0,0),Color.argb(170,0,0,0),Color.TRANSPARENT))
  }
  modeButton=action("VIDEO     ·     TIMELAPSE").apply{textSize=13f;letterSpacing=.08f}
  footer.addView(modeButton,LinearLayout.LayoutParams(-1,dp(48)))
  timer=action("00:00").apply{textSize=15f;setTextColor(Color.rgb(255,77,84));visibility=View.INVISIBLE}
  footer.addView(timer,LinearLayout.LayoutParams(-1,dp(34)))
  val controls=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  val gallery=action("▣").apply{textSize=29f;contentDescription="Gallery";setOnClickListener{openGallery()}}
  shutter=action("").apply{
   textSize=55f;setTextColor(Color.rgb(246,58,69));contentDescription="Record or stop"
   setOnClickListener{if(active)stop()else start()}
   background=android.graphics.drawable.GradientDrawable().apply{
    setColor(Color.rgb(245,65,78));shape=android.graphics.drawable.GradientDrawable.OVAL;setStroke(dp(5),Color.WHITE)
   }
  }
  pauseButton=action("Ⅱ").apply{textSize=28f;visibility=View.INVISIBLE;contentDescription="Pause or resume";setOnClickListener{pauseOrResume()}}
  controls.addView(gallery,LinearLayout.LayoutParams(0,dp(76),1f))
  val center=LinearLayout(this).apply{gravity=Gravity.CENTER;addView(shutter,LinearLayout.LayoutParams(dp(76),dp(76)))}
  controls.addView(center,LinearLayout.LayoutParams(0,dp(88),1f))
  controls.addView(pauseButton,LinearLayout.LayoutParams(0,dp(76),1f))
  footer.addView(controls)
  lensButton=action("⟳").apply{textSize=25f;contentDescription="Switch front and rear camera"}
  footer.addView(lensButton,LinearLayout.LayoutParams(dp(56),dp(46)))
  root.addView(footer,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
  setContentView(root)
  qualityButton.setOnClickListener{
   if(active)return@setOnClickListener
   val opts=arrayOf("720p HD","1080p Full HD","2160p 4K (if available)")
   AlertDialog.Builder(this).setTitle("Video resolution").setSingleChoiceItems(opts,when(quality){720->0;2160->2;else->1}){d,index->quality=intArrayOf(720,1080,2160)[index];qualityButton.text=opts[index].substringBefore(" ");d.dismiss()}.show()
  }
  lensButton.setOnClickListener{if(active)return@setOnClickListener;lens=if(lens==CameraCharacteristics.LENS_FACING_BACK)CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK;closeIdle();openIdle()}
  modeButton.setOnClickListener{
   if(active)return@setOnClickListener
   val options=arrayOf("Video (with microphone)","Video (silent)","Timelapse · 1s","Timelapse · 2s","Timelapse · 5s","Timelapse · 10s","Timelapse · 30s")
   AlertDialog.Builder(this).setTitle("Capture mode").setItems(options){_,i->
    interval=when(i){2->1;3->2;4->5;5->10;6->30;else->0};audio=i==0
    modeButton.text=if(interval>0)"TIMELAPSE  ·  ${interval}s" else if(audio)"VIDEO  ·  AUDIO ON" else "VIDEO  ·  SILENT"
   }.show()
  }
  preview.surfaceTextureListener=object:TextureView.SurfaceTextureListener{
   override fun onSurfaceTextureAvailable(t:SurfaceTexture,w:Int,h:Int){attachSurface()}
   override fun onSurfaceTextureSizeChanged(t:SurfaceTexture,w:Int,h:Int){}
   override fun onSurfaceTextureDestroyed(t:SurfaceTexture):Boolean{detachSurface();return true}
   override fun onSurfaceTextureUpdated(t:SurfaceTexture){}
  }
  val ask=mutableListOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
  if(Build.VERSION.SDK_INT>=33)ask.add(Manifest.permission.POST_NOTIFICATIONS)
  val missing=ask.filter{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED}
  if(missing.isNotEmpty())ActivityCompat.requestPermissions(this,missing.toTypedArray(),1)
  ui.post(updater)
 }
 private fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
 private fun command(action:String){startService(Intent(this,CaptureService::class.java).apply{this.action=action})}
 private fun attachSurface(){
  if(!preview.isAvailable)return
  preview.surfaceTexture?.setDefaultBufferSize(1280,720)
  previewSurface=Surface(preview.surfaceTexture!!)
  CameraPreviewBridge.surface=previewSurface
  if(CaptureService.running)command(CaptureService.ATTACH) else openIdle()
 }
 private fun detachSurface(){
  if(CaptureService.running)command(CaptureService.DETACH) else closeIdle()
  CameraPreviewBridge.surface=null
  // Service removes this target before a new session is created.
  previewSurface=null
 }
 private fun openIdle(){
  if(!preview.isAvailable||CaptureService.running||idleCamera!=null)return
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return
  val manager=getSystemService(CAMERA_SERVICE) as CameraManager
  val id=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==lens}?:return
  try{manager.openCamera(id,object:CameraDevice.StateCallback(){
   override fun onOpened(c:CameraDevice){idleCamera=c;val s=previewSurface?:return;c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply{addTarget(s);if(stabilized)try{set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)}catch(_:Exception){}}.also{builder->
    @Suppress("DEPRECATION") c.createCaptureSession(listOf(s),object:CameraCaptureSession.StateCallback(){
     override fun onConfigured(session:CameraCaptureSession){idleSession=session;try{session.setRepeatingRequest(builder.build(),null,handler)}catch(_:Exception){}}
     override fun onConfigureFailed(session:CameraCaptureSession){closeIdle()}
    },handler)
   }}
   override fun onDisconnected(c:CameraDevice){closeIdle()}
   override fun onError(c:CameraDevice,error:Int){closeIdle()}
  },handler)}catch(_:Exception){state.text="Camera not available"}
 }
 private fun closeIdle(){try{idleSession?.close()}catch(_:Exception){};idleSession=null;try{idleCamera?.close()}catch(_:Exception){};idleCamera=null}
 private fun start(){
  if(active||starting||CaptureService.phase=="SAVING"||CaptureService.phase=="STARTING")return
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),1);return}
  if(audio&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),1);return}
  starting=true;closeIdle()
  handler.postDelayed({
   val intent=Intent(this,CaptureService::class.java).apply{action=CaptureService.START;putExtra("lens",lens);putExtra("height",quality);putExtra("interval",interval);putExtra("audio",audio);putExtra("stabilize",stabilized)}
   try{ContextCompat.startForegroundService(this,intent);ui.post{starting=false;state.text="● STARTING";lensButton.alpha=.45f;ui.postDelayed({syncRecordingState()},600)}}
   catch(e:Exception){ui.post{starting=false;state.text="Failed to start";openIdle()}}
  },350)
 }
 private fun syncRecordingState(){
  when(CaptureService.phase){
   "RECORDING","PAUSED"->{
    if(!active){elapsedStart=SystemClock.elapsedRealtime();pausedElapsed=0L}
    active=true;paused=CaptureService.phase=="PAUSED"
    state.text=if(paused)"PAUSED" else "● RECORDING"
    timer.visibility=View.VISIBLE;pauseButton.visibility=View.VISIBLE
    pauseButton.text=if(paused)"▶" else "Ⅱ";shutter.text="■"
   }
   "STARTING"->ui.postDelayed({syncRecordingState()},400)
   "ERROR"->{active=false;starting=false;shutter.text="●";lensButton.alpha=1f;state.text="Recording failed: "+CaptureService.lastError.take(90);openIdle()}
   else->{if(!active)ui.postDelayed({if(CaptureService.phase=="STARTING")syncRecordingState()},350)}
  }
 }
 private fun stop(){
  command(CaptureService.STOP);active=false;paused=false;timer.visibility=View.INVISIBLE;pauseButton.visibility=View.INVISIBLE;pauseButton.text="Ⅱ";shutter.text="●";state.text="Saving to Gallery…";lensButton.alpha=1f
  ui.postDelayed({if(!CaptureService.running){state.text=if(CaptureService.phase=="ERROR")"Recording failed" else "READY";openIdle()}},1800)
 }
 private fun pauseOrResume(){if(!active)return;command(if(paused)CaptureService.RESUME else CaptureService.PAUSE);paused=!paused;pauseButton.text=if(paused)"▶" else "Ⅱ";state.text=if(paused)"PAUSED" else "● RECORDING"}
 private fun openGallery(){startActivity(Intent(this,GalleryActivity::class.java))}
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(requestCode,permissions,results);if(preview.isAvailable)openIdle()}
 override fun onResume(){super.onResume();if(preview.isAvailable)attachSurface();active=CaptureService.running||CaptureService.phase=="STARTING";if(active){shutter.text="■";timer.visibility=View.VISIBLE;pauseButton.visibility=View.VISIBLE;state.text=if(CaptureService.phase=="PAUSED")"PAUSED" else if(CaptureService.phase=="STARTING")"● STARTING" else "● RECORDING"}}
 override fun onPause(){super.onPause();detachSurface()}
 override fun onDestroy(){ui.removeCallbacks(updater);closeIdle();thread.quitSafely();super.onDestroy()}
}
