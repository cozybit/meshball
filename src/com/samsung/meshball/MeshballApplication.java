package com.samsung.meshball;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import com.samsung.magnet.wrapper.MagnetAgent;
import com.samsung.magnet.wrapper.MagnetAgentImpl;
import com.samsung.meshball.data.Candidate;
import com.samsung.meshball.data.Player;
import com.samsung.meshball.utils.Log;
import com.samsung.meshball.utils.MediaManager;
import com.samsung.meshball.utils.Utils;

import java.io.*;
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

    public static final String MAGNET_SERVICE_ACTION = "com.samsung.magnet.service.MESHBALL";
    public static final String CHANNEL = "com.samsung.meshball";
    public static final String REQUEST_IDENTITY_TYPE = "com.samsung.meshball/request_identity";
    public static final String IDENTITY_TYPE_RES = "com.samsung.meshball/identity";
    public static final String CONFIRMED_HIT_TYPE = "com.samsung.meshball/confirmed_hit";

    public static final String REFRESH = "com.samsung.meshball/refresh";

    private static final int MINUTES = (1000 * 60);

    private MeshballActivity meshballActivity;
    private MagnetAgent magnet;
    private boolean noService = false;
    private boolean noValidSDK = false;
    private boolean reviewing = false;
    private boolean inGame = false;
    private Handler handler = new Handler();

    private Bitmap tempProfileImage;
    private Bitmap profileImage;
    private boolean usingDefaultImage = false;
    private String screenName;
    private boolean firstTime;
    private String playerID = null;
    private int score = 0;

    private long inactivityTimer = 0;

    private List<Candidate> reviewList = new ArrayList<Candidate>();
    private List<Candidate> sendList = new ArrayList<Candidate>();
    private List<Candidate> confirmList = new ArrayList<Candidate>();

    private Map<String, Player> playersMap = new HashMap<String, Player>();
    private List<Player> players = new ArrayList<Player>();
    private Map<String, Player> nodeMap = new HashMap<String, Player>();

    private Random dice = new Random( System.currentTimeMillis() );
    private List<Drawable> splatters = new ArrayList<Drawable>();

    private final Object lock = new Object();

    private Timer timer = new  Timer();
    private TimerTask timerTask = new TimerTask() {

        @Override
        public void run()
        {
            if ( (inactivityTimer != 0) && ((inactivityTimer - System.currentTimeMillis()) < 0) ) {
                // Times up!

                Log.d( TAG, "TIMES UP!!!! Releasing magnet and finishing activity..." );
                releaseService();

                // Clean up!
                players.clear();
                playersMap.clear();
                nodeMap.clear();
                reviewList.clear();
                confirmList.clear();
                sendList.clear();

                if ( meshballActivity != null ) {
                    meshballActivity.finish();
                }
                inactivityTimer = 0;
            }

            // Handle any stuff to review...

            synchronized(lock) {
                Iterator<Candidate> it = sendList.iterator();
                while ( it.hasNext() ) {
                    final Candidate candidate = it.next();
                    if ( candidate.getPlayerID() != null ) {

                        // Let's rename our file to include the player's ID who we claim to have hit...

                        StringBuilder newName = new StringBuilder();
                        newName.append( candidate.getPlayerID() );
                        newName.append(  "." );
                        newName.append( candidate.getFileName() );

                        File from = new File( candidate.getPath(), candidate.getFileName() );
                        final File to = new File( candidate.getPath(), newName.toString() );

                        if ( ! from.renameTo( to ) ) {
                            Log.e( TAG, "Failed to rename %s to %s", from.getAbsolutePath(), to.getAbsolutePath() );
                        }
                        else {
                            magnet.shareFile( null, CHANNEL, null, to.getAbsolutePath(), newName.toString(), new MagnetAgent.MagnetListener()
                            {
                                @Override
                                public void onFailure(int reason)
                                {
                                    // Booooo!!!!
                                    Log.e(TAG, "FAILED to send file: %s, reason = %d", to, reason);
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
            Log.mark(TAG);
            noService = true;
        }

        @Override
        public void onServiceTerminated()
        {
            Log.mark( TAG );
        }

        @Override
        public void onInvalidSdk()
        {
            Log.mark( TAG );
            noValidSDK = true;
        }

        @Override
        public void onWifiConnectivity()
        {
            Log.mark( TAG );
            handleWifiConnect();

            Log.i(TAG, "Attempting to join: %s", CHANNEL);
            joinGame();
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
            Log.mark(TAG);
        }

		@Override
		public void onClosestPeerUpdated(String arg0) 
		{
			Log.mark( TAG );
		}
    };

    private MagnetAgent.ChannelListener channelListener = new MagnetAgent.ChannelListener()
    {
        @Override
        public void onJoinEvent(String fromNode, String fromChannel)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );
            if ( fromChannel.equals( CHANNEL ) ) {
            	// We create a new player with empty PlayerId but valid NodeId
            	Player newPlayer = new Player();
            	newPlayer.setNodeID(fromNode);
            	nodeMap.put(fromNode, newPlayer);
                // A join event will trigger an Identity request
                requestIdentity(fromNode);
            }
        }

        @Override
        public void onLeaveEvent(String fromNode, String fromChannel)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );

            if ( fromChannel.equals( CHANNEL ) ) {
                Player player = nodeMap.get( fromNode );
                nodeMap.remove( fromNode );

                if ( player == null ) {
                    Log.e( TAG, "GOT NULL PLAYER BACK!  fromNode = %s", fromNode );
                    meshballActivity.updateHUD();
                    return;
                }
                
                if (player.getPlayerID() != null) {
	                // Remove them from the game...
	                Log.i( TAG, "Removing player %s from game...", player );
	
	                playersMap.remove( player.getPlayerID() );
	                players.remove( player );
	
	                // Also from the confirm list...
	                Iterator<Candidate> it = confirmList.iterator();
	                while( it.hasNext() ) {
	                    Candidate candidate = it.next();
	                    if ( candidate.getPlayerID().equals( player.getPlayerID() ) ) {
	                        it.remove();
	                    }
	                }
	                // We set a player Id as null so we retry on second attempt
	                player.setPlayerID(null);
                }

                // Broadcast a refresh in case any activity is interested
                Intent intent = new Intent( MeshballApplication.REFRESH );
                intent.putExtra("player_id", player.getPlayerID());
                sendBroadcast(intent);

                if ( meshballActivity != null ) {
                    meshballActivity.showMessage(getString(R.string.has_left, player.getScreenName()));
                }
            }
        }

        @Override
        public void onDataReceived(String fromNode, String fromChannel, String type, List<byte[]> payload)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s", fromNode, fromChannel );
            if ( fromChannel.equals( CHANNEL ) ) {
            	if ( REQUEST_IDENTITY_TYPE.equals( type ) ) {
            		handleRequestIdentity(fromNode);
            	} 
            	else if ( IDENTITY_TYPE_RES.equals( type ) && (payload.size() == 3) ) {
                    handleIdentity(fromNode, payload);
                }
                else if ( CONFIRMED_HIT_TYPE.equals( type ) && (payload.size() == 3) ) {
                    handleConfirmedHit(fromNode, payload);
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

                String[] parts = originalName.split( "\\." );
                if ( parts.length != 4 ) {
                    Log.e( TAG, "Invalid filename format! Expected 4 parts but got: %s", originalName );
                }
                else {
                    String pID = parts[0];
                    String sID = parts[1];

                    Player player = playersMap.get( pID );
                    Player shooter = playersMap.get( sID );

                    // If there are more than two players, then we can not confirm ourselves

                    if ( (playersMap.size() > 2) && playerID.equals( pID ) ) {
                        Log.d( TAG, "Victim %s is our self (%s). Ignoring...", pID, getPlayerID() );
                        return;
                    }

                    if ( (player == null) || (shooter == null) ) {
                        // Perhaps they left already?
                        Log.w( TAG, "Failed to lookup hit player (%s) or shooter (%s) - filename: %s", pID, sID, originalName);
                    }
                    else {
                        Log.d( TAG, "[%s, %s] claims to have hit [%s, %s] : filename: %s",
                               sID, shooter.getScreenName(), pID, player.getScreenName(), tmp_path );
                        handleFile(originalName, tmp_path, sID, pID);
                    }
                }
            }
        }

        @Override
        public void onFileFailed(String fromNode, String fromChannel, String originalName,
                                 String hash, String exchangeId, int reason)
        {
            Log.i( TAG, "fromNode = %s, fromChannel = %s, originalName=%s, hash=%s, exchangeId=%s, reason=%d",
                   fromNode, fromChannel, originalName, hash, exchangeId, reason );
        }

        @Override
        public void onFailure(int reason)
        {
            Log.e(TAG, "Failure on channel.  Reason = %d", reason);
        }
    };

    @Override
    public void onCreate()
    {
        Log.i( TAG, "------------------------ New Meshball ------------------------" );
        super.onCreate();

        // Cleanup all JPG and PNG files except for the profile
        File path = MediaManager.getMediaStoragePath();
        File[] list = path.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file)
            {
                if ( file.getName().equals( PROFILE_FILENAME ) ) {
                    return false;
                }

                String parts[] = file.getName().split( "\\." );
                if ( parts.length > 1 ) {
                    String part = parts[parts.length - 1];
                    if ( ".jpg".equalsIgnoreCase( part ) || ".png".equalsIgnoreCase( part ) ) {
                        return true;
                    }
                }
                return  false;
            }
        });

        Log.i( TAG, "Cleaning up %d files...", list.length );

        for ( File file : list ) {
            if ( ! file.delete() ) {
                Log.w( TAG, "Failed to delete file: %s", file );
            }
        }

        // Add some players

//        for ( int i = 0; i < 10; i++ ) {
//            Drawable d = getResources().getDrawable( R.drawable.missing_profile );
//            Player player = new Player( String.valueOf( i ) );
//            player.setScreenName( "Player #" + i );
//            player.setPicture( ((BitmapDrawable) d).getBitmap() );
//            addPlayer(player);
//        }

        loadPreferences();

        splatters.add( getResources().getDrawable( R.drawable.splat_01 ) );
        splatters.add( getResources().getDrawable( R.drawable.splat_02 ) );
        splatters.add( getResources().getDrawable( R.drawable.splat_03 ) );
        splatters.add( getResources().getDrawable( R.drawable.splat_04 ) );

        Log.i(TAG, "Scheduling timer task...");
        timer.schedule(timerTask, new Date(System.currentTimeMillis()), 250);
    }

    protected void requestIdentity(final String fromNode) {

        Log.mark(TAG);

        if ( ! inGame ) {
            Log.i( TAG, "Not yet in the game - nothing to request." );
            return;
        }

        if ( screenName == null ) {
            Log.i( TAG, "Identity has not been set up yet - nothing to request." );
            return;
        }

        Log.d( TAG, "Requesting identity: %s", screenName );
        magnet.sendData(fromNode, CHANNEL, REQUEST_IDENTITY_TYPE, null, null);
	}

	@Override
    public void onTerminate()
    {
        Log.i(TAG, "Cancelling timer tasks...");
        timer.cancel();

        super.onTerminate();
    }

    public void setMeshballActivity(MeshballActivity meshballActivity)
    {
        this.meshballActivity = meshballActivity;
    }

    public List<Candidate> getReviewList()
    {
        return reviewList;
    }

    public void sendHit(Candidate candidate)
    {
        synchronized ( lock ) {
            sendList.add( candidate );
        }
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
        if ( ! players.contains( player ) && ! player.isMe()) {
            players.add( player );
        }

        handler.post( new Runnable() {
            @Override
            public void run()
            {
                meshballActivity.updateHUD();
            }
        } );
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

    public boolean usingDefaultImage()
    {
        return usingDefaultImage;
    }

    public Bitmap getProfileImage()
    {
        if (profileImage == null) {
            Bitmap image = null;

            try {
                image = MediaManager.loadBitmapImage(PROFILE_FILENAME);
                usingDefaultImage = false;
            }
            catch (IOException e) {
                Log.e(TAG, e, "Caught Exception: %s", e.getMessage());
            }

            // If null, then get the default...
            if (image == null) {
                Resources res = getResources();
                Drawable defaultDrawable = res.getDrawable(R.drawable.missing_profile);
                image = ((BitmapDrawable) defaultDrawable).getBitmap();
                usingDefaultImage = true;
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
        usingDefaultImage = false;
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

    public boolean hasNoService()
    {
        return noService;
    }

    public boolean hasNoValidSDK()
    {
        return noValidSDK;
    }

    public boolean isReviewing()
    {
        return reviewing;
    }

    public boolean inGame()
    {
        return inGame;
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

    public void broadcastIdentity()
    {
        Log.mark(TAG);

        if ( ! inGame ) {
            Log.i( TAG, "Not yet in the game - nothing to broadcast." );
            return;
        }

        if ( screenName == null ) {
            Log.i( TAG, "Identity has not been set up yet - nothing to broadcast." );
            return;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        Bitmap image = getProfileImage();
        image.compress(Bitmap.CompressFormat.PNG, 0, bos);

        List<byte[]> payload = new ArrayList<byte[]>();

        payload.add( getPlayerID().getBytes() );
        payload.add( screenName.getBytes() );
        payload.add( bos.toByteArray() );

        Log.d( TAG, "Broadcasting my identity: %s", screenName );
        magnet.sendData(null, CHANNEL, IDENTITY_TYPE_RES, payload, new MagnetAgent.MagnetListener()
        {
            @Override
            public void onFailure(int reason)
            {
                Log.e(TAG, "Failure broadcasting identity. Reason = %d", reason);
            }
        });
    }

    private void handleWifiConnect()
    {
    }

    private void handleWifiDisconnect()
    {
    }

    private void handleFile(final String originalName, final String tmp_path, final String shooterID, final String playerID)
    {
        final File copyFromPath = new File(tmp_path);
        final File copyToPath = new File(MediaManager.getMediaStoragePath(), originalName);

        AsyncTask<File, Void, Boolean> task = new AsyncTask<File, Void, Boolean>()
        {
            @Override
            protected void onPreExecute()
            {
            }

            @Override
            protected Boolean doInBackground(File... files)
            {
                File sourceFile = files[0];
                File destFile = files[1];

                if((sourceFile == null) || (destFile == null)) {
                    return false;
                }

                try {
                    Utils.copyFile(sourceFile, destFile);
                }
                catch(IOException e) {
                    Log.e(TAG, e, "Failed to copy file: %s", e.getMessage());
                    e.printStackTrace();
                    return false;
                }

                return true;
            }

            @Override
            protected void onPostExecute(Boolean success)
            {
                Candidate candidate = new Candidate( MediaManager.getMediaStoragePath(), originalName );
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
        };
        task.execute(copyFromPath, copyToPath);
    }

    private void handleConfirmedHit(String fromNode, List<byte[]> payload)
    {
        String hitID = new String( payload.get(0) );
        String shooterID = new String( payload.get(1) );
        String reviewerID = new String( payload.get(2) );

        Log.d( TAG, "hitID = %s, shooterID = %s, reviewerID = %s", hitID, shooterID, reviewerID );

        Player player = playersMap.get( hitID );
        Player shooter = playersMap.get( shooterID );

        if ( (player == null) || (shooter == null) ) {
            // One or the other left, so ignore the message
            return;
        }

        player.setHitBy( shooterID );
        player.setReviewedBy( reviewerID );

        // Remove any pending confirmations that I might have in my queues
        Iterator<Candidate> it = confirmList.iterator();
        while ( it.hasNext() ) {
            Candidate candidate = it.next();
            if ( candidate.getPlayerID().equals( hitID ) ) {
                Log.d( TAG, "Removing %s", hitID );
                it.remove();
            }
        }

        // TODO: Add interval tree clock to resolve multiple claims on the hit!!!

        String statusMessage = null;

        // Do I get the credit for the hit?
        if ( shooterID.equals( getPlayerID() ) ) {
            statusMessage = getString( R.string.hit_message_credit, player.getScreenName() );
            score++;
        }
        else {
            // Was it me who was hit?
            if ( hitID.equals( getPlayerID() ) ) {
                Log.d( TAG, "I was hit!" );
                leaveGame();

                Intent intent = new Intent( meshballActivity, GameOverActivity.class );
                intent.putExtra( "screen_name", shooter.getScreenName() );
                meshballActivity.startActivity( intent );
            }
            else {
                // We have duplicate code to remove players.
                // Here, and in the onLeaveEvent

                Log.d( TAG, "%s was hit!", player.getPlayerID() );

                playersMap.remove( player.getPlayerID() );
                players.remove( player );

                // Broadcast a refresh in case any activity is interested
                Intent intent = new Intent( MeshballApplication.REFRESH );
                intent.putExtra("player_id", player.getPlayerID());
                sendBroadcast( intent );

                statusMessage = getString( R.string.hit_message, shooter.getScreenName(), player.getScreenName() );
            }
        }

        if ( (meshballActivity != null) && (statusMessage != null) ) {
            meshballActivity.showMessage(statusMessage);
        }
    }

    private void handleRequestIdentity(String fromNode) {
    	Log.mark(TAG);

        if ( ! inGame ) {
            Log.i( TAG, "Not yet in the game - nothing to broadcast." );
            return;
        }

        if ( screenName == null ) {
            Log.i( TAG, "Identity has not been set up yet - nothing to broadcast." );
            return;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        Bitmap image = getProfileImage();
        image.compress(Bitmap.CompressFormat.PNG, 0, bos);

        List<byte[]> payload = new ArrayList<byte[]>();

        payload.add( getPlayerID().getBytes() );
        payload.add( screenName.getBytes() );
        payload.add( bos.toByteArray() );

        Log.d( TAG, "Responding with my identity: %s", screenName );
        magnet.sendData(fromNode, CHANNEL, IDENTITY_TYPE_RES, payload, null);
    }
    
    private void handleIdentity(String fromNode, List<byte[]> payload)
    {
        String playerID = new String( payload.get(0) );
        String name = new String(payload.get(1));

        Log.d(TAG, "playerID = %s, name = %s", playerID, name);

        // Since node IDs can not be relied on to be stable, we will use a player ID that is
        // generated from the devices telephony details.  Each player generates and publishes their
        // player ID.
        //
        // See getPlayerID()/setPlayerID().

    	Player player = getPlayerByNodeID(fromNode);
    	
    	// We don't have the Player on the nodeMap
    	if (player == null) {
    		player = new Player();
    		player.setNodeID(fromNode);
    	}
    	    	
        player.setPlayerID(playerID);

        addPlayer( player );

        if ( meshballActivity != null ) {
            meshballActivity.showMessage(getString(R.string.has_joined, name));
        }

        // Broadcast a refresh in case any activity is interested
        Intent intent = new Intent( MeshballApplication.REFRESH );
        intent.putExtra("player_id", player.getPlayerID());
        sendBroadcast( intent );

        player.setScreenName(name);

        Log.i(TAG, "Got identity for player: %s [ID %s, Node: %s]", screenName, playerID, fromNode);

        byte[] bytes = payload.get(2);
        player.setPicture(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));

    }

    public void joinGame()
    {
        score = 0;

        players.clear();
        playersMap.clear();
        reviewList.clear();
        confirmList.clear();

        // Make sure we are in our player map correctly (but not in the playerList)
        Player me = new Player( getPlayerID() );
        me.setScreenName( screenName );
        me.setIsPlaying(true);
        me.setPicture(getProfileImage());
        me.setIsMe( true );
        addPlayer(me);

        magnet.joinChannel( CHANNEL, channelListener );
        inGame = true;
    }

    public void leaveGame()
    {
        reviewList.clear();
        confirmList.clear();
        players.clear();
        sendList.clear();
        playersMap.clear();

        score = 0;
        meshballActivity.updateHUD();

        magnet.leaveChannel(CHANNEL);
        inGame = false;
    }

    public void confirmedHit(Candidate beingReviewed)
    {
        Player player = playersMap.get( beingReviewed.getPlayerID() );
        if ( player != null ) {
            player.setHitBy( beingReviewed.getShooterID() );
            player.setReviewedBy( getPlayerID() );

            // Broadcast the confirmed hit!
            List<byte[]> payload = new ArrayList<byte[]>();

            payload.add( beingReviewed.getPlayerID().getBytes() );
            payload.add( beingReviewed.getShooterID().getBytes() );
            payload.add( getPlayerID().getBytes() );

            Log.d( TAG, "Broadcasting confirmed hit on player: %s", player );

            magnet.sendData( null, CHANNEL, CONFIRMED_HIT_TYPE, payload, new MagnetAgent.MagnetListener()
            {
                @Override
                public void onFailure(int reason)
                {
                    Log.e( TAG, "Failure broadcasting confirmed hit. Reason = %d", reason );
                }
            });
        }
    }

    public void releaseService()
    {
        Log.mark( TAG );
        magnet.releaseService();
        magnet = null;

        players.clear();
        playersMap.clear();
        nodeMap.clear();
        sendList.clear();

        score = 0;
    }

    public boolean hasService()
    {
        return (magnet != null);
    }

    public boolean testService()
    {
        if ( magnet == null ) {

            Log.d( TAG, "Magnet is NOT running, creating a new instance..." );

            magnet = new MagnetAgentImpl();
            magnet.initServiceWithCustomAction(MAGNET_SERVICE_ACTION, getApplicationContext(), serviceListener);
            magnet.registerPublicChannelListener(channelListener);

            return true;
        }
        else {
            Log.d( TAG, "Magnet is running..." );
            return false;
        }
    }

    public void becomeInactive()
    {
        inactivityTimer = System.currentTimeMillis() + (30 * MINUTES);
    }

    public void becomeActive()
    {
        inactivityTimer = 0;
    }

    public void getConnectedNodes( MagnetAgent.NodeListListener nodeListListener )
    {
        magnet.getConnectedNodes( CHANNEL, nodeListListener );
    }

    public Player getPlayerByNodeID(String nodeID)
    {
        return nodeMap.get(nodeID);
    }
}

