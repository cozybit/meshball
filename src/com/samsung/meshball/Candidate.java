package com.samsung.meshball;

import android.graphics.Bitmap;
import com.samsung.meshball.utils.MediaManager;

import java.io.File;
import java.io.IOException;

/**
 * This class ...
 */
public class Candidate
{
    private String playerID;
    private File path;
    private String fileName;
    private String shooterID;

    public Candidate(String playerID, File path, String fileName)
    {
        this.playerID = playerID;
        this.path = path;
        this.fileName = fileName;
    }

    public Candidate(File path, String fileName)
    {
        this.path = path;
        this.fileName = fileName;
    }

    public String getPlayerID()
    {
        return playerID;
    }

    public String getFileName()
    {
        return fileName;
    }

    public File getPath()
    {
        return path;
    }

    public Bitmap getBitmap()
            throws IOException
    {
        return MediaManager.loadBitmapImage( fileName );
    }

    public void setPlayerID(String playerID)
    {
        this.playerID = playerID;
    }

    public void setShooterID(String shooterID)
    {
        this.shooterID = shooterID;
    }

    public String getShooterID()
    {
        return shooterID;
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

        Candidate candidate = (Candidate) o;

        return !(fileName != null ? !fileName.equals(candidate.fileName) : candidate.fileName != null);
    }

    @Override
    public int hashCode()
    {
        return fileName != null ? fileName.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        return "Candidate{" +
                "fileName='" + fileName + '\'' +
                ", playerID='" + playerID + '\'' +
                '}';
    }
}
