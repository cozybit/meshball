package com.samsung.meshball;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import com.samsung.meshball.utils.Log;

import java.io.IOException;
import java.util.List;

/**
 * This class ...
 */
public class ReviewHitActivity extends Activity
{
    private static final String TAG = ReviewHitActivity.class.getName();

    private GridView gridview;
    private ImageButton previousButton;
    private ImageButton nextButton;
    private ImageView reviewImage;
    private ImageView checkMark;

    private int viewingIdx = 0;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.review_player );

        final MeshballApplication app = (MeshballApplication) getApplication();
        app.setReviewing( true );

        gridview = (GridView) findViewById(R.id.player_grid);
        gridview.setAdapter(new PlayerAdapter(this));

        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {

                checkMark.setVisibility( View.VISIBLE );
                Player player = app.getPlayers().get( position );
                Candidate candidate = app.getReviewList().get( viewingIdx );
                candidate.setPlayerID( player.getPlayerID() );
            }
        });

        previousButton = (ImageButton) findViewById( R.id.previous_button );
        nextButton = (ImageButton) findViewById( R.id.next_button );

        if ( app.getReviewList().size() > 1 ) {
            nextButton.setVisibility( View.VISIBLE );
        }

        reviewImage = (ImageView) findViewById( R.id.review_picture );
        Candidate candidate = app.getReviewList().get(0);
        try {
            reviewImage.setImageBitmap( candidate.getBitmap() );
        }
        catch(IOException e) {
            Log.e(TAG, e, "%s - Failed to load candidate bitmap: %s", e.getMessage(), candidate);
        }

        checkMark = (ImageView) findViewById( R.id.check_mark );
        if ( candidate.getPlayerID() != null ) {
            checkMark.setVisibility( View.VISIBLE );
        }
    }

    @Override
    public void onBackPressed()
    {
        MeshballApplication app = (MeshballApplication) getApplication();
        app.setReviewing( false );

        super.onBackPressed();
    }

    public void previousPressed(View v)
    {
        MeshballApplication app = (MeshballApplication) getApplication();
        List<Candidate> reviewList = app.getReviewList();

        Candidate candidate = reviewList.get( --viewingIdx );
        try {
            reviewImage.setImageBitmap( candidate.getBitmap() );
        }
        catch(IOException e) {
            Log.e(TAG, e, "%s - Failed to load candidate bitmap: %s", e.getMessage(), candidate);
        }

        String playerID = candidate.getPlayerID();
        if ( playerID != null ) {
            int idx = app.findPlayer( playerID );
            // Scroll to that grid cell
            gridview.smoothScrollToPosition( idx );
            checkMark.setVisibility( View.VISIBLE );
        }
        else {
            checkMark.setVisibility( View.INVISIBLE );
        }

        if ( viewingIdx == 0 ) {
            previousButton.setVisibility( View.INVISIBLE );
            viewingIdx = 0;
        }

        if ( viewingIdx < (reviewList.size() - 1) ) {
            nextButton.setVisibility( View.VISIBLE );
        }
    }

    public void nextPressed(View v)
    {
        MeshballApplication app = (MeshballApplication) getApplication();
        List<Candidate> reviewList = app.getReviewList();

        Candidate candidate = reviewList.get( ++viewingIdx );
        try {
            reviewImage.setImageBitmap( candidate.getBitmap() );
        }
        catch(IOException e) {
            Log.e(TAG, e, "%s - Failed to load candidate bitmap: %s", e.getMessage(), candidate);
        }

        String playerID = candidate.getPlayerID();
        if ( playerID != null ) {
            int idx = app.findPlayer( playerID );
            // Scroll to that grid cell
            gridview.smoothScrollToPosition( idx );
            checkMark.setVisibility( View.VISIBLE );
        }
        else {
            checkMark.setVisibility( View.INVISIBLE );
        }

        if ( viewingIdx >= (reviewList.size() - 1) ) {
            nextButton.setVisibility( View.INVISIBLE );
            viewingIdx = (reviewList.size() - 1);
        }

        if ( viewingIdx > 0 ) {
            previousButton.setVisibility( View.VISIBLE );
        }
    }

    public void picturePressed(View v)
    {
        MeshballApplication app = (MeshballApplication) getApplication();
        Candidate candidate = app.getReviewList().get( viewingIdx );
        if ( candidate.getPlayerID() != null ) {
            checkMark.setVisibility( View.INVISIBLE );
            candidate.setPlayerID( null );
        }
        else {
            Intent intent = new Intent( this, FullScreenActivity.class );
            intent.putExtra( "index", viewingIdx );
            startActivity( intent );
        }
    }
}