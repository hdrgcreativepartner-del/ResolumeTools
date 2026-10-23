package com.hdrg.camera

import android.graphics.*
import android.graphics.drawable.Drawable

/** Constant outer ring; the stop affordance changes only the inner shape. */
class ShutterDrawable(private val recording:Boolean,private val timelapse:Boolean=false):Drawable(){
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 override fun draw(c:Canvas){
  val x=bounds.exactCenterX();val y=bounds.exactCenterY();val radius=bounds.width().coerceAtMost(bounds.height())/2f
  paint.color=Color.WHITE;paint.style=Paint.Style.STROKE;paint.strokeWidth=radius*.08f
  c.drawCircle(x,y,radius*.91f,paint)
  paint.style=Paint.Style.FILL;paint.color=if(timelapse)Color.rgb(83,157,255) else Color.rgb(255,73,86)
  if(recording){val r=radius*.38f;c.drawRoundRect(x-r,y-r,x+r,y+r,r*.25f,r*.25f,paint)}else c.drawCircle(x,y,radius*.76f,paint)
 }
 override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
 override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
 @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
