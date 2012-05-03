package com.samsung.meshball;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import com.samsung.magnet.wrapper.MagnetAgent;
import com.samsung.magnet.wrapper.MagnetAgentImpl;
import com.samsung.meshball.utils.Log;
import com.samsung.meshball.utils.MediaManager;
import com.samsung.meshball.utils.WifiUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * This class ...
 */
public class MeshballApplication extends Application
{
    private static final String TAG = MeshballApplication.class.getName();

    public static final String PROFILE_FILENAME = "meshball_profile.png";

    public static final String CHANNEL = "com.samsung.meshball";
    public static final String IDENTITY_TYPE = "com.samsung.meshball/identity";

    private MeshballActivity meshballActivity;
    private MagnetAgent magnet;
    private boolean isReady = false;
    private boolean playing = true;
    private boolean reviewing = false;
    private Handler handler = new Handler();

    private Bitmap tempProfileImage;
    private Bitmap profileImage;
    private String screenName;
    private boolean firstTime;
    private String playerID = null;
    private int score = 0;

    private List<Candidate> reviewList = new ArrayList<Candidate>();
    private List<Candidate> confirmList = new ArrayList<Candidate>();

    private Map<String, Player> playersMap = new HashMap<String, Player>();
    private List<Player> players = new ArrayList<Player>();
    private Map<String, Player> nodeMap = new HashMap<String, Player>();

    private Random dice = new Random( System.currentTimeMillis() );
    private List<Drawable> splatters = new ArrayList<Drawable>();

    private WifiUtils wifiUtils;

    private Timer timer = new  Timer();

    private TimerTask timerTask = new TimerTask() {

        @Override
        public void run()
        {
            if ( ! reviewing ) {
                Iterator<Candidate> it = reviewList.iterator();
                while ( it.hasNext() ) {
                    Candidate candidate = it.next();
                    if ( candidate.getPlayerID() != null ) {

                        // Let's rename our file to include the player's ID who we claim to have hit...
                        StringBuilder newName = new StringBuilder();
                        newName.append( candidate.getPlayerID() );
                        newName.append(  "." );
                        newName.append( getPlayerID() );
                        newName.append( ".JPG" );

                        File from = new File( candidate.getPath(), candidate.getFileName() );
                        File to = new File(  candidate.getPath(), newName.toString() );

                        if ( ! from.renameTo( to ) ) {
                            Log.e( TAG, "Failed to rename %s to %s", from, to );
                        }
                        else {
                            magnet.shareFile( null, CHANNEL, null, to.getAbsolutePath(), newName.toString(), new MagnetAgent.MagnetListener()
                            {
                                @Override
                                public void onFailure(int reason)
                                {
                                    Log.e( TAG, "FAILED to send file: reason = %d", reason );
                                    Toast.makeText( getApplicationContext(), R.string.toast_failed_send_file, Toast.LENGTH_LONG ).show();
                                }
                            });
                        }

                        it.remove();
                    }
                }
            }
        }
    };

    private MagnetAgent.MagnetServiceListener serviceListener = new MagnetAgent.MagnetServiceListener()
    {
        @Override
        public void onServiceNotFound()
        {
            Log.mark( TAG );

            String title = getString(R.string.dlg_noservice_title);
            String message = getString(R.string.dlg_noservice_message);

            displayDialog(title, message);
        }

        @Override
        public void onServiceTerminated()
        {
            Log.mark( TAG );

            Toast.makeText(getApplicationContext(),
                           getString(R.string.toast_service_terminated),
                           Toast.LENGTH_LONG).show();
        }

        @Override
        public void onInvalidSdk()
        {
            Log.mark( TAG );

            String title = getString(R.string.dlg_not_valid_sdk_title);
            String message = getString(R.string.dlg_not_valid_sdk_message);

            displayDialog(title, message);
        }

        @Override
        public void onWifiConnectivity()
        {
            Log.mark( TAG );
            handleWifiConnect();

            Log.i(TAG, "Attempting to join: %s", CHANNEL);
            if ( playing ) {
                joinGame();
            }
        }

        @Override
        public void onNoWifiConnectivity()
        {
            Log.mark( TAG );
            handleWifiDisconnect();
        }

        @Override
        public void onMagnetNoPeers()
        {
            Log.mark( TAG );

        }

        @Override
        public void onMagnetPeers()
        {
            Log.mark( TAG );
            magnet.getConnectedNodes( CHANNEL, nodeListListener );
        }
    };

