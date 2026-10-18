package com.hdrg.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest

/** Shared camera parameters, read by preview and copied into the recorder service. */
object ProCamera {
 @Volatile var iso:Int=0
 @Volatile var shutterNs:Long=0L
 @Volatile var focusDiopters:Float=-1f
 fun apply(request:CaptureRequest.Builder, characteristics:CameraCharacteristics, exposureSteps:Int, whiteBalance:Int){
  val manual=characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)==true
  val isoRange=characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
  val exposureRange=characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
  if(manual && iso>0 && shutterNs>0L && isoRange!=null && exposureRange!=null){
   request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF)
   request.set(CaptureRequest.SENSOR_SENSITIVITY,iso.coerceIn(isoRange.lower,isoRange.upper))
   request.set(CaptureRequest.SENSOR_EXPOSURE_TIME,shutterNs.coerceIn(exposureRange.lower,exposureRange.upper))
  }else{
   request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON)
   val compensation=characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
   if(compensation!=null)request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,exposureSteps.coerceIn(compensation.lower,compensation.upper))
  }
  val minFocus=characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?:0f
  if(focusDiopters>=0f && minFocus>0f){
   request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF)
   request.set(CaptureRequest.LENS_FOCUS_DISTANCE,focusDiopters.coerceIn(0f,minFocus))
  }else request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
  val modes=characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
  if(modes?.contains(whiteBalance)==true)request.set(CaptureRequest.CONTROL_AWB_MODE,whiteBalance)
 }
}
