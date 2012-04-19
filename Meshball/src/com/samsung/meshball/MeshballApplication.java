package com.samsung.meshball;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import com.samsung.meshball.utils.Log;
import com.samsung.meshball.utils.MediaManager;

import java.io.FileNotFoundException;
import java.io.IOException;

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

        Log.i(TAG, "     firstTime = %s", (firstTime ? "YES" : "NO"));
        Log.i(TAG, "     screenName = %s", screenName);
    }
}
