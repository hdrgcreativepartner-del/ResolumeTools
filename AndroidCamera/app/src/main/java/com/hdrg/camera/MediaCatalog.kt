package com.hdrg.camera

import android.content.*
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Size

/** All methods perform disk IO and must be called on a worker. */
object MediaCatalog {
 private val cache=object:android.util.LruCache<String,Bitmap>(8*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
 fun list(context:Context):List<Pair<String,Uri>> {
  val entries=mutableListOf<Pair<String,Uri>>()
  if(Build.VERSION.SDK_INT>=29)try{
   context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME),"${MediaStore.Video.Media.RELATIVE_PATH} = ?",arrayOf("Movies/HDRG Camera/"),"${MediaStore.Video.Media.DATE_ADDED} DESC")?.use{cursor->
    while(cursor.moveToNext()&&!Thread.currentThread().isInterrupted)entries.add(cursor.getString(1) to ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0)))
   }
  }catch(_:Exception){}
  // Keep local recordings visible if MediaStore export failed.
  val names=entries.map{it.first}.toHashSet()
  context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.listFiles()?.filter{it.extension=="mp4"&&it.name !in names}?.sortedByDescending{it.lastModified()}?.forEach{entries.add(it.name to Uri.fromFile(it))}
  return entries
 }
 fun thumbnail(context:Context,uri:Uri,size:Int):Bitmap? {
  val key="$uri@$size";cache.get(key)?.let{return it}
  try {
   if(Build.VERSION.SDK_INT>=29&&uri.scheme=="content")return context.contentResolver.loadThumbnail(uri,Size(size,size),null).also{cache.put(key,it)}
   val retriever=MediaMetadataRetriever()
   try{retriever.setDataSource(context,uri);return retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,size,size)?.also{cache.put(key,it)}}finally{retriever.release()}
  }catch(_:Exception){return null}
 }
}
