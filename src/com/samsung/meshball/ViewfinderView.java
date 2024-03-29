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

package com.samsung.meshball;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.samsung.meshball.utils.Log;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView
        extends View
{
    private static final String TAG = ViewfinderView.class.getName();

    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int POINT_SIZE = 6;

    private MeshballActivity meshballActivity;
    private final Paint paint;
    private final int maskColor;
    private final int frameColor;
    private final int laserColor;
    private int scannerAlpha;

    private Bitmap splatter;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        frameColor = resources.getColor(R.color.viewfinder_frame);
        laserColor = resources.getColor(R.color.viewfinder_laser);
        scannerAlpha = 0;
        splatter = null;
    }

    public void setMeshballActivity(MeshballActivity meshballActivity)
    {
        this.meshballActivity = meshballActivity;
    }

    @Override
    public void onDraw(Canvas canvas)
    {
        if(meshballActivity == null) {
            Log.d( TAG, "meshballActivity is NULL!" );
            return;
        }
        Rect frame = meshballActivity.getFramingRect();
        if(frame == null) {
            Log.d( TAG, "frame is NULL!" );
            return;
        }

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        // Are we in a fire state?
        if ( splatter != null ) {

            float left = (width / 2) - (splatter.getWidth() / 2);
            float top = (height / 2) - (splatter.getHeight() / 2);

            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap( splatter, left, top, paint );
        }

        // Draw a two pixel solid black border inside the framing rect
        paint.setColor(frameColor);
        canvas.drawRect(frame.left, frame.top, frame.right + 1, frame.top + 2, paint);
        canvas.drawRect(frame.left, frame.top + 2, frame.left + 2, frame.bottom - 1, paint);
        canvas.drawRect(frame.right - 1, frame.top, frame.right + 1, frame.bottom - 1, paint);
        canvas.drawRect(frame.left, frame.bottom - 1, frame.right + 1, frame.bottom + 1, paint);

        // Draw a red "laser scanner" line through the middle to show decoding is active
        paint.setColor(laserColor);
        paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
        scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
        int middle = frame.height() / 2 + frame.top;
        canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, paint);

        middle = frame.width() / 2 + frame.left;
        canvas.drawRect(middle - 1, frame.top + 2, middle + 2, frame.bottom - 1, paint);

        // Request another update at the animation interval, but only repaint the laser line,
        // not the entire viewfinder mask.
        postInvalidateDelayed(ANIMATION_DELAY,
                              frame.left - POINT_SIZE,
                              frame.top - POINT_SIZE,
                              frame.right + POINT_SIZE,
                              frame.bottom + POINT_SIZE);
    }

    public void setSplatter(Bitmap splatter)
    {
        this.splatter = splatter;
    }

    public void clearSplatter()
    {
        splatter = null;
        invalidate();
    }
}