    private MagnetAgent.ChannelListener channelListener = new MagnetAgent.ChannelListener()
    {
        @Override
        public void onJoinEvent(String fromNode, String fromChannel)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );
            if ( fromChannel.equals( CHANNEL ) ) {
                // A join event will trigger a broadcast/shout out
                broadcastIdentity();
            }
        }

        @Override
        public void onLeaveEvent(String fromNode, String fromChannel)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );
            if ( fromChannel.equals( CHANNEL ) ) {
                Player player = nodeMap.get( fromNode );
                if ( player == null ) {
                    Log.e( TAG, "GOT NULL PLAYER BACK!  fromNode = %s", fromNode );
                    return;
                }

                // Remove them from the game...
                Log.i( TAG, "Removing player %s from game...", player );

                nodeMap.remove( fromNode );
                playersMap.remove( player.getPlayerID() );
                players.remove( player );

                Toast.makeText( meshballActivity, getString( R.string.has_left, player.getScreenName()), Toast.LENGTH_LONG ).show();
            }
        }

        @Override
        public void onDataReceived(String fromNode, String fromChannel, String type, List<byte[]> payload)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );
            if ( fromChannel.equals( CHANNEL ) ) {
                if ( IDENTITY_TYPE.equals( type ) && (payload.size() == 4) ) {
                    String playerID = new String( payload.get(0) );
                    String name = new String(payload.get(1));

                    // Since node IDs can not be relied on to be stable, we will use a player ID that is
                    // generated from the devices telephony details.  Each player generates and publishes their
                    // player ID.
                    //
                    // See getPlayerID()/setPlayerID().

                    Player player = playersMap.get( playerID );
                    if ( player == null ) {
                        player = new Player( playerID );
                        addPlayer( player );

                        Toast.makeText( meshballActivity, getString( R.string.has_joined, name), Toast.LENGTH_LONG ).show();
                    }

                    player.setScreenName( name );

                    byte[] flag = payload.get(2);
                    player.setIsPlaying( flag[0] == 1 );

                    Log.i( TAG, "Got identity for player: %s [ID %s, Node: %s]", screenName, playerID, fromNode );

                    byte[] bytes = payload.get(3);
                    player.setPicture( BitmapFactory.decodeByteArray( bytes, 0, bytes.length ) );

                    // Let's still make a mapping with the node ID

                    nodeMap.put( fromNode, player );

                    // TODO: DEBUGGING!!!  Not 100% the fromNode doesnt change on us...
                    if ( (player.getNodeID() != null) && ! player.getNodeID().equals(fromNode) ) {
                        Log.e( TAG, "ERROR: Got a different fromNode for Player ID %s.  Expected %s but got %s", playerID, player.getNodeID(), fromNode);
                    }
                    player.setNodeID( fromNode );
                }
            }
        }

        @Override
        public void onFileNotified(String fromNode, String fromChannel, String originalName,
                                   String hash, String exchangeId, String type, long fileSize, String coreTransactionId)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );
            if ( fromChannel.equals( CHANNEL ) ) {
                magnet.acceptFile( coreTransactionId );
            }
        }

        @Override
        public void onChunkReceived(String fromNode, String fromChannel, String originalName,
                                    String hash, String exchangeId, String type, long fileSize, long offset)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );
            if ( fromChannel.equals( CHANNEL ) ) {
                 //
            }
        }

        @Override
        public void onFileReceived(String fromNode, String fromChannel, String originalName,
                                   String hash, String exchangeId, String type, long fileSize, String tmp_path)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );
            if ( fromChannel.equals( CHANNEL ) ) {
                // Place it in the confirm queue and decode the shooter

                String[] parts = originalName.split( "." );
                if ( parts.length != 3 ) {
                    Log.e( TAG, "Invalid filename format! Expected 3 parts but got: %s", originalName );
                }
                else {
                    String playerID = parts[0];
                    String shooterID = parts[1];

                    Player player = playersMap.get( playerID );
                    Player shooter = playersMap.get( shooterID );

                    if ( (player == null) || (shooter == null) ) {
                        // Perhaps they left already?
                        Log.w( TAG, "Failed to lookup hit player (%s) or shooter (%s) - filename: %s",
                               playerID, shooterID, originalName );
                    }
                    else {
                        Log.d( TAG, "[%s, %s] claims to have hit [%s, %s]", shooterID, shooter.getScreenName(), playerID, player.getScreenName() );

                        File path = new File( tmp_path );
                        Candidate candidate = new Candidate( path, originalName );
                        candidate.setPlayerID( playerID );
                        candidate.setShooterID( shooterID );

                        confirmList.add(candidate);

                        // Need to notify the activity
                        handler.post( new Runnable() {
                            @Override
                            public void run()
                            {
                                meshballActivity.updateHUD();
                            }
                        } );
                    }
                }
            }
        }

        @Override
        public void onFileFailed(String fromNode, String fromChannel, String originalName,
                                 String hash, String exchangeId, int reason)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );
            if ( fromChannel.equals( CHANNEL ) ) {

            }
        }

        @Override
        public void onFailure(int reason)
        {
            Log.w(TAG, "Failure on channel.  Reason = %d", reason);
        }
    };

    private MagnetAgent.NodeListListener nodeListListener = new MagnetAgent.NodeListListener()
    {
        @Override
        public void onResult(String channel, List<String> connectedList)
        {
            if ( channel.equals( CHANNEL ) ) {
                Log.d( TAG, "Connected Users on %s:", channel );
                Log.d( TAG, "=======================================================" );

                for( String nodeID : connectedList ) {
                    Log.d( TAG, "   %s - %s", nodeID, nodeMap.get( nodeID ) );
                }

                // Use this trigger our broadcast/shout out of ourselves!!!
                broadcastIdentity();
            }
        }

        @Override
        public void onFailure(int reason)
        {
            Log.e( TAG, "Failed to get connected nodes. Reason = %d", reason );
        }
    };


    @Override
    public void onCreate()
    {
        Log.i( TAG, "------------------------ New Meshball ------------------------" );
        super.onCreate();

        wifiUtils = new WifiUtils( this );

        magnet = new MagnetAgentImpl();
        magnet.initService(getApplicationContext(), serviceListener);
        magnet.registerPublicChannelListener(channelListener);

        // Add some players

//        for ( int i = 0; i < 10; i++ ) {
//            Drawable d = getResources().getDrawable( R.drawable.missing_profile );
//            Player player = new Player( String.valueOf( i ) );
//            player.setScreenName( "Player #" + i );
//            player.setPicture( ((BitmapDrawable) d).getBitmap() );
//            addPlayer(player);
//        }

        splatters.add( getResources().getDrawable( R.drawable.splat_01 ) );
        splatters.add( getResources().getDrawable( R.drawable.splat_02 ) );
        splatters.add( getResources().getDrawable( R.drawable.splat_03 ) );
        splatters.add( getResources().getDrawable( R.drawable.splat_04 ) );

        Log.i(TAG, "Scheduling timer task...");
        timer.schedule(timerTask, new Date(System.currentTimeMillis()), 250);
    }

    @Override
    public void onTerminate()
    {
        Log.i(TAG, "Cancelling timer tasks...");
        timer.cancel();

        Log.mark( TAG );
        super.onTerminate();
    }

    public MeshballActivity getMeshballActivity()
    {
        return meshballActivity;
    }

    public void setMeshballActivity(MeshballActivity meshballActivity)
    {
        this.meshballActivity = meshballActivity;
    }

    public List<Candidate> getReviewList()
    {
        return reviewList;
    }

    public List<Player> getPlayers()
    {
        return players;
    }

    public Player getPlayer(String playerID)
    {
        return playersMap.get( playerID );
    }

    public void addPlayer(Player player)
    {
        playersMap.put( player.getPlayerID(), player );
        if ( ! players.contains( player ) ) {
            players.add( player );
        }
    }

    public void removePlayer(String playerID)
    {
        Player player = playersMap.get( playerID );
        players.remove( player );
        playersMap.remove( playerID );
    }

    public int findPlayer(String playerID)
    {
        int pos;
        for ( pos = 0; pos < players.size(); pos++ ) {
            Player player = players.get(pos);
            if ( player.getPlayerID().equals( playerID ) ) {
                return pos;
            }
        }

        return 0;
    }

    public List<Candidate> getConfirmList()
    {
        return confirmList;
    }

    public Drawable getRandomSplatter()
    {
        int which = dice.nextInt(splatters.size());
        return splatters.get( which );
    }

    public Bitmap getProfileImage()
    {
        if (profileImage == null) {
            Bitmap image = null;
            try {
                image = MediaManager.loadBitmapImage(PROFILE_FILENAME);
            }
            catch (IOException e) {
                Log.e(TAG, e, "Caught Exception: %s", e.getMessage());
            }

            // If null, then get the default...
            if (image == null) {
                Resources res = getResources();
                Drawable defaultDrawable = res.getDrawable(R.drawable.missing_profile);
                image = ((BitmapDrawable) defaultDrawable).getBitmap();
            }

            profileImage = image;
        }

        return profileImage;
    }

    /**
     * Writes out the profile PNG image at 30% quality to private area, as well as generates a 32x32 thumb nail and
     * then sets the cached thumbnail bitmap.
     *
     * @param image Bitmap of the 200x200 profile image
     */
    public void setProfileImage(Bitmap image)
    {
        Log.mark(TAG);
        /*
         * Write out the profile image
         */
        try {
            MediaManager.saveBitmapImage(image, PROFILE_FILENAME, 40);
        }
        catch (FileNotFoundException e) {
            Log.e(TAG, e, "Caught exception while writing profile image: %s", e.getMessage());
        }
        catch (IOException e) {
            Log.e(TAG, e, "Caught exception while writing profile image: %s", e.getMessage());
            e.printStackTrace();
        }

        this.profileImage = image;
    }

    public Bitmap getTempProfileImage()
    {
        return tempProfileImage;
    }

    public void setTempProfileImage(Bitmap tempProfileImage)
    {
        this.tempProfileImage = tempProfileImage;
    }

    public String getScreenName()
    {
        return screenName;
    }

    public String getHandedNess()
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        return settings.getString(PreferencesActivity.PREF_HANDEDNESS, getString(R.string.right));
    }

    public void setScreenName(String screenName)
    {
        this.screenName = screenName;
    }

    public boolean isFirstTime()
    {
        return firstTime;
    }

    public void setFirstTime(boolean firstTime)
    {
        this.firstTime = firstTime;
    }

    public boolean isPlaying()
    {
        return playing;
    }

    public void setPlaying(boolean playing)
    {
        this.playing = playing;
    }

    public boolean isReviewing()
    {
        return reviewing;
    }

    public void setReviewing(boolean reviewing)
    {
        this.reviewing = reviewing;
    }

    public int getScore()
    {
        return score;
    }

    public void incrementScore()
    {
        score++;
    }

    public void savePreferences()
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();

        editor.putBoolean(PreferencesActivity.PREF_FIRST_TIME, firstTime);
        editor.putString(PreferencesActivity.PREF_SCREENNAME, screenName);

        Log.i(TAG, "savePreferences()");
        Log.i(TAG, "-----------------------------------");
        Log.i(TAG, "     firstTime = %s", (firstTime ? "YES" : "NO"));
        Log.i(TAG, "     screenName = %s", screenName);

        editor.commit();
    }

    public void loadPreferences()
    {
        Log.i(TAG, "loadPreferences()");
        Log.i(TAG, "-----------------------------------");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        firstTime = settings.getBoolean(PreferencesActivity.PREF_FIRST_TIME, true);
        screenName = settings.getString(PreferencesActivity.PREF_SCREENNAME, null);

        String handedness = settings.getString(PreferencesActivity.PREF_HANDEDNESS, null);

        Log.i(TAG, "     firstTime = %s", (firstTime ? "YES" : "NO"));
        Log.i(TAG, "     screenName = %s", screenName);
        Log.i(TAG, "     handedness = %s", handedness);
    }

    public String getPlayerID()
    {
        if ( playerID == null ) {
            setPlayerID();
        }
        return playerID;
    }

    private void setPlayerID()
    {
        TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);

        StringBuilder builder = new StringBuilder();

        builder.append(tm.getDeviceId() != null ? tm.getDeviceId() : "");
        builder.append(Build.SERIAL);
        builder.append("com.samsung.meshball");

        String id = builder.toString();

        // compute md5
        MessageDigest m;
        try {
            m = MessageDigest.getInstance("MD5");
            m.update(id.getBytes(), 0, id.length());

            // get md5 bytes
            byte md5Data[] = m.digest();

            // create a hex string
            String szUniqueID = "";
            for (byte aMd5Data : md5Data) {
                int b = (0xFF & aMd5Data);
                // if it is a single digit, make sure it have 0 in front (proper padding)
                if (b <= 0xF) {
                    szUniqueID += "0";
                }
                // add number to string
                szUniqueID += Integer.toHexString(b);
            }
            playerID = szUniqueID;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, e, "Caught Exception: " + e.getMessage());
            e.printStackTrace();
        }

        Log.i(TAG, "Player ID = %s [UNIQUE ID: %s]", playerID, id);
    }

    private void displayDialog(String title, String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(meshballActivity);
        builder.setMessage(message)
                .setTitle(title)
                .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        //
                    }
                });

        builder.show();
    }

    public void broadcastIdentity()
    {
        Log.mark(TAG);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        Bitmap image = getProfileImage();
        image.compress( Bitmap.CompressFormat.PNG, 0, bos );

        List<byte[]> payload = new ArrayList<byte[]>();

        payload.add( getPlayerID().getBytes() );
        payload.add( screenName.getBytes() );
        payload.add( (playing ? new byte[] {1} : new byte[] {0}) );
        payload.add( bos.toByteArray() );

        magnet.sendData( null, CHANNEL, IDENTITY_TYPE, payload, new MagnetAgent.MagnetListener()
        {
            @Override
            public void onFailure(int reason)
            {
                Log.e( TAG, "Failure broadcasting identity. Reason = %d", reason );
            }
        });
    }

    private void handleWifiConnect()
    {
        isReady = true;

        String ssid = wifiUtils.getWifiSSID();
        boolean apMode = wifiUtils.isWifiApEnabled();

    }

    private void handleWifiDisconnect()
    {

    }

    public WifiUtils getWifiUtils()
    {
        return wifiUtils;
    }

    public void joinGame()
    {
        magnet.joinChannel( CHANNEL, channelListener );
        playing = true;
    }

    public void leaveGame()
    {
        reviewList.clear();
        confirmList.clear();

        score = 0;
        meshballActivity.updateHUD();

        magnet.leaveChannel( CHANNEL );
        playing = false;
    }
}
