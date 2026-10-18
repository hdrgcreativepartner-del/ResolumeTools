package com.hdrg.camera

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import android.provider.MediaStore
import android.content.ContentValues
import android.util.Size
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CaptureService:Service(){
 companion object{
  const val START="HDRG_START";const val STOP="HDRG_STOP";const val PAUSE="HDRG_PAUSE";const val RESUME="HDRG_RESUME";const val ATTACH="HDRG_ATTACH";const val DETACH="HDRG_DETACH";const val CHANNEL="hdrg_camera_capture"
  @Volatile var running=false
  @Volatile var phase="IDLE"
  @Volatile var lastError=""
 }
 private val thread=HandlerThread("HDRGRecord").apply{start()}
 private val handler=Handler(thread.looper)
 private var recorder:MediaRecorder?=null
 private var camera:CameraDevice?=null
 private var session:CameraCaptureSession?=null
 private var output:File?=null
 private var recording=false
 private var starting=false
 private var finishing=false
 private var isPaused=false
 private var stabilizationSupported=false
 private var lens=CameraCharacteristics.LENS_FACING_BACK
 private var cameraId=""
 private var stabilize=true
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"HDRG Camera Recording",NotificationManager.IMPORTANCE_LOW))}
 private fun notice(label:String):Notification{
  val stop=PendingIntent.getService(this,7,Intent(this,CaptureService::class.java).apply{action=STOP},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("HDRG Camera recording").setContentText(label).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Stop",stop).build()
 }
 private fun update(label:String){(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(17,notice(label))}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  when(intent?.action){
   STOP->handler.post{finish()}
   PAUSE->handler.post{if(recording&&!isPaused)try{recorder?.pause();isPaused=true;phase="PAUSED";update("Paused · tap Stop to save")}catch(e:Exception){lastError=e.message?:"Pause failed"}}
   RESUME->handler.post{if(recording&&isPaused)try{recorder?.resume();isPaused=false;phase="RECORDING";update("Recording · tap Stop to save")}catch(e:Exception){lastError=e.message?:"Resume failed"}}
   ATTACH->handler.post{if(recording)configureSession()}
   DETACH->handler.post{if(recording)configureSession(true)}
   START->{
    if(running||starting||finishing)return START_NOT_STICKY
    starting=true;phase="STARTING";lastError=""
    val includeAudio=intent.getBooleanExtra("audio",false)
    val type=if(includeAudio)ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
    try{
     if(Build.VERSION.SDK_INT>=29)startForeground(17,notice("Preparing recording…"),type)else startForeground(17,notice("Preparing recording…"))
     lens=intent.getIntExtra("lens",CameraCharacteristics.LENS_FACING_BACK)
     stabilize=intent.getBooleanExtra("stabilize",true)
     handler.post{begin(intent.getIntExtra("interval",0),intent.getIntExtra("height",1080),includeAudio)}
    }catch(e:Exception){starting=false;phase="ERROR";lastError=e.message?:"Failed to start service";stopSelf()}
   }
  }
  return START_NOT_STICKY
 }
 private fun configureSession(forceNoPreview:Boolean=false){
  val dev=camera?:return
  val rec=recorder?:return
  try{
   session?.stopRepeating();session?.close();session=null
   val recordSurface=rec.surface
   val previewSurface=if(forceNoPreview)null else CameraPreviewBridge.surface
   val targets=mutableListOf(recordSurface)
   if(previewSurface!=null&&previewSurface.isValid)targets.add(previewSurface)
   val request=dev.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply{targets.forEach{addTarget(it)}
    if(stabilize){try{val available=(getSystemService(CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(cameraId).get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf();if(available.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)){set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);stabilizationSupported=true}}catch(_:Exception){}}
   }.build()
   @Suppress("DEPRECATION")
   dev.createCaptureSession(targets,object:CameraCaptureSession.StateCallback(){
    override fun onConfigured(s:CameraCaptureSession){session=s;try{s.setRepeatingRequest(request,null,handler);if(!recording){rec.start();recording=true;running=true;starting=false;phase="RECORDING";update("Recording · tap Stop to save")}}catch(_:Exception){finish()}}
    override fun onConfigureFailed(s:CameraCaptureSession){finish()}
   },handler)
  }catch(_:Exception){finish()}
 }
 private fun begin(interval:Int,requestedHeight:Int,wantAudio:Boolean){
  try{
   val manager=getSystemService(CAMERA_SERVICE) as CameraManager
   cameraId=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==lens}?:error("Camera not available")
   val sizes=manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(MediaRecorder::class.java)?:emptyArray()
   val size=sizes.filter{it.height<=requestedHeight&&it.width<=requestedHeight*2}.maxByOrNull{it.width*it.height}?:sizes.firstOrNull()?:Size(1280,720)
   val dir=getExternalFilesDir(Environment.DIRECTORY_MOVIES)?:filesDir
   dir.mkdirs();output=File(dir,"HDRG_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())+".mp4")
   @Suppress("DEPRECATION")val r=MediaRecorder()
   recorder=r;r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
   val withAudio=wantAudio&&interval==0&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
   if(withAudio)r.setAudioSource(MediaRecorder.AudioSource.MIC)
   r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
   if(interval>0)r.setCaptureRate(1.0/interval)
   r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
   if(withAudio)r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
   r.setVideoSize(size.width,size.height);r.setVideoFrameRate(30)
   r.setVideoEncodingBitRate(if(size.height>=2160)20_000_000 else if(size.height>=1080)8_000_000 else 4_000_000)
   if(withAudio)r.setAudioEncodingBitRate(128_000)
   r.setOutputFile(output!!.absolutePath);r.prepare()
   manager.openCamera(cameraId,object:CameraDevice.StateCallback(){
    override fun onOpened(dev:CameraDevice){camera=dev;configureSession()}
    override fun onDisconnected(dev:CameraDevice){finish()}
    override fun onError(dev:CameraDevice,error:Int){finish()}
   },handler)
  }catch(_:Exception){finish()}
 }
 private fun export(file:File){
  if(!file.exists()||file.length()==0L)return
  if(Build.VERSION.SDK_INT>=29){
   val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,file.name);put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/HDRG Camera");put(MediaStore.Video.Media.IS_PENDING,1)}
   val uri=contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)?:return
   try{contentResolver.openOutputStream(uri)?.use{dst->file.inputStream().use{it.copyTo(dst)}};contentResolver.update(uri,ContentValues().apply{put(MediaStore.Video.Media.IS_PENDING,0)},null,null);file.delete()}catch(_:Exception){contentResolver.delete(uri,null,null)}
  }
 }
 private fun finish(){
  if(finishing)return
  finishing=true;phase="SAVING";starting=false
  try{session?.stopRepeating();session?.abortCaptures()}catch(_:Exception){}
  try{session?.close()}catch(_:Exception){};session=null
  var valid=recording
  if(recording)try{recorder?.stop()}catch(_:Exception){valid=false}
  recording=false;running=false;isPaused=false
  try{recorder?.reset();recorder?.release()}catch(_:Exception){};recorder=null
  try{camera?.close()}catch(_:Exception){};camera=null
  if(valid)output?.let{export(it)}else output?.delete()
  output=null;phase=if(valid)"IDLE" else "ERROR";finishing=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
 }
 override fun onDestroy(){running=false;phase="IDLE";try{session?.close();camera?.close();recorder?.release()}catch(_:Exception){};thread.quitSafely();super.onDestroy()}
}
