package com.samsung.meshball;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import com.samsung.meshball.utils.Log;
import com.samsung.meshball.utils.MediaManager;
import com.samsung.meshball.utils.WifiUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class MeshballActivity extends Activity
{
    private static final String TAG = MeshballActivity.class.getName();

    private static final int MIN_FRAME_WIDTH = 240;
    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_WIDTH = 600;
    private static final int MAX_FRAME_HEIGHT = 400;

    private CameraConfigurationManager configManager;
    private Camera camera;
    private FrameLayout preview;
    private Rect framingRect;

    private Bitmap splatter;
    private boolean inShot;
    private int shotCounter = 0;

    private InactivityTimer inactivityTimer;
    private ImageButton fireButton;
    private TextView scoreLabel;
    private TextView reviewLabel;
    private TextView confirmLabel;
    private ViewfinderView viewFinder;
    private boolean fromCreate;
    private Handler handler;

    private Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback()
    {
        @Override
        public void onAutoFocus(boolean success, final Camera camera)
        {
            Log.mark(TAG);
            camera.takePicture( shutterCallback, rawPictureCallback, jpegPictureCallback );
        }
    };

    private Camera.PictureCallback jpegPictureCallback = new Camera.PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            Log.d(TAG, "Size: %d", (data != null ? data.length : 0));

            camera.stopPreview();

            if ( data != null ) {

                viewFinder.setSplatter( splatter );

                Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);
                Matrix mat = new Matrix();

                // This is really dumb
                Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                if ( (display.getRotation() == Surface.ROTATION_0) || (display.getRotation() == Surface.ROTATION_270) ) {
                    mat.postRotate(90);
                }

                Bitmap rotatedImage = Bitmap.createBitmap( image, 0, 0, image.getWidth(), image.getHeight(), mat, true );
                Bitmap mutableBitmap = rotatedImage.copy( Bitmap.Config.ARGB_8888, true );

                // Now we need to composite the splatter
                Canvas canvas = new Canvas( mutableBitmap );

                mat = new Matrix();
                mat.setScale( 0.3f, 0.3f );

                Bitmap scaledImage = Bitmap.createBitmap( splatter, 0, 0, splatter.getWidth(), splatter.getHeight(), mat, true );

                float left = (canvas.getWidth() / 2) - (scaledImage.getWidth() / 2);
                float top = (canvas.getHeight() / 2) - (scaledImage.getHeight() / 2);

                canvas.drawBitmap( scaledImage, left, top, null );

                MeshballApplication app = (MeshballApplication) getApplication();

                try {
                    Log.i( TAG, "SAVING AND QUEUING IMAGE!" );

                    StringBuilder name = new StringBuilder();
                    name.append(app.getPlayerID());
                    name.append("-");
                    name.append(shotCounter++);
                    name.append(".JPG");

                    MediaManager.saveBitmapImage(mutableBitmap, name.toString(), 100);
                    app.getConfirmList().add(new Candidate(name.toString()));
                }
                catch (FileNotFoundException e) {
                    Log.e(TAG, e, "Caught exception while writing profile image: %s", e.getMessage());
                }
                catch (IOException e) {
                    Log.e(TAG, e, "Caught exception while writing profile image: %s", e.getMessage());
                    e.printStackTrace();
                }

                camera.addCallbackBuffer( data );
            }

            inShot = false;
        }
    };

    private Camera.PictureCallback rawPictureCallback = new Camera.PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            Log.d(TAG, "Size: %d", (data != null ? data.length : 0));

            // Ignore the raw picture..
            if ( data != null ) {
                camera.addCallbackBuffer( data );
            }
        }
    };

    private Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter()
        {
            Log.mark( TAG );
        }
    };

    private Camera getCameraInstance() {
        Camera c = null;

        try
        {
            c = Camera.open();
            if ( c == null ) {
                displayFrameworkBugMessageAndExit();
            }
        }
        catch ( RuntimeException e )
        {
            Log.e(TAG, e, "Unexpected error initializing camera: %s", e.getMessage());
            displayFrameworkBugMessageAndExit();
        }
        catch(Exception e) {
            Log.e(TAG, e, "Unexpected error initializing camera: %s", e.getMessage());
            displayFrameworkBugMessageAndExit();
        }

        return c;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.mark( TAG );
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );

        setContentView(R.layout.main);

        this.configManager = new CameraConfigurationManager( getApplicationContext() );

        fromCreate = true;
        inactivityTimer = new InactivityTimer( this );
        handler = new Handler();

        MeshballApplication app = (MeshballApplication) getApplication();
        app.loadPreferences();
        app.setMeshballActivity( this );

        if ( app.isFirstTime() || (app.getScreenName() == null) ) {
            Log.i(TAG, "Display name is %s or is first time (%s). Switching to the ProfileActivity...",
                  app.getScreenName(), (app.isFirstTime() ? "YES" : "NO"));
            startActivity(new Intent(this, ProfileActivity.class));
        }

        preview = (FrameLayout) findViewById( R.id.preview_view );
        scoreLabel = (TextView) findViewById( R.id.score_label );
        fireButton = (ImageButton) findViewById( R.id.fire_button );

        reviewLabel = (TextView) findViewById( R.id.review_counter );
        confirmLabel = (TextView) findViewById( R.id.confirm_counter );

        viewFinder = (ViewfinderView) findViewById( R.id.viewfinder_view );
        viewFinder.setMeshballActivity( this );

        // Based on our orientation and handedness, place our button...
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        if ( (display.getRotation() == Surface.ROTATION_90) || (display.getRotation() == Surface.ROTATION_270) ) {
            Log.d( TAG, "LANDSCAPE!" );

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                                                                           FrameLayout.LayoutParams.WRAP_CONTENT);

            if ( getString(R.string.right).equalsIgnoreCase( app.getHandedNess() ) ) {
                Log.d( TAG, "Right handed!" );
                params.gravity = 0x10 | 0x05;
            }
            else {
                Log.d( TAG, "Left handed!" );
                params.gravity = 0x10 | 0x03;
            }

            fireButton.setLayoutParams( params );

            params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                                                  FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = 0x50 | 0x11;
            scoreLabel.setLayoutParams( params );
        }

        fireButton.setOnTouchListener( new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                if ( event.getAction() == MotionEvent.ACTION_DOWN ) {
                    fireButton.setImageDrawable( getResources().getDrawable( R.drawable.fire_button_pressed ) );
                    return true;
                }
                else if ( event.getAction() == MotionEvent.ACTION_UP ) {
                    fireButton.setImageDrawable( getResources().getDrawable( R.drawable.fire_button ) );

                    if ( ! inShot ) {
                        fireShot();

                        // Set timer to clear the shot!
                        handler.postDelayed( new Runnable() {
                            @Override
                            public void run()
                            {
                                clearShot();
                            }
                        }, 2000 );
                    }

                    return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void onDestroy()
    {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onResume()
    {
        Log.mark( TAG );
        super.onResume();

        preview.removeAllViews();

        camera = getCameraInstance();

        if ( ! configManager.isInitialized() ) {
            configManager.initFromCameraParameters(camera);
        }
        configManager.setDesiredCameraParameters( camera );

        CameraPreview cameraPreview = new CameraPreview(this, camera);
        preview.addView(cameraPreview, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) );

        camera.startPreview();

        MeshballApplication app = (MeshballApplication) getApplication();

        reviewLabel.setText( String.valueOf( app.getReviewList().size() ) );
        confirmLabel.setText( String.valueOf( app.getConfirmList().size() ) );
        scoreLabel.setText( getString( R.string.score_lbl_txt, app.getScore() ) );

        inactivityTimer.onResume();
    }

    @Override
    protected void onPause()
    {
        Log.mark( TAG );
        super.onPause();

        releaseCamera();

        inactivityTimer.onPause();
    }

    private void releaseCamera()
    {
        if ( camera != null ) {
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onStart()
    {
        Log.mark(TAG);
        super.onStart();

        MeshballApplication app = (MeshballApplication) getApplication();
        WifiUtils wifiUtils = app.getWifiUtils();

        boolean isEnabled = wifiUtils.getWifiManager().isWifiEnabled() || wifiUtils.isWifiApEnabled();
        if( !isEnabled || (!wifiUtils.isWifiApEnabled() && (wifiUtils.getWifiManager().getConnectionInfo().getSSID() == null))) {
            if(fromCreate) {
                displayFriendlyWifiDialog();
            }
            else {
                displayDisabledWifiDialog();
            }
        }
        fromCreate = false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        Log.mark( TAG );
        super.onConfigurationChanged( newConfig );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        menu.add(Menu.NONE, R.id.menu_players, 0, "Players")
                .setIcon(R.drawable.icon_action_players)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, R.id.menu_share, 1, "Share")
                .setIcon( R.drawable.icon_action_share )
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS );

        menu.add(Menu.NONE, R.id.menu_profile, 2, "Profile")
                .setIcon(R.drawable.icon_profile)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        menu.add(Menu.NONE, R.id.menu_settings, 3, "Settings")
                .setIcon(R.drawable.icon_settings)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Log.d(TAG, "Selected: %s", item.getTitle());

        Intent intent;
        switch(item.getItemId()) {
            case R.id.menu_players:
                intent = new Intent(this, PlayersActivity.class);
                startActivity(intent);
                return true;

            case R.id.menu_share:
                Toast.makeText( this, "Share!", Toast.LENGTH_LONG ).show();
                return true;

            case R.id.menu_profile:
                intent = new Intent(this, ProfileActivity.class);
                startActivity(intent);
                return true;

            case R.id.menu_settings:
                intent = new Intent(this, PreferencesActivity.class);
                startActivity(intent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void displayFrameworkBugMessageAndExit()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder( this );
        builder.setTitle( getString( R.string.app_name ) );
        builder.setMessage( getString( R.string.msg_camera_framework_bug ) );
        builder.setPositiveButton( R.string.okay, new FinishListener( this ) );
        builder.setOnCancelListener( new FinishListener( this ) );
        builder.show();
    }

    public void fireShot()
    {
        Log.d( TAG, "FIRE!!!" );

        inShot = true;
        MeshballApplication app = (MeshballApplication) getApplication();

        // First draw a paint splatter and add some randomness in its position.

        Drawable drawable = app.getRandomSplatter();
        splatter = ((BitmapDrawable) drawable).getBitmap();

        camera.startPreview();

        Camera.Parameters p = camera.getParameters();
        List<String> focusModes = p.getSupportedFocusModes();

        if( (focusModes != null) && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO) ) {
            //Phone supports autofocus! Focus first, then take picture
            camera.autoFocus( autoFocusCallback );
        }
        else {
            //Phone does not support autofocus! Just take the picture
            Log.d( TAG, "PHONE DOES NOT SUPPORT AUTOFOCUS" );
            camera.takePicture( shutterCallback, rawPictureCallback, jpegPictureCallback );
        }
    }

    public void clearShot()
    {
        viewFinder.clearSplatter();
        camera.startPreview();
    }

    @SuppressWarnings("UnusedParameters")
    public void hitsPressed(View v)
    {
        Intent intent = new Intent(this, ReviewHitActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings("UnusedParameters")
    public void reviewPressed(View v)
    {
        Intent intent = new Intent(this, ConfirmHitActivity.class);
        startActivity(intent);
    }

    public void displayFriendlyWifiDialog()
    {
        // Let's go ahead and ask for the details and then store them.  This will make it easier to
        // transmit securely to other devices.

        displayDisabledWifiDialog();
    }

    public void displayDisabledWifiDialog()
    {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int which)
            {
                switch(which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        // Refresh the lists
                        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                        startActivity(intent);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dlg_disabled_wifi_message)
                .setTitle(R.string.warning)
                .setCancelable(false)
                .setPositiveButton(R.string.settings, listener)
                .setNegativeButton(R.string.no, listener);
        AlertDialog alert = builder.create();
        alert.show();
    }

    public Rect getFramingRect()
    {
        if ( framingRect == null )
        {
            if ( camera == null )
            {
                return null;
            }
            Point screenResolution = configManager.getScreenResolution();
            int width = screenResolution.x * 3 / 4;
            if ( width < MIN_FRAME_WIDTH )
            {
                width = MIN_FRAME_WIDTH;
            }
            else if ( width > MAX_FRAME_WIDTH )
            {
                width = MAX_FRAME_WIDTH;
            }
            int height = screenResolution.y * 3 / 4;
            if ( height < MIN_FRAME_HEIGHT )
            {
                height = MIN_FRAME_HEIGHT;
            }
            else if ( height > MAX_FRAME_HEIGHT )
            {
                height = MAX_FRAME_HEIGHT;
            }
//            if ( context.getResources().getConfiguration().orientation == 1 ) {
//                // Portrait...
//                int leftOffset = (( screenResolution.x - height ) / 2) - 100;
//                int topOffset = ( screenResolution.y - width ) / 2;
//
//                framingRect = new Rect( leftOffset, topOffset, leftOffset + height, topOffset + width );
//            }
//            else {
//                int leftOffset = ( screenResolution.x - width ) / 2;
//                int topOffset = (( screenResolution.y - height ) / 2) - 100;
//
//                framingRect = new Rect( leftOffset, topOffset, leftOffset + width, topOffset + height );
//            }

            int leftOffset = ( screenResolution.x - width ) / 2;
            int topOffset = (( screenResolution.y - height ) / 2) - 100;

            framingRect = new Rect( leftOffset, topOffset, leftOffset + width, topOffset + height );

            Log.d( TAG, "Calculated framing rect: %s", framingRect );
        }
        return framingRect;
    }
}
