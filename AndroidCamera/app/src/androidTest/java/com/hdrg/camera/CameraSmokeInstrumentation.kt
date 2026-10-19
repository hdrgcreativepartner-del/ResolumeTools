package com.hdrg.camera

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Bitmap
import android.view.TextureView
import android.view.View
import java.io.File

/** Real camera-session smoke test. A compiled APK alone cannot establish preview health. */
class CameraSmokeInstrumentation : Instrumentation() {
 override fun onCreate(arguments:Bundle?) { super.onCreate(arguments); start() }
 override fun onStart() {
  val result=Bundle()
  try {
   val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
   fun field(name:String):Any { val f=MainActivity::class.java.getDeclaredField(name);f.isAccessible=true;return f.get(activity)!! }
   fun frames():Long { var n=0L;runOnMainSync{n=field("previewFrames") as Long};return n }
   fun waitFor(label:String, predicate:()->Boolean) { val end=SystemClock.elapsedRealtime()+25000;while(!predicate()){check(SystemClock.elapsedRealtime()<end){"Timeout: $label; ${CaptureService.phase}: ${CaptureService.lastError}"};SystemClock.sleep(100)} }
   fun screenshot(name:String) { val bitmap=uiAutomation.takeScreenshot();val f=File(targetContext.getExternalFilesDir(null),"qa/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle() }
   waitFor("first preview frames"){frames()>5}
   runOnMainSync{check((field("preview") as TextureView).isHardwareAccelerated){"TextureView not accelerated"};check((field("shutter") as View).isEnabled)}
   screenshot("01-preview")
   val before=frames();waitFor("moving preview"){frames()>before+5}
   runOnMainSync{(field("shutter") as View).performClick()}
   waitFor("recording"){CaptureService.phase=="RECORDING"}
   val recordingFrames=frames();waitFor("preview during recording"){frames()>recordingFrames+5}
   SystemClock.sleep(3000);screenshot("02-recording")
   runOnMainSync{(field("shutter") as View).performClick()}
   waitFor("recording saved"){CaptureService.phase=="IDLE"}
   val after=frames();waitFor("preview after recording"){frames()>after+5}
   screenshot("03-restored-preview")
   runOnMainSync{(field("lensButton") as View).performClick()}
   val flip=frames();waitFor("front camera preview"){frames()>flip+10}
   screenshot("04-front-camera")
   result.putString("stream","HDRG_SMOKE_PASS: hardware acceleration, live preview, recording, save, restored preview, camera switch\n")
   finish(0,result)
  }catch(t:Throwable){result.putString("stream","HDRG_SMOKE_FAIL: ${t.stackTraceToString()}\n");finish(-1,result)}
 }
}
