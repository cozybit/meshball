package com.samsung.meshball;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.samsung.meshball.camera.CameraManager;
import com.samsung.meshball.utils.Log;
import com.samsung.meshball.utils.WifiUtils;

import java.io.IOException;
import java.util.Random;

public class MeshballActivity extends SherlockActivity implements SurfaceHolder.Callback
{
    private static final String TAG = MeshballActivity.class.getName();

    private boolean hasSurface = false;
    private InactivityTimer inactivityTimer;
    private CameraManager cameraManager;
    private ImageButton fireButton;
    private TextView scoreLabel;
    private ViewfinderView viewFinder;
    private FrameLayout mainFrame;
    private ImageView capturedImageView;
    private boolean fromCreate;
    private Handler handler;
    private Random dice = new Random( System.currentTimeMillis() );

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.mark( TAG );
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );

        setContentView(R.layout.main);

        fromCreate = true;
        hasSurface = false;
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

        scoreLabel = (TextView) findViewById( R.id.score_label );
        fireButton = (ImageButton) findViewById( R.id.fire_button );
        viewFinder = (ViewfinderView) findViewById( R.id.viewfinder_view );
        capturedImageView = (ImageView) findViewById( R.id.captured_imageview );
        mainFrame = (FrameLayout) findViewById( R.id.main_frame );

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

                    if ( ! cameraManager.isInShot() ) {
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

        // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
        // want to open the camera driver and measure the screen size if we're going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
        // off screen.

        cameraManager = new CameraManager( getApplication(), viewFinder );

        ViewfinderView viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);

        SurfaceView surfaceView = (SurfaceView) findViewById( R.id.preview_view );
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if ( hasSurface )
        {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.

            initCamera( surfaceHolder );
        }
        else
        {
            // Install the callback and wait for surfaceCreated() to init the camera.
            surfaceHolder.addCallback( this );
            surfaceHolder.setType( SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS );
        }

        inactivityTimer.onResume();
    }

    @Override
    protected void onPause()
    {
        Log.mark( TAG );
        inactivityTimer.onPause();

        cameraManager.stopPreview();
        cameraManager.closeDriver();

        if ( !hasSurface )
        {
            SurfaceView surfaceView = (SurfaceView) findViewById( R.id.preview_view );
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback( this );
        }

        super.onPause();
    }

    @Override
    protected void onStart()
    {
        Log.mark( TAG );
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

    @Override
    public void surfaceCreated( SurfaceHolder holder )
    {
        if ( holder == null )
        {
            Log.e( TAG, "*** WARNING *** surfaceCreated() gave us a null surface!" );
        }
        if ( !hasSurface )
        {
            hasSurface = true;
            initCamera( holder );
            cameraManager.startPreview();
        }
    }

    @Override
    public void surfaceChanged( SurfaceHolder surfaceHolder, int i, int i1, int i2 )
    {

    }

    @Override
    public void surfaceDestroyed( SurfaceHolder surfaceHolder )
    {
        hasSurface = false;
    }

    private void initCamera( SurfaceHolder surfaceHolder )
    {
        try
        {
            cameraManager.openDriver( surfaceHolder );
            cameraManager.startPreview();
        }
        catch ( IOException ioe )
        {
            Log.e( TAG, ioe, "Unexpected error initializing camera: %s", ioe.getMessage() );
            displayFrameworkBugMessageAndExit();
        }
        catch ( RuntimeException e )
        {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.e(TAG, e, "Unexpected error initializing camera: %s", e.getMessage());
            displayFrameworkBugMessageAndExit();
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

        cameraManager.setInShot( true );
        MeshballApplication app = (MeshballApplication) getApplication();

        // First draw a paint splatter and add some randomness in its position.

        Drawable drawable = app.getRandomSplatter();
        Bitmap splatter = ((BitmapDrawable) drawable).getBitmap();

        cameraManager.takePicture( splatter );
    }

    public void clearShot()
    {
        viewFinder.clearSplatter();
        cameraManager.startPreview();
    }

    public void hitsPressed(View v)
    {
        Intent intent = new Intent(this, ReviewPlayerActivity.class);
        startActivity(intent);
    }

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
}
