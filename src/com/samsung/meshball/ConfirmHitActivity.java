package com.samsung.meshball;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.samsung.meshball.data.Candidate;
import com.samsung.meshball.data.Player;
import com.samsung.meshball.utils.Log;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * This class ...
 */
public class ConfirmHitActivity
        extends Activity
{
    private static final String TAG = ConfirmHitActivity.class.getName();

    private ImageView confirmImage;
    private ImageView withImage;
    private TextView hitMessage;
    private Candidate beingReviewed;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.confirm_hit );

        confirmImage = (ImageView) findViewById( R.id.confirm_image );
        withImage = (ImageView) findViewById( R.id.with_image );
        hitMessage = (TextView) findViewById( R.id.confirm_hit_label );

        nextCandidate();
    }

    private void nextCandidate()
    {
        MeshballApplication app = (MeshballApplication) getApplication();

        // Okay, we need to handle the case of a player leaving while we are reviewing the
        // hits.

        List<Candidate> confirmList = app.getConfirmList();
        Iterator<Candidate> it = confirmList.iterator();
        while( it.hasNext() ) {
            Candidate candidate = it.next();

            beingReviewed = candidate;

            Player player = app.getPlayer( candidate.getPlayerID() );
            Player shooter = app.getPlayer( candidate.getShooterID() );

            if ( (player != null) && (player.getPicture() != null) && (shooter != null) && (shooter.getPicture() != null) ) {
                try {
                    confirmImage.setImageBitmap( candidate.getBitmap() );
                }
                catch(IOException e) {
                    Log.e(TAG, e, "%s - Failed to load candidate bitmap: %s", e.getMessage(), candidate);
                }

                withImage.setImageBitmap( player.getPicture() );
                hitMessage.setText(getString(R.string.hit_lbl_txt, shooter.getScreenName(), player.getScreenName()));
                break;
            }
            Log.e( TAG, "Error getting player back for candidate: %s. Perhaps they left? Removing from list!", candidate);
            it.remove();
        }
    }

    public void confirmHitPressed(View v)
    {
        Log.mark( TAG );

        // Remove player from list, broadcast the confirmed hit, including who hit them
        MeshballApplication app = (MeshballApplication) getApplication();
        app.getConfirmList().remove( beingReviewed );

        app.confirmedHit(beingReviewed);

        if ( ! app.getConfirmList().isEmpty() ) {
            nextCandidate();
        }
        else {
            finish();
        }
    }

    public void badHitPressed(View v)
    {
        Log.mark( TAG );
        MeshballApplication app = (MeshballApplication) getApplication();
        app.getConfirmList().remove( beingReviewed );

        if ( ! app.getConfirmList().isEmpty() ) {
            nextCandidate();
        }
        else {
            finish();
        }
    }

    public void confirmPicturePressed(View v)
    {
        Log.mark( TAG );
    }
}