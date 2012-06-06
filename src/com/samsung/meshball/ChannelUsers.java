package com.samsung.meshball;

import android.app.ListActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.samsung.magnet.wrapper.MagnetAgent;
import com.samsung.meshball.data.Player;
import com.samsung.meshball.utils.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * This class ...
 */
public class ChannelUsers
        extends ListActivity
{
    private static final String TAG = ChannelUsers.class.getName();
    private ArrayAdapter<String> adapter;
    private List<String> results = new ArrayList<String>();

    private MagnetAgent.NodeListListener nodeListListener = new MagnetAgent.NodeListListener()
    {
        @Override
        public void onResult(String channel, List<String> connectedList)
        {
            MeshballApplication app = (MeshballApplication) getApplication();

            if ( channel.equals( MeshballApplication.CHANNEL ) ) {
                Log.d(TAG, "Connected Users on %s:", channel);
                Log.d(TAG, "=======================================================");

                results.clear();

                for( String nodeID : connectedList ) {
                    StringBuilder builder = new StringBuilder();
                    builder.append( nodeID );
                    builder.append(  " : " );

                    Player player = app.getPlayerByNodeID(nodeID);
                    if ( player != null ) {
                        builder.append( player.getScreenName() );
                    }
                    else {
                        builder.append( "(MISSING)" );
                    }
                    results.add( builder.toString() );
                }

                adapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onFailure(int reason)
        {
            Log.e(TAG, "Failed to get connected nodes. Reason = %d", reason);
        }
    };

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        MeshballApplication app = (MeshballApplication) getApplication();
        app.getConnectedNodes(nodeListListener);

        adapter = new ArrayAdapter<String>( getApplicationContext(), R.layout.list_item, results );
        setListAdapter( adapter );

        ListView lv = getListView();
        lv.setTextFilterEnabled(true);
    }
}