/*
 * Copyright (C) 2012, Wobblesoft LLC, All rights reserved.
 */
package com.samsung.meshball;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * This class ...
 */
public class ReviewAdapter
        extends BaseAdapter
{
    private LayoutInflater inflater;
    private Activity context;

    private static class ViewHolder
    {
        ImageView gridImage;
        TextView gridText;
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

            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        MeshballApplication app = (MeshballApplication) context.getApplication();
        List<Player> players = app.getPlayers();
        Player player = players.get(position);

        holder.gridImage.setImageBitmap(player.getPicture());
        holder.gridText.setText(player.getScreenName());

        return convertView;
    }
}
