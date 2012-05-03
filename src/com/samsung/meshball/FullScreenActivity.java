package com.samsung.meshball;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import com.samsung.meshball.utils.Log;

import java.io.IOException;

/**
 * This class ...
 */
public class FullScreenActivity
        extends Activity
{
    private static final String TAG = FullScreenActivity.class.getName();

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.fullscreen_review );

        Intent intent = getIntent();
        int index = intent.getIntExtra( "index", 0 );

        MeshballApplication app = (MeshballApplication) getApplication();
        Candidate candidate = app.getReviewList().get( index );

        ImageView imageView = (ImageView) findViewById( R.id.fullscreen_image );
        try {
            imageView.setImageBitmap( candidate.getBitmap() );
        }
        catch(IOException e) {
            Log.e(TAG, e, "%s - Failed to get candidate bitmap: %s", e.getMessage(), candidate);
        }
    }
}