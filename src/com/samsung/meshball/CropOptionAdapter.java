package com.samsung.meshball;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * This class ...
 */
public class CropOptionAdapter extends ArrayAdapter<CropOptionAdapter.CropOption>
{
    private List<CropOption> items;
    private Context ctx;

    static public class CropOption
    {
        public CharSequence TITLE;
        public Drawable ICON;
        public Intent CROP_APP;
    }

    public CropOptionAdapter(Context ctx, List<CropOption> items)
    {
        super(ctx, R.layout.crop_options, items);
        this.items = items;
        this.ctx = ctx;
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent )
    {
        if ( convertView == null )
            convertView = LayoutInflater.from(ctx).inflate( R.layout.crop_options, null );

        CropOption item = items.get( position );
        if ( item != null )
        {
            ( (ImageView) convertView.findViewById( R.id.crop_icon ) ).setImageDrawable( item.ICON );
            ( (TextView) convertView.findViewById( R.id.crop_name ) ).setText( item.TITLE );
            return convertView;
        }
        return null;
    }
}
