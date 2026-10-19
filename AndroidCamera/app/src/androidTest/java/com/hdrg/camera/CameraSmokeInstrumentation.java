package com.hdrg.camera;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Bitmap;
import android.view.TextureView;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/** Java keeps test helpers independent of the app's deliberately shrunk Kotlin runtime. */
public class CameraSmokeInstrumentation extends Instrumentation {
 private MainActivity activity;
 @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
 private Object field(String name) {
  try { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
  catch(Exception e){throw new RuntimeException(e);}
 }
 private long frames(){long[] n={0};runOnMainSync(()->n[0]=(Long)field("previewFrames"));return n[0];}
 private void waitFor(String label,BooleanSupplier predicate){
  long end=SystemClock.elapsedRealtime()+25000;
  while(!predicate.getAsBoolean()){
   if(SystemClock.elapsedRealtime()>end)throw new AssertionError("Timeout: "+label+"; "+CaptureService.Companion.getPhase()+": "+CaptureService.Companion.getLastError());
   SystemClock.sleep(100);
  }
 }
 private void click(String name){runOnMainSync(()->((View)field(name)).performClick());}
 private void screenshot(String name)throws Exception{
  Bitmap b=getUiAutomation().takeScreenshot();File f=new File(getTargetContext().getExternalFilesDir(null),"qa/"+name+".png");f.getParentFile().mkdirs();
  try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
 }
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   waitFor("first preview frames",()->frames()>5);
   runOnMainSync(()->{if(!((TextureView)field("preview")).isHardwareAccelerated())throw new AssertionError("Preview not accelerated");if(!((View)field("shutter")).isEnabled())throw new AssertionError("Shutter disabled");});
   screenshot("01-preview");long before=frames();waitFor("moving preview",()->frames()>before+5);
   click("shutter");waitFor("recording",()->"RECORDING".equals(CaptureService.Companion.getPhase()));
   long recording=frames();waitFor("preview during recording",()->frames()>recording+5);
   SystemClock.sleep(3000);screenshot("02-recording");click("shutter");
   waitFor("recording saved",()->"IDLE".equals(CaptureService.Companion.getPhase()));
   long after=frames();waitFor("preview after recording",()->frames()>after+5);screenshot("03-restored-preview");
   click("lensButton");long flip=frames();waitFor("front camera preview",()->frames()>flip+10);screenshot("04-front-camera");
   result.putString("stream","HDRG_SMOKE_PASS: hardware acceleration, live preview, recording, save, restored preview, camera switch\n");finish(0,result);
  }catch(Throwable t){StringWriter sw=new StringWriter();t.printStackTrace(new PrintWriter(sw));result.putString("stream","HDRG_SMOKE_FAIL: "+sw+"\n");finish(-1,result);}
 }
}
