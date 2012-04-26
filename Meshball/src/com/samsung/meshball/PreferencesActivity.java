package com.samsung.meshball;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

/**
 * This class ...
 */
public class PreferencesActivity extends PreferenceActivity
{
    private static final String TAG = PreferencesActivity.class.getName();

    public static final String PREF_REVERSE_IMAGE = "meshball.reverse_image";
    public static final String PREF_AUDIO = "meshball.audio";
    public static final String PREF_HANDEDNESS = "meshball.handedness";
    public static final String PREF_VIBRATE = "meshball.vibrate";
    public static final String PREF_FRONT_LIGHT = "meshball.front_light";

    public static final String PREF_FIRST_TIME = "meshball.first_time";
    public static final String PREF_SCREENNAME = "meshball.screenname";

    public void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        String currentHandedness = settings.getString( PREF_HANDEDNESS, "Right" );
        ListPreference listPreference = (ListPreference) findPreference(PREF_HANDEDNESS);
        listPreference.setTitle(getString(R.string.preferences_handedness, currentHandedness));

        listPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object object)
            {
                preference.setTitle(getString(R.string.preferences_handedness, object));
                return true;
            }
        });

    }
}