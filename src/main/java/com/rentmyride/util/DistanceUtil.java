package com.rentmyride.util;

import java.util.List;

public class DistanceUtil {

    private static final double EARTH_RADIUS_KM = 6371.0;

    // Straight-line (great-circle) distances underestimate real road distance.
    // Applying a standard road-detour factor gives a more realistic driving estimate.
    private static final double ROAD_DETOUR_FACTOR = 1.3;

    /**
     * Returns the estimated road distance in km between two coordinates, rounded to 1 decimal place.
     */
    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightLineKm = EARTH_RADIUS_KM * c;

        double roadDistanceKm = straightLineKm * ROAD_DETOUR_FACTOR;
        return Math.round(roadDistanceKm * 10) / 10.0;
    }

    /**
     * Total distance travelling through an ordered list of locations (pickup → via stops → drop),
     * and — for a round trip — the distance back from the last stop to the first (pickup).
     */
    public static double calculateRouteDistanceKm(List<BiharLocations.Location> orderedStops, boolean roundTrip) {
        if (orderedStops == null || orderedStops.size() < 2) return 0.0;

        double total = 0.0;
        for (int i = 0; i < orderedStops.size() - 1; i++) {
            BiharLocations.Location a = orderedStops.get(i);
            BiharLocations.Location b = orderedStops.get(i + 1);
            total += calculateDistanceKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        }

        if (roundTrip) {
            BiharLocations.Location last = orderedStops.get(orderedStops.size() - 1);
            BiharLocations.Location first = orderedStops.get(0);
            total += calculateDistanceKm(last.getLatitude(), last.getLongitude(), first.getLatitude(), first.getLongitude());
        }

        return Math.round(total * 10) / 10.0;
    }
}
