package com.hdrg.camera

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import android.util.Size
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class CaptureService : Service() {
 companion object { const val START="HDRG_START"; const val STOP="HDRG_STOP"; const val CHANNEL="capture_channel" }
 private val thread=HandlerThread("HDRGCameraThread").apply{start()}
 private val handler=Handler(thread.looper)
 private var camera:CameraDevice?=null
 private var session:CameraCaptureSession?=null
 private var recorder:MediaRecorder?=null
 private var recording=false
 private var outputFile:File?=null
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"HDRG Camera Recording",NotificationManager.IMPORTANCE_LOW))}
 private fun notification(text:String):Notification{
   val stop=PendingIntent.getService(this,1,Intent(this,CaptureService::class.java).apply{action=STOP},PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
   return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("HDRG Camera · recording").setContentText(text).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Stop recording",stop).build()
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
   if(intent?.action==STOP){handler.post{finishRecording()};return START_NOT_STICKY}
   if(intent?.action==START){
     if(recording || recorder!=null)return START_NOT_STICKY
     try{
       if(Build.VERSION.SDK_INT>=29)startForeground(17,notification("Recording in progress · tap Stop to finish"),ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
       else startForeground(17,notification("Recording in progress"))
       val interval=intent.getIntExtra("interval",0)
       handler.post{begin(interval)}
     }catch(e:Exception){stopSelf()}
   }
   return START_NOT_STICKY
 }
 private fun begin(interval:Int){
   try{
     val manager=getSystemService(CAMERA_SERVICE) as CameraManager
     val id=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK} ?: throw IllegalStateException("Rear camera missing")
     val chars=manager.getCameraCharacteristics(id)
     val sizes=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()
     val size=sizes.filter{it.width<=1920&&it.height<=1080}.maxByOrNull{it.width*it.height} ?: sizes.firstOrNull() ?: Size(1280,720)
     val dir=getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: filesDir
     dir.mkdirs()
     outputFile=File(dir,"HDRG_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())+".mp4")
     @Suppress("DEPRECATION")
     val r=MediaRecorder()
     recorder=r
     r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
     r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
     if(interval>0)r.setCaptureRate(1.0/interval.toDouble())
     r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
     r.setVideoSize(size.width,size.height)
     r.setVideoFrameRate(30)
     r.setVideoEncodingBitRate(5_000_000)
     r.setOutputFile(outputFile!!.absolutePath)
     r.prepare()
     manager.openCamera(id,object:CameraDevice.StateCallback(){
       override fun onOpened(device:CameraDevice){camera=device
         try{
           val target=r.surface
           val req=device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply{addTarget(target);set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)}.build()
           @Suppress("DEPRECATION")
           device.createCaptureSession(listOf(target),object:CameraCaptureSession.StateCallback(){
             override fun onConfigured(s:CameraCaptureSession){session=s;try{s.setRepeatingRequest(req,null,handler);r.start();recording=true}catch(e:Exception){finishRecording()}}
             override fun onConfigureFailed(s:CameraCaptureSession){finishRecording()}
           },handler)
         }catch(e:Exception){finishRecording()}
       }
       override fun onDisconnected(device:CameraDevice){finishRecording()}
       override fun onError(device:CameraDevice,error:Int){finishRecording()}
     },handler)
   }catch(e:Exception){finishRecording()}
 }
 private fun finishRecording(){
   try{session?.stopRepeating();session?.abortCaptures()}catch(_:Exception){}
   try{session?.close()}catch(_:Exception){}
   session=null
   if(recording)try{recorder?.stop()}catch(_:Exception){outputFile?.delete()}
   recording=false
   try{recorder?.reset();recorder?.release()}catch(_:Exception){}
   recorder=null
   try{camera?.close()}catch(_:Exception){}
   camera=null
   stopForeground(STOP_FOREGROUND_REMOVE)
   stopSelf()
 }
 override fun onDestroy(){try{session?.close();camera?.close();recorder?.release()}catch(_:Exception){};thread.quitSafely();super.onDestroy()}
}