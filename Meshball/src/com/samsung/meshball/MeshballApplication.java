package com.samsung.meshball;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import com.samsung.meshball.utils.Log;
import com.samsung.meshball.utils.MediaManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class ...
 */
public class MeshballApplication extends Application
{
    private static final String TAG = MeshballApplication.class.getName();

    private static final String PROFILE_FILENAME = "meshball_profile.png";

    private Bitmap tempProfileImage;
    private Bitmap profileImage;
    private String screenName;
    private boolean firstTime;
    private String playerID = null;

    private List<Integer> hitList = new ArrayList<Integer>();
    private List<Integer> reviewList = new ArrayList<Integer>();

    private Map<Integer, Player> playersMap = new HashMap<Integer, Player>();
    private List<Player> players = new ArrayList<Player>();

    @Override
    public void onCreate()
    {
        Log.i( TAG, "------------------------ New Meshball ------------------------" );
        super.onCreate();

        // Add some players

        for ( int i = 0; i < 10; i++ ) {
            Drawable d = getResources().getDrawable( R.drawable.missing_profile );
            Player player = new Player( i, "Player #" + i, ((BitmapDrawable) d).getBitmap() );
            addPlayer(player);
        }
    }

    @Override
    public void onTerminate()
    {
        Log.mark( TAG );
        super.onTerminate();
    }

    public List<Integer> getHitList()
    {
        return hitList;
    }

    public List<Player> getPlayers()
    {
        return players;
    }

    public Player getPlayer(int playerID)
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

    public void removePlayer(int playerID)
    {
        Player player = playersMap.get( playerID );
        players.remove( player );
        playersMap.remove( playerID );
    }

    public List<Integer> getReviewList()
    {
        return reviewList;
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
}
