package com.hdrg.camera

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Resolution-independent camera icons, drawn without emoji/font glyph fallbacks. */
class CameraIcon(private val kind:String, private val tint:Int=Color.WHITE):Drawable(){
 private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=tint;strokeWidth=2.1f;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
 override fun draw(canvas:Canvas){
  val save=canvas.save()
  val b=bounds
  canvas.translate(b.left.toFloat(),b.top.toFloat())
  canvas.scale(b.width()/24f,b.height()/24f)
  when(kind){
   "grid"->{for(i in 0..2)for(j in 0..2)canvas.drawRect(3f+i*6.5f,3f+j*6.5f,7.5f+i*6.5f,7.5f+j*6.5f,pen)}
   "gallery"->{canvas.drawRoundRect(3f,4f,21f,20f,2f,2f,pen);canvas.drawCircle(8f,9f,1.5f,pen);val p=Path().apply{moveTo(4f,18f);lineTo(10f,12f);lineTo(14f,15f);lineTo(17f,12f);lineTo(21f,17f)};canvas.drawPath(p,pen)}
   "flip"->{canvas.drawArc(3f,3f,21f,21f,205f,145f,false,pen);canvas.drawArc(3f,3f,21f,21f,25f,145f,false,pen);val p=Path().apply{moveTo(18f,4f);lineTo(21f,7f);lineTo(17f,8f);moveTo(6f,20f);lineTo(3f,17f);lineTo(7f,16f)};canvas.drawPath(p,pen)}
   "settings"->{canvas.drawLine(4f,7f,20f,7f,pen);canvas.drawLine(4f,17f,20f,17f,pen);canvas.drawCircle(9f,7f,2.5f,pen);canvas.drawCircle(16f,17f,2.5f,pen)}
   "stabilize"->{canvas.drawRoundRect(6f,4f,18f,20f,3f,3f,pen);canvas.drawLine(3f,8f,3f,16f,pen);canvas.drawLine(21f,8f,21f,16f,pen)}
   "pause"->{pen.style=Paint.Style.FILL;canvas.drawRoundRect(6f,4f,10f,20f,1f,1f,pen);canvas.drawRoundRect(14f,4f,18f,20f,1f,1f,pen);pen.style=Paint.Style.STROKE}
  }
  canvas.restoreToCount(save)
 }
 override fun setAlpha(alpha:Int){pen.alpha=alpha;invalidateSelf()}
 override fun setColorFilter(colorFilter:android.graphics.ColorFilter?){pen.colorFilter=colorFilter;invalidateSelf()}
 @Deprecated("Deprecated in Java")
 override fun getOpacity():Int=PixelFormat.TRANSLUCENT
 override fun getIntrinsicWidth():Int=24
 override fun getIntrinsicHeight():Int=24
}
