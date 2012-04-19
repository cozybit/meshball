package com.samsung.meshball;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.samsung.meshball.camera.CameraManager;
import com.samsung.meshball.utils.Log;

import java.io.IOException;

public class MeshballActivity extends SherlockActivity implements SurfaceHolder.Callback
{
    private static final String TAG = MeshballActivity.class.getName();

    private boolean hasSurface = false;
    private InactivityTimer inactivityTimer;
    private CameraManager cameraManager;
    private ViewfinderView viewfinderView;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );

        setContentView(R.layout.main);

        hasSurface = false;
        inactivityTimer = new InactivityTimer( this );
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
        super.onResume();
        // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
        // want to open the camera driver and measure the screen size if we're going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
        // off screen.
        cameraManager = new CameraManager( getApplication() );

        viewfinderView = (ViewfinderView) findViewById( R.id.viewfinder_view );
        viewfinderView.setCameraManager( cameraManager );

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
        inactivityTimer.onPause();
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
                Toast.makeText( this, "Players!", Toast.LENGTH_LONG ).show();
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

}
