package com.hdrg.camera

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
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
  val shade=View(this).apply{setBackgroundColor(Color.argb(85,0,0,0))}
  root.addView(shade,FrameLayout.LayoutParams(-1,dp(120),Gravity.TOP))
  val top=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),0)}
  val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  fun action(label:String):TextView=TextView(this).apply{text=label;setTextColor(Color.WHITE);textSize=16f;gravity=Gravity.CENTER;setPadding(dp(10),dp(12),dp(10),dp(12));minimumHeight=dp(48)}
  val brand=action("HDRG  /  CAMERA").apply{textSize=15f;letterSpacing=.13f;setTypeface(typeface,1);gravity=Gravity.CENTER_VERTICAL or Gravity.LEFT}
  row.addView(brand,LinearLayout.LayoutParams(0,dp(48),1f))
  gridButton=action("▦").apply{contentDescription="Toggle grid";setOnClickListener{gridOverlay.gridVisible=!gridOverlay.gridVisible;setTextColor(if(gridOverlay.gridVisible)Color.rgb(58,222,233) else Color.WHITE)}}
  row.addView(gridButton)
  stabilizeButton=action("STAB").apply{contentDescription="Toggle requested video stabilization when supported";setTextColor(Color.rgb(58,222,233));setOnClickListener{if(!active){stabilized=!stabilized;setTextColor(if(stabilized)Color.rgb(58,222,233) else Color.WHITE);closeIdle();openIdle()}}}
  row.addView(stabilizeButton)
  qualityButton=action("1080p")
  row.addView(qualityButton)
  lensButton=action("↻")
  row.addView(lensButton)
  top.addView(row)
  state=action("READY  •  CAMERA").apply{setTextColor(Color.rgb(190,213,234));textSize=12f;gravity=Gravity.LEFT}
  top.addView(state)
  root.addView(top,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
  val footer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(18));setBackgroundColor(Color.argb(178,3,8,15))}
  modeButton=action("VIDEO     /     TIMELAPSE").apply{textSize=13f;letterSpacing=.12f}
  footer.addView(modeButton)
  timer=action("00:00").apply{textSize=15f;setTextColor(Color.rgb(255,105,114));visibility=View.INVISIBLE}
  footer.addView(timer)
  val controls=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL}
  val gallery=action("▣").apply{textSize=27f;setOnClickListener{openGallery()}}
  shutter=action("●").apply{textSize=66f;setTextColor(Color.rgb(255,75,91));setOnClickListener{if(active)stop()else start()}}
  pauseButton=action("Ⅱ").apply{textSize=27f;visibility=View.INVISIBLE;setOnClickListener{pauseOrResume()}}
  controls.addView(gallery,LinearLayout.LayoutParams(0,dp(75),1f))
  controls.addView(shutter,LinearLayout.LayoutParams(0,dp(90),1f))
  controls.addView(pauseButton,LinearLayout.LayoutParams(0,dp(75),1f))
  footer.addView(controls)
  val hint=action("RECORDING CONTINUES WITH VISIBLE NOTIFICATION").apply{textSize=10f;letterSpacing=.08f;setTextColor(Color.rgb(174,195,213))}
  footer.addView(hint)
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
   try{ContextCompat.startForegroundService(this,intent);ui.post{active=true;starting=false;paused=false;elapsedStart=SystemClock.elapsedRealtime();state.text="● STARTING";timer.visibility=View.VISIBLE;pauseButton.visibility=View.VISIBLE;shutter.text="■";lensButton.alpha=.45f}}
   catch(e:Exception){ui.post{starting=false;state.text="Failed to start";openIdle()}}
  },350)
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
