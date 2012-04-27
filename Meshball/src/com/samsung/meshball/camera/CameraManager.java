/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.samsung.meshball.camera;

import android.content.Context;
import android.graphics.*;
import android.hardware.Camera;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import com.samsung.meshball.ViewfinderView;
import com.samsung.meshball.utils.Log;
import com.samsung.meshball.utils.MediaManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager
{
    private static final String TAG = CameraManager.class.getName();

    private static final int MIN_FRAME_WIDTH = 240;
    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_WIDTH = 600;
    private static final int MAX_FRAME_HEIGHT = 400;

    private final Context context;
    private final CameraConfigurationManager configManager;
    private Camera camera;
    private Rect framingRect;
    private Rect framingRectInPreview;
    private boolean initialized;
    private boolean previewing;
    private int requestedFramingRectWidth;
    private int requestedFramingRectHeight;

    private ViewfinderView viewFinder;
    private Bitmap splatter;

    private boolean inShot;

    private Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback()
    {
        @Override
        public void onAutoFocus(boolean success, final Camera camera)
        {
            Log.mark(TAG);
            camera.takePicture( shutterCallback, rawPictureCallback, jpegPictureCallback );
        }
    };

    private Camera.PictureCallback jpegPictureCallback = new Camera.PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            Log.d(TAG, "Size: %d", (data != null ? data.length : 0));

            stopPreview();

            if ( data != null ) {

                viewFinder.setSplatter( splatter );

                Bitmap image = BitmapFactory.decodeByteArray( data, 0, data.length );
                Matrix mat = new Matrix();

                // This is really dumb
                Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                if ( (display.getRotation() == Surface.ROTATION_0) || (display.getRotation() == Surface.ROTATION_270) ) {
                    mat.postRotate(90);
                }

                Bitmap rotatedImage = Bitmap.createBitmap( image, 0, 0, image.getWidth(), image.getHeight(), mat, true );
                Bitmap mutableBitmap = rotatedImage.copy( Bitmap.Config.ARGB_8888, true );

                // Now we need to composite the splatter
                Canvas canvas = new Canvas( mutableBitmap );

                mat = new Matrix();
                mat.setScale( 0.3f, 0.3f );

                Bitmap scaledImage = Bitmap.createBitmap( splatter, 0, 0, splatter.getWidth(), splatter.getHeight(), mat, true );

                float left = (canvas.getWidth() / 2) - (scaledImage.getWidth() / 2);
                float top = (canvas.getHeight() / 2) - (scaledImage.getHeight() / 2);

                canvas.drawBitmap( scaledImage, left, top, null );

                try {
                    Log.i( TAG, "SAVING IMAGE!" );
                    MediaManager.saveBitmapImage(mutableBitmap, "FOOBAR.JPG", 100);
                }
                catch (FileNotFoundException e) {
                    Log.e(TAG, e, "Caught exception while writing profile image: %s", e.getMessage());
                }
                catch (IOException e) {
                    Log.e(TAG, e, "Caught exception while writing profile image: %s", e.getMessage());
                    e.printStackTrace();
                }

                camera.addCallbackBuffer( data );
            }

            inShot = false;
        }
    };

    private Camera.PictureCallback rawPictureCallback = new Camera.PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            Log.d(TAG, "Size: %d", (data != null ? data.length : 0));

            // Ignore the raw picture..
            if ( data != null ) {
                camera.addCallbackBuffer( data );
            }
        }
    };

    private Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter()
        {
            Log.mark( TAG );
        }
    };

    public CameraManager( Context context, ViewfinderView viewFinder )
    {
        this.context = context;
        this.viewFinder = viewFinder;
        this.configManager = new CameraConfigurationManager( context );
    }

    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the camera will draw preview frames into.
     *
     * @throws IOException Indicates the camera driver failed to open.
     */
    public void openDriver( SurfaceHolder holder )
            throws IOException
    {
        Camera theCamera = camera;
        if ( theCamera == null )
        {
            theCamera = Camera.open();
            if ( theCamera == null )
            {
                throw new IOException();
            }
            camera = theCamera;
        }
        theCamera.setPreviewDisplay( holder );

        if ( !initialized )
        {
            initialized = true;
            configManager.initFromCameraParameters( theCamera );
            if ( requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0 )
            {
                setManualFramingRect( requestedFramingRectWidth, requestedFramingRectHeight );
                requestedFramingRectWidth = 0;
                requestedFramingRectHeight = 0;
            }

            int bufferSize = configManager.getRawBufferSize();
            byte[] buffer = new byte[bufferSize];
            camera.addCallbackBuffer(buffer);
        }
        configManager.setDesiredCameraParameters( theCamera );
    }

    /**
     * Closes the camera driver if still in use.
     */
    public void closeDriver()
    {
        Log.mark( TAG );

        if ( camera != null )
        {
            camera.release();
            camera = null;
            // Make sure to clear these each time we close the camera, so that any scanning rect
            // requested by intent is forgotten.
            framingRect = null;
            framingRectInPreview = null;
        }
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    public void startPreview()
    {
        Log.mark( TAG );

        Camera theCamera = camera;
        if ( theCamera != null && !previewing ) {

            Log.d( TAG, "Starting camera preview" );

            theCamera.startPreview();
            previewing = true;
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    public void stopPreview()
    {
        Log.mark( TAG );

        if ( camera != null && previewing )
        {
            Log.d( TAG, "Stopping camera preview" );

            camera.stopPreview();
            previewing = false;
        }
    }

    /**
     * Calculates the framing rect which the UI should draw to show the user where to place the
     * barcode. This target helps with alignment as well as forces the user to hold the device
     * far enough away to ensure the image will be in focus.
     *
     * @return The rectangle to draw on screen in window coordinates.
     */
    public Rect getFramingRect()
    {
        if ( framingRect == null )
        {
            if ( camera == null )
            {
                return null;
            }
            Point screenResolution = configManager.getScreenResolution();
            int width = screenResolution.x * 3 / 4;
            if ( width < MIN_FRAME_WIDTH )
            {
                width = MIN_FRAME_WIDTH;
            }
            else if ( width > MAX_FRAME_WIDTH )
            {
                width = MAX_FRAME_WIDTH;
            }
            int height = screenResolution.y * 3 / 4;
            if ( height < MIN_FRAME_HEIGHT )
            {
                height = MIN_FRAME_HEIGHT;
            }
            else if ( height > MAX_FRAME_HEIGHT )
            {
                height = MAX_FRAME_HEIGHT;
            }
//            if ( context.getResources().getConfiguration().orientation == 1 ) {
//                // Portrait...
//                int leftOffset = (( screenResolution.x - height ) / 2) - 100;
//                int topOffset = ( screenResolution.y - width ) / 2;
//
//                framingRect = new Rect( leftOffset, topOffset, leftOffset + height, topOffset + width );
//            }
//            else {
//                int leftOffset = ( screenResolution.x - width ) / 2;
//                int topOffset = (( screenResolution.y - height ) / 2) - 100;
//
//                framingRect = new Rect( leftOffset, topOffset, leftOffset + width, topOffset + height );
//            }

            int leftOffset = ( screenResolution.x - width ) / 2;
            int topOffset = (( screenResolution.y - height ) / 2) - 100;

            framingRect = new Rect( leftOffset, topOffset, leftOffset + width, topOffset + height );

            Log.d( TAG, "Calculated framing rect: %s", framingRect );
        }
        return framingRect;
    }

    /**
     * Like {@link #getFramingRect} but coordinates are in terms of the preview frame,
     * not UI / screen.
     */
    public Rect getFramingRectInPreview()
    {
        if ( framingRectInPreview == null )
        {
            Rect framingRect = getFramingRect();
            if ( framingRect == null )
            {
                return null;
            }
            Rect rect = new Rect( framingRect );
            Point cameraResolution = configManager.getCameraResolution();
            Point screenResolution = configManager.getScreenResolution();

            rect.left = rect.left * cameraResolution.x / screenResolution.x;
            rect.right = rect.right * cameraResolution.x / screenResolution.x;
            rect.top = rect.top * cameraResolution.y / screenResolution.y;
            rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;

            framingRectInPreview = rect;
        }
        return framingRectInPreview;
    }

    /**
     * Allows third party apps to specify the scanning rectangle dimensions, rather than determine
     * them automatically based on screen resolution.
     *
     * @param width  The width in pixels to scan.
     * @param height The height in pixels to scan.
     */
    public void setManualFramingRect( int width, int height )
    {
        if ( initialized )
        {
            Point screenResolution = configManager.getScreenResolution();
            if ( width > screenResolution.x )
            {
                width = screenResolution.x;
            }
            if ( height > screenResolution.y )
            {
                height = screenResolution.y;
            }
            int leftOffset = ( screenResolution.x - width ) / 2;
            int topOffset = ( screenResolution.y - height ) / 2;
            framingRect = new Rect( leftOffset, topOffset, leftOffset + width, topOffset + height );
            Log.d(TAG, "Calculated manual framing rect: %s", framingRect);
            framingRectInPreview = null;
        }
        else
        {
            requestedFramingRectWidth = width;
            requestedFramingRectHeight = height;
        }
    }

    public void takePicture(Bitmap bitmap)
    {
        Log.i(TAG, "TAKING PICTURE!");

        splatter = bitmap;
        camera.startPreview();

        Camera.Parameters p = camera.getParameters();
        List<String> focusModes = p.getSupportedFocusModes();

        if( (focusModes != null) && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO) ) {
            //Phone supports autofocus! Focus first, then take picture
            camera.autoFocus( autoFocusCallback );
        }
        else {
            //Phone does not support autofocus! Just take the picture
            Log.d( TAG, "PHONE DOES NOT SUPPORT AUTOFOCUS" );
            camera.takePicture( shutterCallback, rawPictureCallback, jpegPictureCallback );        }
    }

    public boolean isInShot()
    {
        return inShot;
    }

    public void setInShot(boolean inShot)
    {
        this.inShot = inShot;
    }
}
