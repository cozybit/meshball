/*
 * Copyright (C) 2012, Wobblesoft LLC, All rights reserved.
 */
package com.samsung.meshball.adapters;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.samsung.meshball.MeshballApplication;
import com.samsung.meshball.R;
import com.samsung.meshball.data.Candidate;
import com.samsung.meshball.utils.Log;

import java.io.IOException;
import java.util.List;

/**
 * This class ...
 */
public class ReviewAdapter
        extends BaseAdapter
{
    private static final String TAG = ReviewAdapter.class.getName();

    private LayoutInflater inflater;
    private Activity context;

    private static class ViewHolder
    {
        ImageView gridImage;
        TextView gridText;
        ImageView gridCheckmark;
    }

    public ReviewAdapter(Activity context)
    {
        inflater = LayoutInflater.from(context);
        this.context = context;
    }

    public int getCount()
    {
        MeshballApplication app = (MeshballApplication) context.getApplication();
        return (app.getReviewList() != null ? app.getReviewList().size() : 0);
    }

    public Object getItem(int position)
    {
        return null;
    }

    public long getItemId(int position)
    {
        return 0;
    }

    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder holder;

        if (convertView == null) {  // if it's not recycled, initialize some attributes
            convertView = inflater.inflate(R.layout.grid_item, null);
            holder = new ViewHolder();

            holder.gridImage = (ImageView) convertView.findViewById(R.id.grid_image);
            holder.gridText = (TextView) convertView.findViewById(R.id.grid_label);
            holder.gridCheckmark = (ImageView) convertView.findViewById(R.id.grid_check_mark);

            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        MeshballApplication app = (MeshballApplication) context.getApplication();
        List<Candidate> reviewList = app.getReviewList();
        Candidate candidate = reviewList.get(position);

        if ( candidate.getPlayerID() != null ) {
            holder.gridCheckmark.setVisibility( View.VISIBLE );
        }
        else {
            holder.gridCheckmark.setVisibility( View.INVISIBLE );
        }

        try {
            holder.gridImage.setImageBitmap(candidate.getBitmap());
        }
        catch(IOException e) {
            holder.gridImage.setImageDrawable(context.getResources().getDrawable(R.drawable.missing_profile));
            Log.e(TAG, e, "Failed to set candidate bitmap.");
        }
        holder.gridText.setVisibility( View.INVISIBLE );

        return convertView;
    }
}
