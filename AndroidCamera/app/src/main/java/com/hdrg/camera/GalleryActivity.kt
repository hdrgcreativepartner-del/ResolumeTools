package com.hdrg.camera

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import java.util.concurrent.Executors

class GalleryActivity:Activity(){
 private val worker=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.ArrayBlockingQueue<Runnable>(32),java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy())
 private var player:VideoView?=null
 private var generation=0
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 override fun onCreate(state:Bundle?){super.onCreate(state);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK;showList()}
 private fun showList(){
  player?.stopPlayback();player=null;val token=++generation
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(0xff0a0d12.toInt());setOnApplyWindowInsetsListener{v,i->v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i}}
  root.addView(TextView(this).apply{text="‹   Galeri HDRG Cam";textSize=20f;setTextColor(Color.WHITE);setPadding(dp(20),dp(20),dp(20),dp(20));contentDescription="Kembali ke kamera";setOnClickListener{finish()}})
  val list=ListView(this).apply{divider=null;setPadding(dp(12),0,dp(12),0)}
  val empty=TextView(this).apply{text="Memuat rekaman…";textSize=15f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER;setPadding(dp(24),dp(40),dp(24),dp(40))}
  root.addView(empty);root.addView(list,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
  worker.execute{
   val videos=MediaCatalog.list(this)
   runOnUiThread{
    if(isDestroyed||token!=generation)return@runOnUiThread
    empty.text="Belum ada rekaman.\nVideo dan timelapse Anda akan tampil di sini.";empty.visibility=if(videos.isEmpty())View.VISIBLE else View.GONE
    list.adapter=object:BaseAdapter(){
     override fun getCount()=videos.size
     override fun getItem(position:Int)=videos[position]
     override fun getItemId(position:Int)=position.toLong()
     override fun getView(position:Int,convertView:View?,parent:ViewGroup):View{
      val row=convertView as? LinearLayout?:LinearLayout(this@GalleryActivity).apply{
       gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8))
       addView(ImageView(context).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(0xff202630.toInt())},LinearLayout.LayoutParams(dp(88),dp(64)))
       addView(TextView(context).apply{textSize=14f;setTextColor(Color.WHITE);setPadding(dp(16),0,dp(8),0);maxLines=2},LinearLayout.LayoutParams(0,-2,1f))
      }
      val entry=videos[position];val image=row.getChildAt(0) as ImageView;image.tag=entry.second;image.setImageDrawable(CameraIcon("play"));(row.getChildAt(1) as TextView).text=entry.first
      worker.execute{val bitmap=MediaCatalog.thumbnail(this@GalleryActivity,entry.second,dp(128));runOnUiThread{if(!isDestroyed&&token==generation&&image.tag==entry.second&&bitmap!=null)image.setImageBitmap(bitmap)}}
      row.setOnClickListener{openPlayer(entry.second)};return row
     }
    }
   }
  }
 }
 private fun openPlayer(uri:Uri){
  ++generation;val root=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
  val video=VideoView(this);player=video;root.addView(video,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
  root.addView(TextView(this).apply{text="‹  Galeri";textSize=18f;setTextColor(Color.WHITE);setPadding(dp(24),dp(24),dp(24),dp(24));setOnClickListener{showList()}},FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START))
  setContentView(root);video.setMediaController(MediaController(this).apply{setAnchorView(video)});video.setVideoURI(uri);video.setOnPreparedListener{video.start()}
  video.setOnErrorListener{_,_,_->Toast.makeText(this,"Video tidak dapat diputar",Toast.LENGTH_LONG).show();showList();true}
 }
 @Deprecated("Deprecated in Java") override fun onBackPressed(){if(player!=null)showList()else super.onBackPressed()}
 override fun onPause(){player?.pause();super.onPause()}
 override fun onDestroy(){++generation;player?.stopPlayback();worker.shutdownNow();super.onDestroy()}
}
