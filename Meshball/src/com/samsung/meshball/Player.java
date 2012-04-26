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
    private int playerID;
    private String screenName;
    private Bitmap picture;
    private String hitBy;
    private String reviewedBy;
    private int shots = 0;

    public Player(int playerID, String screenName, Bitmap picture)
    {
        this.playerID = playerID;
        this.screenName = screenName;
        this.picture = picture;
    }

    public int getPlayerID()
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

        return playerID == player.playerID;
    }

    @Override
    public int hashCode()
    {
        return playerID;
    }

    @Override
    public String toString()
    {
        return "Player{" +
                "playerID=" + playerID +
                ", screenName='" + screenName + '\'' +
                ", shots=" + shots +
                ", hitBy='" + hitBy + '\'' +
                ", reviewedBy='" + reviewedBy + '\'' +
                '}';
    }
}
