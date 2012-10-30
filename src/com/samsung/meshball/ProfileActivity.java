package com.samsung.meshball;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.ExifInterface;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Display;
import android.view.View;
import android.widget.*;
import com.samsung.meshball.data.Player;
import com.samsung.meshball.utils.Log;
import com.samsung.meshball.utils.MediaManager;
import com.samsung.meshball.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * This class ...
 */
public class ProfileActivity
        extends Activity
{
    private static final String TAG = ProfileActivity.class.getName();

    public static final String PROFILE_IMAGE = "com.samsung.meshball.PROFILE_IMAGE";
    private static final int CAPTURE_IMAGE = 100;

    private boolean pictureSet = true;
    private File tmpImageFile;

    private ProgressDialog progressDialog;

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

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == RESULT_OK) {
            switch ( requestCode ) {
                case CAPTURE_IMAGE:
                    AsyncTask<Void, Void, Bitmap> task = new AsyncTask<Void, Void, Bitmap>() {
                        @Override
                        protected Bitmap doInBackground(Void... voids)
                        {
                            return autoCrop();
                        }

                        @Override
                        protected void onPostExecute(Bitmap bitmap)
                        {
                            MeshballApplication app = (MeshballApplication) getApplication();
                            if ( bitmap == null ) {
                                bitmap = app.getTempProfileImage();
                            }
                            ImageView profileImageView = (ImageView) findViewById( R.id.profile_imageview );
                            profileImageView.setImageBitmap( bitmap );

                            app.setTempProfileImage( bitmap );
                            pictureSet = true;
                            progressDialog.dismiss();
                        }

                        @Override
                        protected void onPreExecute()
                        {
                            progressDialog.setMessage(getString(R.string.progress_auto_cropping));
                            progressDialog.show();
                        }
                    };
                    task.execute(new Void[]{});
                    break;
            }
        }
        else {
            Toast.makeText( this, getString(R.string.crop_option_no_result_message), Toast.LENGTH_LONG ).show();
            Log.w(TAG, "Activity returned with a result code of %1$d", resultCode);
        }
    }

    private Bitmap autoCrop()
    {
        // Remember, the image is in our FILE that we handed to the camera in the EXTRA_OUTPUT extra

        try {
            FileInputStream fis = new FileInputStream(tmpImageFile);
            BitmapFactory.Options options = new BitmapFactory.Options();

            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap rawImage = BitmapFactory.decodeStream(fis, null, options);

            FaceDetector faceDetector = new FaceDetector( rawImage.getWidth(), rawImage.getHeight(), 1 );
            FaceDetector.Face[] detectedFaces = new FaceDetector.Face[1];
            int facesDetected = faceDetector.findFaces( rawImage, detectedFaces );
            Log.d( TAG, "Found %d faces", facesDetected );

            PointF midPoint;
            float distance, origDistance;

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            if ( facesDetected > 0 ) {
                FaceDetector.Face face = detectedFaces[0];
                midPoint = new PointF();
                face.getMidPoint(midPoint);
                origDistance = face.eyesDistance();
                Log.d( TAG, "distance = %f, midPoint.x = %f, midPoint.y = %f", origDistance, midPoint.x, midPoint.y );
            }
            else {
                // Failed to detect faces, so assume the center
                origDistance = 300;
                midPoint = new PointF( rawImage.getWidth()/2, rawImage.getHeight()/2 );
            }

            // First, crop the area around the face...

            Bitmap faceBitmap = Bitmap.createBitmap( 300, 300, Bitmap.Config.ARGB_8888 );
            Canvas canvas = new Canvas( faceBitmap );
            Paint paint = new Paint();

            int left = 0, top = 0, right = 300, bottom = 300;
            float ratio = 2f;
            for ( int attempt = 0; attempt < 3; attempt++ ) {
                distance = origDistance * ratio;
                left = (int) (midPoint.x - distance);
                if ( left < 0 ) {
                    left = 0;
                }
                top = (int) (midPoint.y - distance);
                if ( top < 0 ) {
                    top = 0;
                }
                right = (int) (left + (distance * 2));
                if ( right > rawImage.getWidth() ) {
                    left -= (right - rawImage.getWidth());
                    if ( left < 0 ) {
                        ratio -= 0.33f;
                        continue;
                    }
                    right = rawImage.getWidth();
                }
                bottom = (int) (top + (distance * 2));
                if ( bottom > rawImage.getHeight() ) {
                    top -= (bottom - rawImage.getHeight());
                    if ( top < 0 ) {
                        ratio -= 0.33f;
                        continue;
                    }
                    bottom = rawImage.getHeight();
                }
                break;
            }

            Rect src = new Rect( left, top, right, bottom);

            // Adjust the src rect if the face is not actually centered...

            RectF dest = new RectF( 0, 0, 300, 300 );
            canvas.drawBitmap( rawImage, src, dest, paint );

            ExifInterface exif = new ExifInterface(tmpImageFile.getAbsolutePath());
            int orientation = Integer.parseInt(exif.getAttribute(ExifInterface.TAG_ORIENTATION));
            Log.d(TAG, "Picture orientation is: " + orientation);

            //if the orientation of the original pic is not portrait,
            //then rotate the cropped picture
            if( orientation != ExifInterface.ORIENTATION_UNDEFINED ||
                    orientation != ExifInterface.ORIENTATION_NORMAL ) {
                int degrees = 0;
                switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degrees = 90;
                    break;

                case ExifInterface.ORIENTATION_ROTATE_180:
                    degrees = 180;
                    break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                    degrees = 270;
                    break;
                }

                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                faceBitmap = Bitmap.createBitmap(faceBitmap, 0, 0,
                    faceBitmap.getWidth(), faceBitmap.getHeight(), matrix, true);
            }

            return faceBitmap;
        }
        catch(IOException e) {
            e.printStackTrace();
            Toast.makeText( this, getString( R.string.toast_failed_to_load_image ), Toast.LENGTH_LONG ).show();
            Log.e(TAG, e, "Failed to load bitmap - %s", e.getMessage());
            return null;
        }
    }

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.profile );

        progressDialog = new ProgressDialog(this);

        if ( ! Utils.checkCameraHardware(getApplicationContext())) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ProfileActivity.this);
            builder.setMessage(R.string.dlg_no_camera_text)
                    .setTitle(R.string.dlg_no_camera_title)
                    .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i)
                        {
                            finish();
                        }
                    });

            builder.show();
            return;
        }

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
                    if ( app.inGame() ) {
                        app.broadcastIdentity();
                    }

                    finish();
                }
                else {
                    displayInvalidSetupDialog();
                }
            }
        });


        ImageView profileImageView = (ImageView) findViewById( R.id.profile_imageview );
        profileImageView.setOnClickListener( new ImageView.OnClickListener() {

            @Override
            public void onClick( View view )
            {
                Log.d( TAG, "profile image clicked!" );
                tmpImageFile = MediaManager.getOutputMediaFile(MediaManager.MEDIA_TYPE_IMAGE);

                Log.d(TAG, "tempImageFile = %1$s", tmpImageFile);

                Intent intent = new Intent( MediaStore.ACTION_IMAGE_CAPTURE );
                intent.putExtra( MediaStore.EXTRA_OUTPUT, Uri.fromFile(tmpImageFile) );

                // start the image capture Intent
                startActivityForResult(intent, CAPTURE_IMAGE);
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

        progressDialog.dismiss();
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
            displayInvalidSetupDialog();
        }
    }

    private void displayInvalidSetupDialog()
    {
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

    public void clearPressed(View view)
    {
        Log.mark( TAG );
        EditText displayNameView = (EditText) findViewById( R.id.screenname );
        displayNameView.setText( "" );
    }

}
