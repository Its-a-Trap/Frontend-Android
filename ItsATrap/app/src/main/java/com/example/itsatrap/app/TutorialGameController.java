package com.example.itsatrap.app;

import android.location.LocationManager;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by maegereg on 6/1/14.
 * Alternate version of the gamecontroller. User plantables can only be added through the addUserPlantable method (thus
 * changelocation will fail). Additionally, sweeping always returns results.
 */
public class TutorialGameController extends GameController {

    public TutorialGameController(User curUser, LocationManager locManager, MapActivity mapActivity) {
        super(curUser, locManager, mapActivity);
    }

    /**
     * Returns a shallow copy of the user plantables so it can't be modified
     * @return
     */
    @Override
    public List<Plantable> getUserPlantables() {
        return new ArrayList<Plantable>(userPlantables);
    }

    @Override
    public List<Plantable> getEnemyPlantablesWithinRadius(LatLng currentLocation, float radius) {
        List<Plantable> toReturn = super.getEnemyPlantablesWithinRadius(currentLocation, radius);
        if (toReturn.size() == 0) {
            toReturn.add(new Plantable("", "", new LatLng(currentLocation.latitude+.001, currentLocation.longitude+.001), new Date(), 1000, 10));
            toReturn.add(new Plantable("", "", new LatLng(currentLocation.latitude-.001, currentLocation.longitude-.001), new Date(), 1000, 10));
        }
        return toReturn;
    }
}
