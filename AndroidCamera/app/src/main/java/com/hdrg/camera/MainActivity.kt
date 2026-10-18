package com.hdrg.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
  private lateinit var status: TextView
  private lateinit var interval: Spinner
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36,56,36,36); setBackgroundColor(Color.rgb(8,16,27)) }
    fun text(value: String, size: Float): TextView = TextView(this).apply { text = value; textSize = size; setTextColor(Color.WHITE); setPadding(0,15,0,15) }
    root.addView(text("HDRG CAMERA",32f));root.addView(text("VIDEO  /  TIMELAPSE",16f))
    root.addView(text("Start recording while the app is visible. Background recording continues with a persistent notification. Camera permission required.",14f))
    interval=Spinner(this)
    val modes=listOf("Normal video","Timelapse · 1 sec","Timelapse · 2 sec","Timelapse · 5 sec","Timelapse · 10 sec","Timelapse · 30 sec")
    interval.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,modes)
    root.addView(interval)
    status=text("Idle · Video saved to Android/data/com.hdrg.camera/files/Movies",14f);root.addView(status)
    val start=Button(this).apply{text="● START RECORDING";setOnClickListener{ startRecord() }}
    val stop=Button(this).apply{text="■ STOP RECORDING";setOnClickListener{ stopRecord() }}
    root.addView(start);root.addView(stop)
    root.addView(text("Recording is visible in Android's foreground service notification. No covert capture or remote uploads.",12f))
    setContentView(root)
    if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),101)
  }
  private fun startRecord(){
    if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),102);status.text="Grant camera permission, then press Start again.";return}
    val intervalSeconds= when(interval.selectedItemPosition){1->1;2->2;3->5;4->10;5->30;else->0}
    val intent=Intent(this,CaptureService::class.java).apply{action=CaptureService.START;putExtra("interval",intervalSeconds)}
    try{ContextCompat.startForegroundService(this,intent);status.text="Starting recording…"}catch(e:Exception){status.text="Cannot start: ${e.message}"}
  }
  private fun stopRecord(){startService(Intent(this,CaptureService::class.java).apply{action=CaptureService.STOP});status.text="Stopping…"}
}