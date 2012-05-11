package com.samsung.meshball;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import com.samsung.meshball.data.Player;
import com.samsung.meshball.utils.Log;

/**
 * This class ...
 */
public class ProfileActivity
        extends Activity
{
    private static final String TAG = ProfileActivity.class.getName();

    public static final String PROFILE_IMAGE = "com.samsung.meshball.PROFILE_IMAGE";

    private boolean pictureSet = true;

    private BroadcastReceiver profileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive( final Context context, Intent intent) {
            Log.d(TAG, "BroadcastReceiver:onReceive : %1$s", intent);
            String action = intent.getAction();

            MeshballApplication app = (MeshballApplication) getApplication();

            if ( action.equalsIgnoreCase( PROFILE_IMAGE ) ) {
                final Bitmap profileImage = app.getTempProfileImage();
                if ( profileImage != null ) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            ImageView profileImageView = (ImageView) findViewById( R.id.profile_imageview );
                            profileImageView.setImageBitmap( profileImage );
                            pictureSet = true;
                        }
                    });
                }
            }
        }
    };

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.profile );

        registerReceiver(profileReceiver, new IntentFilter(PROFILE_IMAGE));

        TextView versionLabel = (TextView) findViewById( R.id.version_label );
        CharSequence format = versionLabel.getText();

        String versionName = null;
        try {
            versionName = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
        }
        catch(PackageManager.NameNotFoundException e) {
            Log.e(TAG, e, "Caught: %s", e.getMessage());
            e.printStackTrace();
        }
        versionLabel.setText( String.format( format.toString(), versionName ) );

        final MeshballApplication app = (MeshballApplication) getApplication();

        if ( app.isFirstTime() ) {
            pictureSet = ! app.usingDefaultImage();
        }

        Button done = (Button) findViewById( R.id.update_button );
        done.setOnClickListener( new Button.OnClickListener() {

            @Override
            public void onClick( View view )
            {
                Log.mark( TAG );
                EditText displayNameText = (EditText) findViewById( R.id.screenname );
                String screenName = displayNameText.getText().toString();
                if ( validateDisplayName(screenName) && pictureSet ) {

                    Bitmap profileImage = app.getTempProfileImage();
                    if ( profileImage != null ) {
                        app.setProfileImage( profileImage );
                        app.setTempProfileImage( null );
                    }

                    app.setScreenName( screenName );

                    // If the new install was from an invitation...

                    if ( app.isFirstTime() ) {
                        app.setFirstTime( false );

                        Player me = new Player( app.getPlayerID() );
                        me.setScreenName( screenName );
                        me.setIsPlaying( true );
                        me.setPicture( app.getProfileImage() );
                        me.setIsMe( true );
                        app.addPlayer( me );
                    }

                    app.savePreferences();
                    app.broadcastIdentity();

                    finish();
                }
                else {
                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which){
                                case DialogInterface.BUTTON_POSITIVE:
                                    //Yes button clicked
                                    break;

                                case DialogInterface.BUTTON_NEGATIVE:
                                    //No button clicked
                                    break;
                            }
                        }
                    };

                    String message = getString( R.string.dlg_displayname_text );
                    String title = getString( R.string.dlg_displayname_title );
                    String buttonTitle = getString( R.string.okay );

                    AlertDialog.Builder builder = new AlertDialog.Builder(ProfileActivity.this);
                    builder.setMessage(message).setTitle(title).setPositiveButton(buttonTitle, dialogClickListener);
                    AlertDialog alert = builder.create();
                    alert.show();
                }
            }
        });


        ImageView profileImageView = (ImageView) findViewById( R.id.profile_imageview );
        profileImageView.setOnClickListener( new ImageView.OnClickListener() {

            @Override
            public void onClick( View view )
            {
                Log.d( TAG, "profile image clicked!" );
                startActivity(new Intent(ProfileActivity.this, ImagePickerActivity.class));
            }
        });

        Bitmap profileImage = app.getTempProfileImage();
        if ( profileImage == null ) {
            profileImage = app.getProfileImage();
            app.setTempProfileImage(profileImage);
        }
        profileImageView.setImageBitmap( profileImage );

        TextView displayNameView = (TextView) findViewById( R.id.screenname );
        String displayName = app.getScreenName();
        if ( displayName != null ) {
            displayNameView.setText( displayName );
        }

        final boolean firstTime = app.isFirstTime();
        if ( firstTime ) {
            Log.i( TAG, "First time!" );

            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which){
                        case DialogInterface.BUTTON_POSITIVE:
                            break;

                        case DialogInterface.BUTTON_NEGATIVE:
                            //No button clicked
                            break;
                    }
                }
            };

            String message = getString( R.string.dlg_firsttime_text );
            String title = getString( R.string.dlg_firsttime_title );
            String buttonTitle = getString( R.string.okay );

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message).setTitle(title).setPositiveButton(buttonTitle, dialogClickListener);
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    private boolean validateDisplayName( String displayName )
    {
        return displayName.length() > 0;
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        Log.mark( TAG );

        MeshballApplication app = (MeshballApplication) getApplication();

        if ( app != null ) {
            app.savePreferences();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        Log.mark( TAG );
        unregisterReceiver(profileReceiver);
    }

    @Override
    public void onBackPressed() {
        Log.mark( TAG );

        EditText displayNameText = (EditText) findViewById( R.id.screenname );
        String screenName = displayNameText.getText().toString();
        if ( validateDisplayName( screenName ) ) {
            super.onBackPressed();
        }
        else {
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which){
                        case DialogInterface.BUTTON_POSITIVE:
                            //Yes button clicked
                            break;

                        case DialogInterface.BUTTON_NEGATIVE:
                            //No button clicked
                            break;
                    }
                }
            };

            String message = getString( R.string.dlg_displayname_text );
            String title = getString( R.string.dlg_displayname_title );
            String buttonTitle = getString( R.string.okay );

            AlertDialog.Builder builder = new AlertDialog.Builder(ProfileActivity.this);
            builder.setMessage(message).setTitle(title).setPositiveButton(buttonTitle, dialogClickListener);
            AlertDialog alert = builder.create();
            alert.show();
        }
    }
}