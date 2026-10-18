package com.hdrg.camera

import android.app.Activity
import android.content.ContentUris
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.*
import android.widget.*
import android.media.MediaMetadataRetriever

class GalleryActivity:Activity(){
 override fun onCreate(state:Bundle?){super.onCreate(state);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK;showList()}
 private fun showList(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(9,14,23))}
  val bar=TextView(this).apply{text="‹     HDRG  /  GALLERY";textSize=21f;setTextColor(Color.WHITE);setPadding(28,30,20,26);setOnClickListener{finish()}}
  root.addView(bar)
  val scroll=ScrollView(this);val items=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,0,18,20)}
  scroll.addView(items);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
  val videos=mutableListOf<Pair<String,Uri>>()
  if(android.os.Build.VERSION.SDK_INT>=29){
   val cols=arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME)
   val selection="${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
   contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cols,selection,arrayOf("%HDRG Camera%"),"${MediaStore.Video.Media.DATE_ADDED} DESC")?.use{cursor->
    while(cursor.moveToNext()){val id=cursor.getLong(0);videos.add(cursor.getString(1) to ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,id))}
   }
  }else{
   getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.listFiles()?.filter{it.extension=="mp4"}?.sortedByDescending{it.lastModified()}?.forEach{videos.add(it.name to Uri.fromFile(it))}
  }
  if(videos.isEmpty()){items.addView(TextView(this).apply{text="Belum ada rekaman.\nMulai merekam untuk melihat hasil di sini.";textSize=17f;setTextColor(Color.LTGRAY);setPadding(20,90,20,20);gravity=Gravity.CENTER});return}
  videos.forEach{(name,uri)->
   val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(12,10,12,10)}
   val thumb=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(Color.rgb(22,36,56))}
   row.addView(thumb,LinearLayout.LayoutParams(112,95))
   try{val retriever=MediaMetadataRetriever();retriever.setDataSource(this,uri);thumb.setImageBitmap(retriever.getFrameAtTime(0));retriever.release()}catch(_:Exception){}
   row.addView(TextView(this).apply{text=name;setTextColor(Color.WHITE);textSize=14f;setPadding(16,0,8,0)},LinearLayout.LayoutParams(0,-2,1f))
   row.setOnClickListener{openPlayer(uri)}
   items.addView(row)
  }
 }
 private fun openPlayer(uri:Uri){
  val root=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
  val video=VideoView(this)
  root.addView(video,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
  val close=TextView(this).apply{text="‹  Gallery";textSize=18f;setTextColor(Color.WHITE);setPadding(24,24,24,24);setOnClickListener{showList()}}
  root.addView(close,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START))
  setContentView(root)
  video.setVideoURI(uri);video.setMediaController(MediaController(this).apply{setAnchorView(video)})
  video.setOnPreparedListener{video.start()}
 }
}
