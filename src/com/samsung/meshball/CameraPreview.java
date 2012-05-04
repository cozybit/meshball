/*
 * Copyright (C) 2012, Wobblesoft LLC, All rights reserved.
 */
package com.samsung.meshball;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.samsung.meshball.utils.Log;

import java.io.IOException;

/**
 * This class ...
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback
{
    private static final String TAG = CameraPreview.class.getName();

    private Camera camera;

    public CameraPreview(Context context, Camera camera)
    {
        super(context);
        this.camera = camera;

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
    }

    public Camera getCamera()
    {
        return camera;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        try {
            if ( camera != null ) {
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            }
        }
        catch (IOException e) {
            Log.d(TAG, e, "Error setting camera preview: ", e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        if ( (holder == null) || (camera == null) ) {
            return;
        }

        try {
            camera.stopPreview();
        }
        catch ( Exception e ) {
            // Ignore.  Tried to stop a non-existent preview
        }

        try {
            camera.setPreviewDisplay( holder );
            camera.startPreview();
        }
        catch(IOException e) {
            Log.d(TAG, e, "Error starting camera preview: ", e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        // Empty - Release the camera preview in the main activity
    }

    public void startPreview()
    {
        if ( camera != null ) {
            camera.startPreview();
        }
    }

    public void stopPreview()
    {
        if ( camera != null ) {
            try {
                camera.stopPreview();
            }
            catch ( Exception e ) {
                // Ignore.  Tried to stop a non-existent preview
            }
        }
    }

    public void releaseCamera()
    {
        if ( camera != null ) {
            camera.release();
            camera = null;
        }
    }
}
