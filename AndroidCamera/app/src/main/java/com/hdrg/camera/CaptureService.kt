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
 companion object{const val START="HDRG_START";const val STOP="HDRG_STOP";const val PAUSE="HDRG_PAUSE";const val RESUME="HDRG_RESUME";const val CHANNEL="hdrg_camera_capture"}
 private val thread=HandlerThread("HDRGRecord").apply{start()}
 private val handler=Handler(thread.looper)
 private var recorder:MediaRecorder?=null
 private var camera:CameraDevice?=null
 private var session:CameraCaptureSession?=null
 private var output:File?=null
 private var recording=false
 private var paused=false
 private var starting=false
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"HDRG Camera Recording",NotificationManager.IMPORTANCE_LOW))}
 private fun notification(label:String):Notification{
  val stop=PendingIntent.getService(this,7,Intent(this,CaptureService::class.java).apply{action=STOP},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("HDRG Camera").setContentText(label).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Stop",stop).build()
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  when(intent?.action){
    STOP->handler.post{finish()}
    PAUSE->handler.post{if(recording&&!paused)try{recorder?.pause();paused=true;updateNotice("Recording paused · tap Stop to save")}catch(_:Exception){}}
    RESUME->handler.post{if(recording&&paused)try{recorder?.resume();paused=false;updateNotice("Recording video · tap Stop to save")}catch(_:Exception){}}
    START->{
      if(recording||starting)return START_NOT_STICKY
      starting=true
      val audio=intent.getBooleanExtra("audio",false)
      val type=if(audio)ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
      try{
       if(Build.VERSION.SDK_INT>=29)startForeground(17,notification("Preparing recording…"),type)
       else startForeground(17,notification("Preparing recording…"))
       handler.post{begin(intent.getIntExtra("interval",0),intent.getIntExtra("height",1080),audio)}
      }catch(_:Exception){starting=false;stopSelf()}
    }
  }
  return START_NOT_STICKY
 }
 private fun updateNotice(label:String){(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(17,notification(label))}
 private fun begin(interval:Int,requestedHeight:Int,wantAudio:Boolean){
  try{
   val manager=getSystemService(CAMERA_SERVICE) as CameraManager
   val id=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK}?:error("No rear camera")
   val candidates=manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()
   val size=candidates.filter{it.height<=requestedHeight&&it.width<=requestedHeight*2}.maxByOrNull{it.width*it.height}
      ?:candidates.minByOrNull{it.width*it.height} ?:Size(1280,720)
   val dir=getExternalFilesDir(Environment.DIRECTORY_MOVIES)?:filesDir
   dir.mkdirs()
   output=File(dir,"HDRG_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())+".mp4")
   @Suppress("DEPRECATION") val rec=MediaRecorder()
   recorder=rec
   rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
   val withAudio=wantAudio&&interval==0&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
   if(withAudio)rec.setAudioSource(MediaRecorder.AudioSource.MIC)
   rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
   if(interval>0)rec.setCaptureRate(1.0/interval)
   rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
   if(withAudio)rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
   rec.setVideoSize(size.width,size.height)
   rec.setVideoFrameRate(30)
   rec.setVideoEncodingBitRate(if(size.height>=2160)20_000_000 else if(size.height>=1080)8_000_000 else 4_000_000)
   if(withAudio)rec.setAudioEncodingBitRate(128_000)
   rec.setOutputFile(output!!.absolutePath)
   rec.prepare()
   manager.openCamera(id,object:CameraDevice.StateCallback(){
    override fun onOpened(dev:CameraDevice){
     camera=dev
     try{
      val surface=rec.surface
      val request=dev.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply{addTarget(surface)}.build()
      @Suppress("DEPRECATION")
      dev.createCaptureSession(listOf(surface),object:CameraCaptureSession.StateCallback(){
       override fun onConfigured(s:CameraCaptureSession){
        session=s
        try{s.setRepeatingRequest(request,null,handler);rec.start();recording=true;starting=false;updateNotice("Recording video · tap Stop to save")}catch(_:Exception){finish()}
       }
       override fun onConfigureFailed(s:CameraCaptureSession){finish()}
      },handler)
     }catch(_:Exception){finish()}
    }
    override fun onDisconnected(dev:CameraDevice){finish()}
    override fun onError(dev:CameraDevice,error:Int){finish()}
   },handler)
  }catch(_:Exception){finish()}
 }
 private fun saveGallery(file:File){
  if(!file.exists()||file.length()==0L)return
  if(Build.VERSION.SDK_INT>=29){
    val resolver=contentResolver
    val values=ContentValues().apply{
      put(MediaStore.Video.Media.DISPLAY_NAME,file.name)
      put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
      put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/HDRG Camera")
      put(MediaStore.Video.Media.IS_PENDING,1)
    }
    val uri=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)?:return
    try{
      resolver.openOutputStream(uri)?.use{dest->file.inputStream().use{it.copyTo(dest)}}
      resolver.update(uri,ContentValues().apply{put(MediaStore.Video.Media.IS_PENDING,0)},null,null)
      file.delete()
    }catch(e:Exception){resolver.delete(uri,null,null)}
  }
  // Android 8/9: file remains in app-specific Movies directory (no broad storage permissions).
 }
 private fun finish(){
  starting=false
  try{session?.stopRepeating();session?.abortCaptures()}catch(_:Exception){}
  try{session?.close()}catch(_:Exception){}
  session=null
  var valid=recording
  if(recording)try{recorder?.stop()}catch(_:Exception){valid=false}
  recording=false;paused=false
  try{recorder?.reset();recorder?.release()}catch(_:Exception){}
  recorder=null
  try{camera?.close()}catch(_:Exception){}
  camera=null
  if(valid)output?.let{saveGallery(it)}else output?.delete()
  output=null
  stopForeground(STOP_FOREGROUND_REMOVE)
  stopSelf()
 }
 override fun onDestroy(){try{session?.close();camera?.close();recorder?.release()}catch(_:Exception){};thread.quitSafely();super.onDestroy()}
}
