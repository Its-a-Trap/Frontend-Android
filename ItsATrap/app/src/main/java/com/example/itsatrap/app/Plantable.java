package com.example.itsatrap.app;

import com.google.android.gms.maps.model.LatLng;

import java.util.Date;

/**
 * Created by maegereg on 5/10/14.
 */
public class Plantable
{

    private int plantableId;
    private int ownerId;
    private LatLng location;
    private boolean isActive;
    private Date plantTime;
    //In seconds
    private long duration;
    //In meters
    private float radius;

    public Plantable(int plantableId, int ownerId, LatLng location, Date plantTime, long duration, float radius)
    {
        this.plantableId = plantableId;
        this.ownerId = ownerId;
        this.location = location;
        this.plantTime = plantTime;
        this.duration = duration;
        this.radius = radius;

        isActive = true;
    }

    public void setPlantableId(int id)
    {
        plantableId = id;
    }

    public void setActive(boolean isActive)
    {
        this.isActive = isActive;
    }

    public void setDuration(long duration)
    {
        this.duration = duration;
    }

    public void setRadius(float radius)
    {
        this.radius = radius;
    }

    public int getPlantableId()
    {
        return plantableId;
    }

    public int getOwnerId()
    {
        return ownerId;
    }

    public LatLng getLocation()
    {
        return location;
    }

    public boolean isActive()
    {
        return isActive;
    }

    public Date getPlantTime()
    {
        return plantTime;
    }

    public long getDuration()
    {
        return duration;
    }

    public float getRadius()
    {
        return radius;
    }

}
