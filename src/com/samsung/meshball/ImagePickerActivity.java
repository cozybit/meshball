/*
 * Copyright (c) 2011, Wobblesoft LLC. All rights reserved.
 */

package com.samsung.meshball;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.samsung.meshball.adapters.CropOptionAdapter;
import com.samsung.meshball.utils.Log;
import com.samsung.meshball.utils.MediaManager;
import com.samsung.meshball.utils.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This class ...
 */
public class ImagePickerActivity extends Activity
{
    private static final String TAG = ImagePickerActivity.class.getName();
    
    private static final int SELECT_IMAGE = 100;
    private static final int CROP_IMAGE = 200;
    private static final int USE_IMAGE = 300;
    
    private File tmpImageFile;

    public void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.imagepicker);

        ImageView profileImageView = (ImageView) findViewById( R.id.ip_profile_image_view );

        MeshballApplication app = (MeshballApplication) getApplication();

        // We may have an in-flight image...
        Bitmap profileImage = app.getTempProfileImage();
        if ( profileImage == null ) {
            // Otherwise use the profile picture (which maybe the default)
            profileImage = app.getProfileImage();
        }
        profileImageView.setImageBitmap(profileImage);

        if ( ! Utils.checkCameraHardware(getApplicationContext())) {
            Button takeButton = (Button) findViewById( R.id.ip_take_picture_button);
            takeButton.setVisibility(View.GONE);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == RESULT_OK) {

            MeshballApplication app = (MeshballApplication) getApplication();

            Bitmap image;
            switch ( requestCode ) {
                case SELECT_IMAGE:
                    Uri selectedImageUri = data.getData();
                    String path = getPath(selectedImageUri);
                    if ( path != null ) {
                        File workingImageFile = new File( path );
                        try {
                            InputStream imageStream = getContentResolver().openInputStream(selectedImageUri);
                            image = BitmapFactory.decodeStream( imageStream );
                            if ( (image.getWidth() > 250) || (image.getHeight() > 250) ) {
                                crop( workingImageFile );
                            }
                            else {
                                ImageView profileImage = (ImageView) findViewById( R.id.ip_profile_image_view );
                                profileImage.setImageBitmap( image );
                                app.setTempProfileImage( image );
                            }
                        }
                        catch ( FileNotFoundException e ) {
                            Log.e(TAG, e, "Caught Exception: %1$s", e.getMessage());
                            Toast.makeText( this, "Failed to pick picture - sorry...", Toast.LENGTH_LONG ).show();
                        }
                    }
                    else {
                        Log.e(TAG, "NULL path returned for %s", selectedImageUri);
                        Toast.makeText( this, "Failed to pick picture - sorry...", Toast.LENGTH_LONG ).show();
                    }
                    break;
                
                case CROP_IMAGE:
                    crop( tmpImageFile );
                    break; 
                
                case USE_IMAGE:
                    Log.mark( TAG );

                    final Bundle extras = data.getExtras();

                    if ( extras != null ) {
                        image = extras.getParcelable( "data" );
                        ImageView profileImage = (ImageView) findViewById( R.id.ip_profile_image_view );
                        profileImage.setImageBitmap( image );
                        app.setTempProfileImage( image );
                    }
                    break;                    
            }
        }
        else {
            Resources res = getResources();
            Toast.makeText( this, res.getString( R.string.crop_option_no_result_message ), Toast.LENGTH_LONG ).show();
            Log.w(TAG, "Activity returned with a result code of %1$d", resultCode);
        }
    }

    private void crop( File imageFile )
    {
        try
        {
            final List<CropOptionAdapter.CropOption> cropOptions = new ArrayList<CropOptionAdapter.CropOption>();

            // this 2 lines are all you need to find the intent!!!
            Intent intent = new Intent( "com.android.camera.action.CROP" );
            intent.setType( "image/*" );

            List<ResolveInfo> list = getPackageManager().queryIntentActivities( intent, 0 );
            if ( list.size() == 0 )
            {
                Toast.makeText( this, getText( R.string.crop_option_error_message ), Toast.LENGTH_LONG ).show();
                return;
            }

            intent.setData( Uri.fromFile( imageFile ) );

            intent.putExtra( "outputX", 300 );
            intent.putExtra( "outputY", 300 );
            intent.putExtra( "aspectX", 1 );
            intent.putExtra( "aspectY", 1 );
            intent.putExtra( "scale", true );
            intent.putExtra( "noFaceDetection", true );

            //intent.putExtra( "", true ); // I seem to have lost the option to have the crop app auto rotate the image, any takers?
            intent.putExtra( "return-data", true );

            if ( list.size() == 1 ) {
                ResolveInfo res = list.get( 0 );
                final CropOptionAdapter.CropOption co = new CropOptionAdapter.CropOption();
                co.TITLE = getPackageManager().getApplicationLabel( res.activityInfo.applicationInfo );
                co.ICON = getPackageManager().getApplicationIcon( res.activityInfo.applicationInfo );
                co.CROP_APP = new Intent( intent );
                co.CROP_APP.setComponent( new ComponentName( res.activityInfo.packageName, res.activityInfo.name ) );

                Log.d(TAG, "Starting activity for: %1$s", co.CROP_APP);
                startActivityForResult( co.CROP_APP, USE_IMAGE );
            }
            else {
                for ( ResolveInfo res : list )
                {
                    final CropOptionAdapter.CropOption co = new CropOptionAdapter.CropOption();
                    co.TITLE = getPackageManager().getApplicationLabel( res.activityInfo.applicationInfo );
                    co.ICON = getPackageManager().getApplicationIcon( res.activityInfo.applicationInfo );
                    co.CROP_APP = new Intent( intent );
                    co.CROP_APP.setComponent( new ComponentName( res.activityInfo.packageName, res.activityInfo.name ) );
                    cropOptions.add( co );
                }

                // set up the chooser dialog
                CropOptionAdapter adapter = new CropOptionAdapter( this, cropOptions );
                AlertDialog.Builder builder = new AlertDialog.Builder( this );
                builder.setTitle( R.string.crop_option_choose_title );
                builder.setAdapter( adapter, new DialogInterface.OnClickListener() {
                    public void onClick( DialogInterface dialog, int item )
                    {
                        startActivityForResult( cropOptions.get( item ).CROP_APP, USE_IMAGE );
                    }
                } );
                builder.setOnCancelListener( new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel( DialogInterface dialog )
                    {
                        // we don't want to keep the capture around if we cancel the crop because we don't want it anymore
                        if ( tmpImageFile != null ) {
                            getContentResolver().delete( Uri.fromFile( tmpImageFile ), null, null );
                            tmpImageFile = null;
                        }
                    }
                } );
                AlertDialog alert = builder.create();
                alert.show();
            }
        }
        catch ( Exception e )
        {
            Log.e(TAG, e, "processing capture - %s", e.getMessage());
        }
    }

    public String getPath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = managedQuery(uri, projection, null, null, null);

        if(cursor != null) {
            //HERE YOU WILL GET A NULLPOINTER IF CURSOR IS NULL
            //THIS CAN BE, IF YOU USED OI FILE MANAGER FOR PICKING THE MEDIA
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        }
        else {
            return null;
        }
    }

    private Bitmap decodeUri(Uri selectedImageUri) throws FileNotFoundException
    {
        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImageUri), null, o);

        // The new size we want to scale to
        final int REQUIRED_SIZE = 300;

        // Find the correct scale value. It should be the power of 2.
        int width_tmp = o.outWidth, height_tmp = o.outHeight;
        int scale = 1;
        while (true) {
            if (width_tmp / 2 < REQUIRED_SIZE || height_tmp / 2 < REQUIRED_SIZE) {
                break;
            }
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }

        // Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        return BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImageUri), null, o2);
    }

    public void onTakePicture(View target)
    {
        tmpImageFile = MediaManager.getOutputMediaFile( MediaManager.MEDIA_TYPE_IMAGE );

        Log.d(TAG, "tempImageFile = %1$s", tmpImageFile);

        Intent intent = new Intent( MediaStore.ACTION_IMAGE_CAPTURE );
        intent.putExtra( MediaStore.EXTRA_OUTPUT, Uri.fromFile( tmpImageFile ) );

        // start the image capture Intent
        startActivityForResult(intent, CROP_IMAGE );
    }
    
    public void onCancel(View target)
    {
        if ( tmpImageFile != null ) {
            if ( ! tmpImageFile.delete() ) {
                Resources res = getResources();
                Toast.makeText( getApplicationContext(), res.getText(R.string.toast_failed_to_delete_tmp_file), Toast.LENGTH_LONG ).show();
            }
            tmpImageFile = null;
        }

        MeshballApplication app = (MeshballApplication) getApplication();
        app.setTempProfileImage( null );
        
        finish();
    }
    
    public void onUse(View target)
    {
        if ( tmpImageFile != null ) {
            if ( ! tmpImageFile.delete() ) {
                Resources res = getResources();
                Toast.makeText( getApplicationContext(), res.getText(R.string.toast_failed_to_delete_tmp_file), Toast.LENGTH_LONG ).show();
            }
            tmpImageFile = null;
        }

        // Send back the image
        Intent i = new Intent( ProfileActivity.PROFILE_IMAGE );
        sendBroadcast( i );

        finish();
    }
}
