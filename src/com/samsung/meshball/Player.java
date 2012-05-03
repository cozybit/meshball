/*
 * Copyright (C) 2012, Wobblesoft LLC, All rights reserved.
 */
package com.samsung.meshball;

import android.graphics.Bitmap;

/**
 * This class ...
 */
public class Player
{
    private String playerID;
    private String screenName;
    private Bitmap picture;
    private String hitBy;
    private String reviewedBy;
    private String nodeID;
    private int shots = 0;
    private boolean playing = true;

    public Player(String playerID)
    {
        this.playerID = playerID;
    }

    public String getPlayerID()
    {
        return playerID;
    }

    public String getScreenName()
    {
        return screenName;
    }

    public void setScreenName(String screenName)
    {
        this.screenName = screenName;
    }

    public Bitmap getPicture()
    {
        return picture;
    }

    public void setPicture(Bitmap picture)
    {
        this.picture = picture;
    }

    public String getHitBy()
    {
        return hitBy;
    }

    public void setHitBy(String hitBy)
    {
        this.hitBy = hitBy;
    }

    public String getReviewedBy()
    {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy)
    {
        this.reviewedBy = reviewedBy;
    }

    public int getShots()
    {
        return shots;
    }

    public void setShots(int shots)
    {
        this.shots = shots;
    }

    public boolean isPlaying()
    {
        return playing;
    }

    public void setIsPlaying(boolean playing)
    {
        this.playing = playing;
    }

    public String getNodeID()
    {
        return nodeID;
    }

    public void setNodeID(String nodeID)
    {
        this.nodeID = nodeID;
    }

    @Override
    public boolean equals(Object o)
    {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }

        Player player = (Player) o;

        return !(playerID != null ? !playerID.equals(player.playerID) : player.playerID != null);
    }

    @Override
    public int hashCode()
    {
        return playerID != null ? playerID.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        return "Player{" +
                "playerID='" + playerID + '\'' +
                ", screenName='" + screenName + '\'' +
                ", playing=" + playing +
                ", shots=" + shots +
                ", reviewedBy='" + reviewedBy + '\'' +
                ", hitBy='" + hitBy + '\'' +
                '}';
    }
}
