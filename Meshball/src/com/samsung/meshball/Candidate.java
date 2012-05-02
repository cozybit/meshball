package com.samsung.meshball;

/**
 * This class ...
 */
public class Candidate
{
    private String playerID;
    private String fileName;

    public Candidate(String playerID, String fileName)
    {
        this.playerID = playerID;
        this.fileName = fileName;
    }

    public Candidate(String fileName)
    {
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
