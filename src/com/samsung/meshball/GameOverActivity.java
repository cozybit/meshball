package com.samsung.meshball;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * This class ...
 */
public class GameOverActivity
        extends Activity
{
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.game_over_for_you );

        String screenName = getIntent().getStringExtra( "screen_name" );
        TextView textView = (TextView) findViewById( R.id.game_over_shotby );
        textView.setText( getString( R.string.hit_message_self, screenName ) );
    }

    public void playAgainPressed(View view)
    {
        MeshballApplication app = (MeshballApplication) getApplication();
        app.joinGame();
        finish();
    }

    @Override
    public void onBackPressed()
    {
        // Consume it.  If they want to play again, they need to press the button!
    }
}