package com.hdrg.camera
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class GridOverlay(context:Context):View(context){
 var gridVisible=false;set(v){field=v;invalidate()}
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(135,255,255,255);strokeWidth=1.5f}
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas);if(!gridVisible)return
  val width=width.toFloat();val height=height.toFloat()
  for(i in 1..2){val x=width*i/3f;val y=height*i/3f;canvas.drawLine(x,0f,x,height,paint);canvas.drawLine(0f,y,width,y,paint)}
 }
}
