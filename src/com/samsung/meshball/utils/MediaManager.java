package com.samsung.meshball.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This class ...
 */
public class MediaManager
{
    private static final String TAG = MediaManager.class.getName();

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    /**
     * Create a file Uri for saving an image or video
     * @param type
     * @return The new output <code>Uri</code>
     * */
    public static Uri getOutputMediaFileUri(int type)
    {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * Create a File for saving an image or video
     * @param type
     * @return The new output <code>File</code>
     * */
    public static File getOutputMediaFile(int type)
    {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = getMediaStoragePath();

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp + ".jpg");
        }
        else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_"+ timeStamp + ".mp4");
        }
        else {
            return null;
        }

        return mediaFile;
    }

    public static void saveBitmapImage(Bitmap bitmap, Uri uri, int quality)
            throws IOException
    {
        Log.i( TAG, "Uri = %s, quality = %d", uri, quality );
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(uri.getPath());
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
        }
        finally {
            if(fos != null) {
                fos.close();
            }
        }
    }
    
    public static void saveBitmapImage(Bitmap bitmap, File path, String name, int quality)
            throws IOException
    {
        saveBitmapImage(bitmap, Uri.parse(path.getPath() + File.separator + name), quality);
    }

    public static void saveBitmapImage(Bitmap bitmap, String name, int quality)
            throws IOException
    {
        File path = getMediaStoragePath();
        saveBitmapImage(bitmap, Uri.parse(path.getPath() + File.separator + name), quality);
    }

    public static File getMediaStoragePath()
    {
        File mediaStorageDir = new File( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Meshball");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()) {
            if (! mediaStorageDir.mkdirs()) {
                Log.w(TAG, "failed to create directory: %s", mediaStorageDir.getAbsolutePath());
                return null;
            }
        }

        return mediaStorageDir;
    }

    public static Bitmap loadBitmapImage(File file)
            throws IOException
    {
        return loadBitmapImage(Uri.parse(file.getAbsolutePath()));
    }

    public static Bitmap loadBitmapImage(String name)
            throws IOException
    {
        File path = getMediaStoragePath();
        if ( path == null ) {
            return null;
        }
        return loadBitmapImage(Uri.parse(path.getPath() + File.separator + name));        
    }

    public static Bitmap loadBitmapImage(Uri uri)
            throws IOException
    {
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(uri.getPath());
            return BitmapFactory.decodeStream( fis );
        }
        finally {
            if(fis != null) {
                fis.close();
            }
        }
    }
}