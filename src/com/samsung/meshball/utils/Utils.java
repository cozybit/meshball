package com.samsung.meshball.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;

/**
 * This class ...
 */
public class Utils
{
    private static final String TAG = Utils.class.getName();

    public static String getMethodName()
    {
        return Thread.currentThread().getStackTrace()[2].getMethodName();
    }

    public static Bitmap decodeUri(InputStream is) throws FileNotFoundException
    {
        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, o);

        // The new size we want to scale to
        final int REQUIRED_SIZE = 200;

        // Find the correct scale value. It should be the power of 2.
        int width_tmp = o.outWidth, height_tmp = o.outHeight;
        int scale = 1;
        while (true) {
            if (width_tmp / 2 < REQUIRED_SIZE || height_tmp / 2 < REQUIRED_SIZE) {
                break;
            }
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }

        // Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        return BitmapFactory.decodeStream(is, null, o2);
    }

    public static int getResId(String variableName, Context context, Class<?> c)
    {
        try {
            Field idField = c.getDeclaredField(variableName);
            return idField.getInt(idField);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature( PackageManager.FEATURE_CAMERA );
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void copyFile(File sourceFile, File destFile) throws IOException
    {
        Log.i( TAG, "sourceFile = %s, destFile = %s", sourceFile, destFile );

        if(!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        }
        finally {
            if(source != null) {
                source.close();
            }
            if(destination != null) {
                destination.close();
            }
        }
    }


}
