import com.hdrg.camera.CameraGeometry
import kotlin.math.abs
fun main(){
 check(CameraGeometry.fit(360,800,1280,720,90,0)==(360 to 640))
 check(CameraGeometry.fit(800,360,1280,720,90,90)==(640 to 360))
 check(CameraGeometry.fit(800,360,1280,720,0,0)==(640 to 360))
 check(CameraGeometry.recordingRotation(90,90,false)==0)
 check(CameraGeometry.recordingRotation(270,90,true)==0)
 var cases=5
 for(sensor in listOf(0,90,180,270))for(display in listOf(0,90,180,270))for((aw,ah) in listOf(360 to 800,800 to 360,320 to 240,1536 to 2048)){
  val(w,h)=CameraGeometry.fit(aw,ah,1920,1080,sensor,display)
  check(w in 1..aw&&h in 1..ah)
  val expected=if((sensor-display+360)%180!=0)1080.0/1920 else 1920.0/1080
  check(abs(w-h*expected)<2)
  check(CameraGeometry.recordingRotation(sensor,display,false) in listOf(0,90,180,270))
  cases++
 }
 println("PASS: $cases camera geometry cases")
}
