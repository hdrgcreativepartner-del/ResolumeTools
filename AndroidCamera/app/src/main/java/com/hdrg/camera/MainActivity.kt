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
import android.graphics.Matrix
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.Size


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
 private var stabilized=false
 private var lastPreviewFrame=0L
 private var previewStartedAt=0L
 private var previewProblem=""
 private var previewRecoveryAttempted=false
 private var previewFrames=0L
 @Volatile private var previewSurface:Surface?=null
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
 @Volatile private var starting=false
  private var exposureSteps=0
 private var whiteBalanceMode=CaptureRequest.CONTROL_AWB_MODE_AUTO
 @Volatile private var resumed=false
 private var idleOpening=false
 private var idleClosing=false
 private var generation=0
 private var afterIdleClose:(()->Unit)?=null
 private lateinit var viewfinder:FrameLayout
 private lateinit var videoMode:TextView
 private lateinit var lapseMode:TextView
 private lateinit var audioButton:TextView
 private lateinit var galleryButton:TextView
 private var previewSize=Size(1280,720)
 private var sensorOrientation=90
 private val mediaWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
 private val updater=object:Runnable{override fun run(){
  if(!resumed)return
  syncRecordingState()
  updatePreviewHealth()
  if(active){val seconds=CaptureService.elapsedMillis()/1000;timer.text="%02d:%02d".format(seconds/60,seconds%60)}
  ui.postDelayed(this,500)
 }}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
  window.statusBarColor=Color.TRANSPARENT;window.navigationBarColor=Color.TRANSPARENT
  if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false)
  else window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
  if(Build.VERSION.SDK_INT>=29)window.isNavigationBarContrastEnforced=false
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  val prefs=getPreferences(MODE_PRIVATE)
  quality=prefs.getInt("quality",1080);interval=prefs.getInt("interval",0);audio=prefs.getBoolean("audio",true);stabilized=prefs.getBoolean("stabilized",false)
  val root=FrameLayout(this).apply{setBackgroundColor(0xff0b0d11.toInt())}
  root.setOnApplyWindowInsetsListener{v,insets->
   if(Build.VERSION.SDK_INT>=30){val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom)}
   else v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
   insets
  }
  fun action(label:String,description:String=label):TextView=TextView(this).apply{
   text=label;contentDescription=description;setTextColor(Color.WHITE);textSize=13f;gravity=Gravity.CENTER
   typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);minimumWidth=dp(48);minimumHeight=dp(48)
   background=RippleDrawable(ColorStateList.valueOf(0x33ffffff),null,GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(24).toFloat()})
   isFocusable=true
  }
  val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(8),0)}
  top.addView(action("HDRG CAM").apply{letterSpacing=.1f;textSize=13f;setOnClickListener{showAbout()}},LinearLayout.LayoutParams(0,dp(56),1f))
  gridOverlay=GridOverlay(this).apply{gridVisible=prefs.getBoolean("grid",false)}
  gridButton=action("","Garis komposisi").apply{setOnClickListener{gridOverlay.gridVisible=!gridOverlay.gridVisible;setIcon("grid",gridOverlay.gridVisible)}}
  gridButton.setIcon("grid",gridOverlay.gridVisible);
  stabilizeButton=action("","Stabilisasi").apply{setOnClickListener{if(canAdjust()){stabilized=!stabilized;setIcon("stabilize",stabilized);updateIdleRequest()}}}
  stabilizeButton.setIcon("stabilize",stabilized);
  qualityButton=action("${quality}p","Resolusi video").apply{setOnClickListener{if(canAdjust())chooseQuality()}}
  top.addView(qualityButton,LinearLayout.LayoutParams(dp(80),dp(48)))
  top.addView(action("PRO","Kontrol kamera manual").apply{setOnClickListener{showProControls()}},LinearLayout.LayoutParams(dp(48),dp(48)))
  top.setBackgroundColor(0xff0b0d11.toInt())
  viewfinder=FrameLayout(this).apply{setBackgroundColor(Color.BLACK);clipChildren=true}
  preview=TextureView(this)
  viewfinder.addView(preview,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
  viewfinder.addView(gridOverlay,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
  state=action("Menghubungkan kamera…").apply{textSize=12f;setPadding(dp(12),0,dp(12),0);background=GradientDrawable().apply{setColor(0x99000000.toInt());cornerRadius=dp(16).toFloat()};setOnClickListener{if(canAdjust())retryPreview()};setOnLongClickListener{showDiagnostics();true}}
  viewfinder.addView(state,FrameLayout.LayoutParams(-2,dp(32),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=dp(12)})
  timer=action("00:00").apply{typeface=Typeface.MONOSPACE;setTextColor(Color.rgb(255,91,99));visibility=View.GONE;background=null}
  viewfinder.addView(timer,FrameLayout.LayoutParams(-2,dp(40),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{topMargin=dp(12)})
  val viewTools=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=GradientDrawable().apply{setColor(0x99000000.toInt());cornerRadius=dp(24).toFloat()}}
  viewTools.addView(gridButton,LinearLayout.LayoutParams(dp(48),dp(48)));viewTools.addView(stabilizeButton,LinearLayout.LayoutParams(dp(48),dp(48)))
  viewfinder.addView(viewTools,FrameLayout.LayoutParams(dp(96),dp(48),Gravity.TOP or Gravity.END).apply{topMargin=dp(12);rightMargin=dp(12)})
  root.addView(viewfinder,FrameLayout.LayoutParams(-1,-1).apply{topMargin=dp(56);bottomMargin=dp(176)})
  root.addView(top,FrameLayout.LayoutParams(-1,dp(56),Gravity.TOP))
  viewfinder.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->fitPreview()}
  val footer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(4),dp(12),dp(4));setBackgroundColor(0xff0b0d11.toInt())}
  val modes=LinearLayout(this).apply{gravity=Gravity.CENTER}
  videoMode=action("VIDEO").apply{letterSpacing=.08f;setOnClickListener{if(canAdjust()){interval=0;updateModes()}}}
  lapseMode=action("TIMELAPSE").apply{letterSpacing=.08f;setOnClickListener{if(canAdjust()){if(interval==0)interval=2;updateModes()}}}
  modes.addView(videoMode,LinearLayout.LayoutParams(dp(110),dp(48)));modes.addView(lapseMode,LinearLayout.LayoutParams(dp(130),dp(48)))
  footer.addView(modes)
  val controls=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(20),0,dp(20),0)}
  galleryButton=action("","Buka galeri").apply{setOnClickListener{openGallery()}}
  galleryButton.setIcon("gallery");controls.addView(galleryButton,LinearLayout.LayoutParams(0,dp(72),1f))
  shutter=action("","Mulai merekam").apply{background=ShutterDrawable(false);setOnClickListener{performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);if(active)stop()else start()}}
  controls.addView(shutter,LinearLayout.LayoutParams(dp(76),dp(76)))
  val right=FrameLayout(this)
  lensButton=action("","Ganti kamera depan atau belakang").apply{setOnClickListener{if(canAdjust()){lens=if(lens==CameraCharacteristics.LENS_FACING_BACK)CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK;closeIdle{openIdle()}}}}
  lensButton.setIcon("flip")
  pauseButton=action("","Jeda rekaman").apply{visibility=View.GONE;setOnClickListener{pauseOrResume()}}
  pauseButton.setIcon("pause")
  right.addView(lensButton,FrameLayout.LayoutParams(dp(56),dp(56),Gravity.CENTER));right.addView(pauseButton,FrameLayout.LayoutParams(dp(56),dp(56),Gravity.CENTER))
  controls.addView(right,LinearLayout.LayoutParams(0,dp(80),1f));footer.addView(controls)
  val options=LinearLayout(this).apply{gravity=Gravity.CENTER}
  audioButton=action("").apply{textSize=11f;setOnClickListener{if(canAdjust()&&interval==0){audio=!audio;updateModes()}}}
  modeButton=action("").apply{textSize=11f;setOnClickListener{if(canAdjust()&&interval>0)chooseInterval()}}
  options.addView(audioButton,LinearLayout.LayoutParams(0,dp(48),1f));options.addView(modeButton,LinearLayout.LayoutParams(0,dp(48),1f));options.addView(action("Latar belakang","Lanjutkan merekam di background").apply{textSize=10f;setOnClickListener{if(active)moveTaskToBack(true)else Toast.makeText(this@MainActivity,"Mulai rekaman terlebih dahulu",Toast.LENGTH_SHORT).show()}},LinearLayout.LayoutParams(0,dp(48),1f));footer.addView(options)
  root.addView(footer,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
  root.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->
   val landscape=root.width>root.height
   val rail=if(landscape)dp(260) else 0
   val stageParams=viewfinder.layoutParams as FrameLayout.LayoutParams
   val bottom=if(landscape)0 else footer.measuredHeight
   if(stageParams.rightMargin!=rail||stageParams.bottomMargin!=bottom){stageParams.rightMargin=rail;stageParams.bottomMargin=bottom;viewfinder.layoutParams=stageParams}
   val footerWidth=if(landscape)dp(260) else -1
   val footerGravity=if(landscape)Gravity.END or Gravity.CENTER_VERTICAL else Gravity.BOTTOM
   val fp=footer.layoutParams as FrameLayout.LayoutParams
   if(fp.width!=footerWidth||fp.gravity!=footerGravity){fp.width=footerWidth;fp.gravity=footerGravity;footer.layoutParams=fp}
   val tp=top.layoutParams as FrameLayout.LayoutParams
   if(tp.rightMargin!=rail){tp.rightMargin=rail;top.layoutParams=tp}
  }
  shutter.setOnTouchListener{v,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->v.animate().scaleX(.94f).scaleY(.94f).setDuration(100).start();MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()};false}
  updateModes();setContentView(root)
  preview.surfaceTextureListener=object:TextureView.SurfaceTextureListener{
   override fun onSurfaceTextureAvailable(t:SurfaceTexture,w:Int,h:Int){attachSurface()}
   override fun onSurfaceTextureSizeChanged(t:SurfaceTexture,w:Int,h:Int){fitPreview()}
   override fun onSurfaceTextureDestroyed(t:SurfaceTexture):Boolean{detachSurface();return true}
   override fun onSurfaceTextureUpdated(t:SurfaceTexture){lastPreviewFrame=SystemClock.elapsedRealtime();previewFrames++;if(!active&&!starting)updatePreviewHealth()}
  }
  val permissions=mutableListOf(Manifest.permission.CAMERA)
  if(Build.VERSION.SDK_INT>=33)permissions.add(Manifest.permission.POST_NOTIFICATIONS)
  val missing=permissions.filter{checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}
  if(missing.isNotEmpty())requestPermissions(missing.toTypedArray(),1)
 }
 private fun canAdjust()=!(active||starting||CaptureService.phase in listOf("STARTING","SAVING"))
 private fun updateModes(){
  val accent=Color.rgb(113,174,255)
  videoMode.setTextColor(if(interval==0)accent else Color.LTGRAY);videoMode.isSelected=interval==0
  lapseMode.setTextColor(if(interval>0)accent else Color.LTGRAY);lapseMode.isSelected=interval>0
  audioButton.text=if(interval>0)"Tanpa audio" else if(audio)"Audio aktif" else "Tanpa audio"
  shutter.background=ShutterDrawable(active,interval>0)
  audioButton.alpha=if(interval>0).4f else 1f;modeButton.text=if(interval>0)"Interval ${interval}s" else "30 FPS"
 }
 private fun showAbout(){showDiagnostics()}
 private fun bottomChoices(title:String,items:Array<String>,selected:Int,onSelect:(Int)->Unit){
  val dialog=AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(items,selected){d,i->onSelect(i);d.dismiss()}.setNegativeButton("Tutup",null).create()
  dialog.show();styleSheet(dialog);dialog.window?.apply{setGravity(Gravity.BOTTOM);setLayout(-1,-2)}
 }
 private fun chooseQuality(){bottomChoices("Resolusi video",arrayOf("HD · 720p","Full HD · 1080p","4K · jika didukung"),when(quality){720->0;2160->2;else->1}){quality=intArrayOf(720,1080,2160)[it];qualityButton.text="${quality}p"}}
 private fun chooseInterval(){val values=intArrayOf(1,2,5,10,30);bottomChoices("Interval timelapse",values.map{"Setiap $it detik"}.toTypedArray(),values.indexOf(interval)){interval=values[it];updateModes()}}
 private fun fitPreview(){
  if(!::viewfinder.isInitialized||viewfinder.width==0||viewfinder.height==0)return
  val rotation=windowManager.defaultDisplay.rotation*90
  val fitted=CameraGeometry.fit(viewfinder.width,viewfinder.height,previewSize.width,previewSize.height,sensorOrientation,rotation)
  val w=fitted.first;val h=fitted.second
  for(v in arrayOf<View>(preview,gridOverlay))if(v.layoutParams.width!=w||v.layoutParams.height!=h)v.layoutParams=FrameLayout.LayoutParams(w,h,Gravity.CENTER)
  val matrix=Matrix()
  if(rotation%180!=0)matrix.setScale(h.toFloat()/w,w.toFloat()/h,w/2f,h/2f)
  matrix.postRotate(-rotation.toFloat(),w/2f,h/2f);preview.setTransform(matrix)
 }
 private fun refreshThumbnail(){if(mediaWorker.isShutdown)return;mediaWorker.execute{
  val latest=MediaCatalog.list(this).firstOrNull();val bitmap=latest?.let{MediaCatalog.thumbnail(this,it.second,dp(48))}
  ui.post{if(!isDestroyed&&bitmap!=null){val drawable=android.graphics.drawable.BitmapDrawable(resources,bitmap);drawable.setBounds(0,0,dp(44),dp(44));galleryButton.setCompoundDrawables(null,drawable,null,null)}}
 }}
 private fun TextView.setIcon(name:String,selected:Boolean=false){
  text="";setCompoundDrawables(null,null,null,null)
  val layer=android.graphics.drawable.LayerDrawable(arrayOf(CameraIcon(name,if(selected)0xff80b6ff.toInt() else Color.WHITE)))
  layer.setLayerSize(0,dp(23),dp(23));layer.setLayerGravity(0,Gravity.CENTER)
  background=RippleDrawable(ColorStateList.valueOf(0x3371aeff),layer,GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(24).toFloat()})
 }
 private fun styleSheet(dialog:AlertDialog){dialog.window?.setBackgroundDrawable(GradientDrawable().apply{setColor(0xff171c25.toInt());cornerRadii=floatArrayOf(dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(),0f,0f,0f,0f)})}
 private fun updatePreviewHealth(){
  if(!resumed||active||starting||CaptureService.phase=="SAVING")return
  val now=SystemClock.elapsedRealtime()
  val fresh=lastPreviewFrame>0&&now-lastPreviewFrame<2500
  shutter.isEnabled=fresh;shutter.alpha=if(fresh)1f else .35f
  val message=when{
   fresh->""
   !preview.isHardwareAccelerated->"Rend er kamera tidak aktif · ketuk ulang".replace("Rend er","Render")
   checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED->"Izinkan akses kamera · ketuk di sini"
   previewProblem.isNotEmpty()->previewProblem+" · ketuk ulang"
   previewStartedAt>0&&now-previewStartedAt>7000->"Belum menerima gambar · ketuk ulang"
   else->"Menghubungkan kamera…"
  }
  state.visibility=if(fresh)View.GONE else View.VISIBLE
  if(state.text.toString()!=message)state.text=message
  if(!fresh&&previewStartedAt>0&&now-previewStartedAt>7000&&!previewRecoveryAttempted){
   previewRecoveryAttempted=true;stabilized=false;ProCamera.iso=0;ProCamera.shutterNs=0;ProCamera.focusDiopters=-1f
   stabilizeButton.setIcon("stabilize",false);retryPreview(false)
  }
 }
 private fun retryPreview(resetRecovery:Boolean=true){
  if(!canAdjust())return
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.CAMERA),1);return}
  if(resetRecovery)previewRecoveryAttempted=false
  lastPreviewFrame=0;previewStartedAt=SystemClock.elapsedRealtime();previewProblem=""
  closeIdle{ui.post{if(previewSurface==null)attachSurface()else openIdle()}}
 }
 private fun showDiagnostics(){
  val text="HDRG Cam 0.6.3 RC4\n${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\nGPU: ${preview.isHardwareAccelerated} · Surface: ${preview.isAvailable}\nFrame: $previewFrames · ${if(lastPreviewFrame==0L)"belum ada" else "${SystemClock.elapsedRealtime()-lastPreviewFrame} ms lalu"}\nStatus: ${CaptureService.phase}\n${previewProblem.ifBlank{CaptureService.lastError}}"
  AlertDialog.Builder(this).setTitle("Diagnostik kamera").setMessage(text).setPositiveButton("Salin"){_,_->(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("HDRG Cam",text))}.setNegativeButton("Tutup",null).show()
 }
 private fun showProControls(){
  if(!canAdjust()){Toast.makeText(this,"Stop recording to adjust preview",Toast.LENGTH_SHORT).show();return}
  val manager=getSystemService(CAMERA_SERVICE) as CameraManager
  val id=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==lens}?:return
  val characteristics=manager.getCameraCharacteristics(id)
  val range=characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
  val min=range?.lower?:0;val upper=range?.upper?:0
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(12),dp(22),dp(12))}
  val label=TextView(this).apply{text="Kompensasi eksposur: $exposureSteps";textSize=16f;setTextColor(Color.WHITE)}
  panel.addView(label)
  val seek=SeekBar(this).apply{this.max=(upper-min).coerceAtLeast(0);progress=(exposureSteps-min).coerceIn(0,this.max);isEnabled=upper>min}
  panel.addView(seek)
  seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
   override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean){label.text="Kompensasi eksposur: ${min+progress}"}
   override fun onStartTrackingTouch(bar:SeekBar?){}
   override fun onStopTrackingTouch(bar:SeekBar?){}
  })
  val modes=intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO,CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
  val available=characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?:intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO)
  val names=arrayOf("Auto","Daylight","Cloudy","Tungsten")
  val supported=modes.indices.filter{available.contains(modes[it])}
  val spinner=Spinner(this)
  spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,supported.map{names[it]})
  spinner.setSelection(supported.indexOfFirst{modes[it]==whiteBalanceMode}.coerceAtLeast(0))
  panel.addView(TextView(this).apply{text="White balance";textSize=14f;setTextColor(Color.LTGRAY)})
  panel.addView(spinner)
  val manual=characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)==true
  val isoRange=characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
  val shutterRange=characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
  val focusMax=characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?:0f
  fun addSelector(title:String, options:List<String>, selected:Int):Spinner{
   panel.addView(TextView(this).apply{text=title;textSize=14f;setTextColor(Color.LTGRAY);setPadding(0,dp(12),0,0)})
   val selector=Spinner(this)
   selector.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,options)
   selector.setSelection(selected.coerceIn(0,options.lastIndex))
   panel.addView(selector)
   return selector
  }
  val isoValues=if(manual&&isoRange!=null)listOf(0,100,200,400,800,1600,3200,6400).filter{it==0||it in isoRange.lower..isoRange.upper} else listOf(0)
  val shutterValues=if(manual&&shutterRange!=null)listOf(0L,1_000_000_000L/1000,1_000_000_000L/500,1_000_000_000L/250,1_000_000_000L/125,1_000_000_000L/60,1_000_000_000L/30,1_000_000_000L/15).filter{it==0L||it in shutterRange.lower..shutterRange.upper} else listOf(0L)
  val isoSelector=addSelector("ISO",isoValues.map{if(it==0)"Auto" else it.toString()},isoValues.indexOf(ProCamera.iso).coerceAtLeast(0))
  val shutterSelector=addSelector("Kecepatan rana",shutterValues.map{if(it==0L)"Auto" else "1/"+(1_000_000_000L/it)},shutterValues.indexOf(ProCamera.shutterNs).coerceAtLeast(0))
  val focusValues=if(focusMax>0f)listOf(-1f,0f,focusMax*.1f,focusMax*.25f,focusMax*.5f,focusMax) else listOf(-1f)
  val focusSelector=addSelector("Fokus",focusValues.mapIndexed{i,_->when(i){0->"Auto";1->"Tak terhingga";else->"Manual "+(i-1)}},focusValues.indexOf(ProCamera.focusDiopters).coerceAtLeast(0))
  panel.addView(TextView(this).apply{text="Pilih ISO dan shutter manual bersama untuk mengaktifkan eksposur manual.";textSize=12f;setTextColor(Color.LTGRAY)})
  if(!manual)panel.addView(TextView(this).apply{text="Kamera ini tidak mendukung ISO/shutter manual.";setTextColor(Color.LTGRAY)})
  if(focusMax<=0f)panel.addView(TextView(this).apply{text="Lensa ini tidak mendukung fokus manual.";setTextColor(Color.LTGRAY)})
  val scroll=ScrollView(this).apply{addView(panel)}
  val dialog=AlertDialog.Builder(this).setTitle("Kontrol PRO").setView(scroll).setNegativeButton("Batal",null).setPositiveButton("Terapkan"){_,_->
   exposureSteps=(min+seek.progress).coerceIn(min,upper)
   if(supported.isNotEmpty())whiteBalanceMode=modes[supported[spinner.selectedItemPosition]]
   ProCamera.iso=isoValues[isoSelector.selectedItemPosition]
   ProCamera.shutterNs=shutterValues[shutterSelector.selectedItemPosition]
   ProCamera.focusDiopters=focusValues[focusSelector.selectedItemPosition]
   updateIdleRequest()
  }.create()
  dialog.show();styleSheet(dialog);dialog.window?.apply{setGravity(Gravity.BOTTOM);setLayout(-1,(resources.displayMetrics.heightPixels*.78f).toInt())}
 }
 private fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
 private fun command(action:String){startService(Intent(this,CaptureService::class.java).apply{this.action=action})}
 private fun attachSurface(){
  if(!preview.isAvailable||!resumed||previewSurface!=null)return
  previewSurface=Surface(preview.surfaceTexture!!);CameraPreviewBridge.surface=previewSurface
  lastPreviewFrame=0;previewStartedAt=SystemClock.elapsedRealtime();previewProblem=""
  if(CaptureService.running||CaptureService.phase=="STARTING")command(CaptureService.ATTACH)else openIdle()
 }
 private fun detachSurface(){
  CameraPreviewBridge.surface=null
  val old=previewSurface;previewSurface=null
  if(CaptureService.running||CaptureService.phase=="STARTING")command(CaptureService.DETACH)else closeIdle{old?.release()}
 }
 private fun updateIdleRequest(){handler.post{
  val dev=idleCamera?:return@post;val surface=previewSurface?:return@post
  try{
   val chars=(getSystemService(CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(dev.id)
   val request=dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply{
    addTarget(surface);ProCamera.apply(this,chars,exposureSteps,whiteBalanceMode)
    val available=chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?:intArrayOf()
    set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,if(stabilized&&available.contains(1))1 else 0)
   }
   idleSession?.setRepeatingRequest(request.build(),null,handler)
  }catch(e:Exception){ui.post{previewProblem="Preview: ${e.message?.take(60)}";updatePreviewHealth()}}
 }}
 private fun openIdle(){handler.post{
  if(!resumed||previewSurface==null||CaptureService.running||starting||idleOpening||idleClosing||CaptureService.phase in listOf("STARTING","SAVING")||idleCamera!=null)return@post
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return@post
  val manager=getSystemService(CAMERA_SERVICE) as CameraManager
  try{
   val id=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==lens}?:error("Lensa tidak tersedia")
   val chars=manager.getCameraCharacteristics(id)
   sensorOrientation=chars.get(CameraCharacteristics.SENSOR_ORIENTATION)?:90
   val sizes=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java)?:emptyArray()
   previewSize=sizes.filter{it.width<=1920&&it.height<=1080}.minByOrNull{kotlin.math.abs(it.width.toFloat()/it.height-16f/9f)*10000+kotlin.math.abs(it.height-720)}?:sizes.firstOrNull()?:Size(1280,720)
   preview.surfaceTexture?.setDefaultBufferSize(previewSize.width,previewSize.height)
   ui.post{fitPreview()}
   idleOpening=true;val token=++generation
   manager.openCamera(id,object:CameraDevice.StateCallback(){
    override fun onOpened(c:CameraDevice){
     idleOpening=false
     if(token!=generation||!resumed||starting){c.close();return}
     idleCamera=c;val target=previewSurface?:run{c.close();return}
     @Suppress("DEPRECATION") c.createCaptureSession(listOf(target),object:CameraCaptureSession.StateCallback(){
      override fun onConfigured(s:CameraCaptureSession){if(token!=generation||idleCamera!==c){s.close();return};idleSession=s;updateIdleRequest();ui.post{previewStartedAt=SystemClock.elapsedRealtime();updatePreviewHealth()}}
      override fun onConfigureFailed(s:CameraCaptureSession){closeIdle();ui.post{previewProblem="Konfigurasi preview gagal";updatePreviewHealth()}}
     },handler)
    }
    override fun onClosed(c:CameraDevice){idleClosing=false;val next=afterIdleClose;afterIdleClose=null;next?.invoke();if(resumed&&!starting)openIdle()}
    override fun onDisconnected(c:CameraDevice){c.close();idleCamera=null;idleOpening=false}
    override fun onError(c:CameraDevice,error:Int){c.close();idleCamera=null;idleOpening=false;ui.post{previewProblem="Kamera tidak tersedia ($error)";updatePreviewHealth()}}
   },handler)
  }catch(e:Exception){idleOpening=false;ui.post{previewProblem="Kamera: ${e.message?.take(60)}";updatePreviewHealth()}}
 }}
 private fun closeIdle(next:(()->Unit)?=null){handler.post{
  generation++;afterIdleClose=next
  try{idleSession?.close()}catch(_:Exception){};idleSession=null
  val old=idleCamera;idleCamera=null
  if(old!=null){idleClosing=true;old.close()}else if(!idleOpening&&!idleClosing){afterIdleClose=null;next?.invoke()}
 }}
 private fun start(){
  if(active||starting||CaptureService.phase=="SAVING"||CaptureService.phase=="STARTING")return
  if(lastPreviewFrame==0L||SystemClock.elapsedRealtime()-lastPreviewFrame>2500){retryPreview();return}
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.CAMERA),1);return}
  if(audio&&interval==0&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),1);return}
  starting=true;shutter.isEnabled=false;state.text="Memulai rekaman"
  closeIdle{
   if(!resumed){ui.post{starting=false;lastPhase=""};return@closeIdle}
   val intent=Intent(this,CaptureService::class.java).apply{action=CaptureService.START;putExtra("lens",lens);putExtra("height",quality);putExtra("interval",interval);putExtra("audio",audio&&interval==0);putExtra("stabilize",stabilized);putExtra("iso",ProCamera.iso);putExtra("shutterNs",ProCamera.shutterNs);putExtra("focusDiopters",ProCamera.focusDiopters);putExtra("exposureSteps",exposureSteps);putExtra("whiteBalance",whiteBalanceMode);putExtra("rotation",windowManager.defaultDisplay.rotation*90)}
   try{startForegroundService(intent);ui.post{state.text="Memulai rekaman"}}
   catch(e:Exception){ui.post{starting=false;state.text="Failed to start";openIdle()}}
  }
 }
 private var lastPhase=""
 private fun syncRecordingState(){
  if(starting&&CaptureService.phase=="IDLE")return
  val phase=CaptureService.phase
  if(phase==lastPhase)return
  lastPhase=phase
  active=phase=="RECORDING"||phase=="PAUSED";paused=phase=="PAUSED"
  if(phase!="STARTING")starting=false
  val busy=active||phase=="STARTING"||phase=="SAVING"
  shutter.isEnabled=active||!busy;shutter.alpha=if(shutter.isEnabled)1f else .4f
  shutter.background=ShutterDrawable(active,interval>0);shutter.contentDescription=if(active)"Hentikan dan simpan rekaman" else "Mulai merekam"
  timer.visibility=if(active)View.VISIBLE else View.GONE
  pauseButton.visibility=if(active)View.VISIBLE else View.GONE;lensButton.visibility=if(active)View.GONE else View.VISIBLE
  pauseButton.setIcon(if(paused)"play" else "pause");pauseButton.contentDescription=if(paused)"Lanjutkan rekaman" else "Jeda rekaman"
  for(v in arrayOf(lensButton,qualityButton,stabilizeButton,videoMode,lapseMode)){v.isEnabled=!busy;v.alpha=if(busy).4f else 1f}
  state.visibility=View.VISIBLE
  state.text=when(phase){"RECORDING"->"● MEREKAM";"PAUSED"->"DIJEDA";"STARTING"->"Memulai rekaman";"SAVING"->"Menyimpan…";"ERROR"->"Gagal: ${CaptureService.lastError.take(70)}";else->"Menghubungkan kamera…"}
  if(!busy){openIdle();refreshThumbnail()}
 }
 private fun stop(){command(CaptureService.STOP);shutter.isEnabled=false;state.text="Menyimpan…"}
 private fun pauseOrResume(){if(active)command(if(paused)CaptureService.RESUME else CaptureService.PAUSE)}
 private fun openGallery(){startActivity(Intent(this,GalleryActivity::class.java))}
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(requestCode,permissions,results);if(preview.isAvailable){if(previewSurface==null)attachSurface()else openIdle()}}
 override fun onConfigurationChanged(config:android.content.res.Configuration){super.onConfigurationChanged(config);viewfinder.post{fitPreview()}}
 override fun onResume(){super.onResume();resumed=true;lastPhase="";if(preview.isAvailable)attachSurface();ui.removeCallbacks(updater);ui.post(updater);refreshThumbnail()}
 override fun onPause(){resumed=false;if(CaptureService.phase!="STARTING")starting=false;ui.removeCallbacks(updater);getPreferences(MODE_PRIVATE).edit().putInt("quality",quality).putInt("interval",interval).putBoolean("audio",audio).putBoolean("stabilized",stabilized).putBoolean("grid",gridOverlay.gridVisible).apply();detachSurface();super.onPause()}
 override fun onDestroy(){ui.removeCallbacksAndMessages(null);closeIdle{thread.quitSafely()};mediaWorker.shutdownNow();super.onDestroy()}
}
