package com.samsung.meshball;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockActivity;

/**
 * This class ...
 */
public class PlayersActivity extends SherlockActivity
{
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player_grid);

        GridView gridview = (GridView) findViewById(R.id.player_grid);
        gridview.setAdapter(new PlayerAdapter(this));

        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Toast.makeText(PlayersActivity.this, "" + position, Toast.LENGTH_SHORT).show();
            }
        });
    }
}