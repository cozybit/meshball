package com.samsung.meshball;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;
import android.widget.TextView;
import com.samsung.meshball.adapters.PlayerAdapter;

/**
 * This class ...
 */
public class PlayersActivity extends Activity
{
    private AlertDialog alert;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player_grid);

        MeshballApplication app = (MeshballApplication) getApplication();
        if ( app.getPlayers().size() == 0 ) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dlg_no_one_else_title)
                    .setTitle(R.string.players)
                    .setPositiveButton( R.string.okay, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i)
                        {
                            //
                        }
                    })
                    .setCancelable(true);
            alert = builder.create();
            alert.show();

            TextView noPlayersLabel = (TextView) findViewById( R.id.no_players_text );
            noPlayersLabel.setVisibility( View.VISIBLE );
        }

        GridView gridview = (GridView) findViewById(R.id.player_grid);
        gridview.setAdapter(new PlayerAdapter(this));
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if ( alert != null ) {
            alert.dismiss();
        }
    }
}