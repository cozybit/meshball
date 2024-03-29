package com.samsung.meshball;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import com.samsung.meshball.adapters.PlayerAdapter;
import com.samsung.meshball.data.Candidate;
import com.samsung.meshball.data.Player;
import com.samsung.meshball.utils.Log;

import java.io.IOException;
import java.util.List;

/**
 * This class ...
 */
public class ReviewHitActivity extends Activity
{
    private static final String TAG = ReviewHitActivity.class.getName();

    private ImageView reviewImage;
    private ImageView checkMark;
    private TextView remainingText;
    private GridView gridview;
    private PlayerAdapter playerAdapter;

    private Handler handler = new Handler();
    private Runnable runnable = new Runnable()
    {
        @Override
        public void run()
        {
            nextCandidate();
        }
    };

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive( final Context context, Intent intent) {
            Log.d( TAG, "BroadcastReceiver:onReceive : %s", intent );
            String action = intent.getAction();

            if ( action.equalsIgnoreCase( MeshballApplication.REFRESH ) ) {

                MeshballApplication app = (MeshballApplication) getApplication();
                if ( app.getPlayers().isEmpty() || app.getReviewList().isEmpty() ) {
                    ReviewHitActivity.this.finish();
                    return;
                }

                Log.d( TAG, "players.size() = %d", app.getPlayers().size() );

                playerAdapter.notifyDataSetChanged();
                gridview.invalidateViews();
            }
        }
    };

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.review_player);

        final MeshballApplication app = (MeshballApplication) getApplication();
        app.setReviewing( true );

        registerReceiver(broadcastReceiver, new IntentFilter(MeshballApplication.REFRESH));

        playerAdapter = new PlayerAdapter(this);

        gridview = (GridView) findViewById(R.id.player_grid);
        gridview.setAdapter(playerAdapter);

        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id)
            {
                if ( (position < app.getPlayers().size()) ) {
                    Player player = app.getPlayers().get(position);

                    // Check...
                    List<Candidate> reviewList = app.getReviewList();
                    if ( reviewList.size() == 0 ) {
                        Log.w( TAG, "INCONSISTENCY - reviewList.size() == 0" );
                        return;
                    }

                    Candidate candidate = reviewList.get(0);
                    candidate.setPlayerID(player.getPlayerID());
                    app.sendHit(candidate);

                    reviewList.remove( 0 );

                    checkMark.setVisibility(View.VISIBLE);

                    // Delay the update a bit so the user can see the red check mark...
                    handler.postDelayed(runnable, 500);
                }
                else {
                    // Player has left while being selected, make sure the grid view is refreshed to match the adapter.
                    gridview.refreshDrawableState();
                }
            }
        });

        int remainingCnt = app.getReviewList().size() - 1;

        remainingText = (TextView) findViewById( R.id.review_subtext_label );
        remainingText.setText(getString(R.string.review_lbl_subtext, remainingCnt));

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
    protected void onDestroy()
    {
        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }

    @Override
    public void onBackPressed()
    {
        MeshballApplication app = (MeshballApplication) getApplication();
        app.setReviewing( false );

        super.onBackPressed();
    }

    private void nextCandidate()
    {
        MeshballApplication app = (MeshballApplication) getApplication();
        List<Candidate> reviewList = app.getReviewList();
        if ( reviewList.size() == 0 ) {
            app.setReviewing( false );
            finish();
            return;
        }

        remainingText.setText(getString(R.string.review_lbl_subtext, (reviewList.size() - 1)));
        checkMark.setVisibility( View.INVISIBLE );

        Candidate candidate = reviewList.get( 0 );
        try {
            reviewImage.setImageBitmap( candidate.getBitmap() );
        }
        catch(IOException e) {
            Log.e(TAG, e, "%s - Failed to load candidate bitmap: %s", e.getMessage(), candidate);
        }
    }

    public void picturePressed(View v)
    {
        MeshballApplication app = (MeshballApplication) getApplication();
        Candidate candidate = app.getReviewList().get( 0 );
        if ( candidate.getPlayerID() != null ) {
            checkMark.setVisibility( View.INVISIBLE );
            candidate.setPlayerID( null );
        }
        else {
            Intent intent = new Intent( this, FullScreenActivity.class );
            intent.putExtra( "index", 0 );
            startActivity( intent );
        }
    }

    public void rejectPressed(View v)
    {
        MeshballApplication app = (MeshballApplication) getApplication();
        if ( app.getReviewList().size() > 0 ) {
            app.getReviewList().remove(0);
        }
        nextCandidate();
    }
}