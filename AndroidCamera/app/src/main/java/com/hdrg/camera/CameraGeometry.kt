package com.hdrg.camera

/** TextureView already applies sensor orientation. Only compensate display rotation in its matrix. */
object CameraGeometry {
 fun recordingRotation(sensor:Int,display:Int,front:Boolean)=(sensor+(if(front)display else -display)+360)%360
 fun fit(availableWidth:Int,availableHeight:Int,bufferWidth:Int,bufferHeight:Int,sensor:Int,display:Int):Pair<Int,Int>{
  require(availableWidth>0&&availableHeight>0&&bufferWidth>0&&bufferHeight>0)
  val swapped=(sensor-display+360)%180!=0
  val ratio=if(swapped)bufferHeight.toDouble()/bufferWidth else bufferWidth.toDouble()/bufferHeight
  val width=availableWidth.coerceAtMost((availableHeight*ratio).toInt()).coerceAtLeast(1)
  val height=(width/ratio).toInt().coerceIn(1,availableHeight)
  return width to height
 }
}
