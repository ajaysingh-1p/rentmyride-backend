package com.rentmyride.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * All 38 districts of Bihar with their headquarters' approximate coordinates.
 * Used to let customers pick any pickup/drop location within Bihar and to
 * calculate the road distance (km) between them for pricing.
 *
 * NOTE: Coordinates are for the district headquarters (city/town), not every
 * individual village — Bihar has thousands of villages, so this gives full
 * state-wide coverage at the district/city level. If a specific village needs
 * to be added, add it here with its own lat/lng.
 */
public class BiharLocations {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String name;     // e.g. "Patna", "Muzaffarpur"
        private String district; // district it belongs to
        private double latitude;
        private double longitude;
    }

    public static final List<Location> ALL = List.of(
            new Location("Araria", "Araria", 26.1500, 87.5167),
            new Location("Arwal", "Arwal", 25.2500, 84.6833),
            new Location("Aurangabad", "Aurangabad", 24.7500, 84.3667),
            new Location("Banka", "Banka", 24.8833, 86.9167),
            new Location("Begusarai", "Begusarai", 25.4167, 86.1333),
            new Location("Bhagalpur", "Bhagalpur", 25.2500, 86.9833),
            new Location("Ara (Bhojpur)", "Bhojpur", 25.5500, 84.6667),
            new Location("Buxar", "Buxar", 25.5667, 83.9833),
            new Location("Darbhanga", "Darbhanga", 26.1667, 85.9000),
            new Location("Motihari (East Champaran)", "East Champaran", 26.6500, 84.9167),
            new Location("Gaya", "Gaya", 24.7833, 85.0000),
            new Location("Gopalganj", "Gopalganj", 26.4667, 84.4333),
            new Location("Jamui", "Jamui", 24.9333, 86.2167),
            new Location("Jehanabad", "Jehanabad", 25.2167, 84.9833),
            new Location("Bhabua (Kaimur)", "Kaimur", 25.0500, 83.6167),
            new Location("Katihar", "Katihar", 25.5333, 87.5667),
            new Location("Khagaria", "Khagaria", 25.5000, 86.4667),
            new Location("Kishanganj", "Kishanganj", 26.1000, 87.9500),
            new Location("Lakhisarai", "Lakhisarai", 25.1667, 86.0833),
            new Location("Madhepura", "Madhepura", 25.9167, 86.7833),
            new Location("Madhubani", "Madhubani", 26.3500, 86.0667),
            new Location("Munger", "Munger", 25.3833, 86.4667),
            new Location("Muzaffarpur", "Muzaffarpur", 26.1167, 85.4000),
            new Location("Bihar Sharif (Nalanda)", "Nalanda", 25.1972, 85.5194),
            new Location("Nawada", "Nawada", 24.8833, 85.5333),
            new Location("Patna", "Patna", 25.5941, 85.1376),
            new Location("Purnia", "Purnia", 25.7833, 87.4667),
            new Location("Sasaram (Rohtas)", "Rohtas", 24.9500, 84.0167),
            new Location("Saharsa", "Saharsa", 25.8833, 86.6000),
            new Location("Samastipur", "Samastipur", 25.8667, 85.7833),
            new Location("Chapra (Saran)", "Saran", 25.7833, 84.7500),
            new Location("Sheikhpura", "Sheikhpura", 25.1333, 85.8500),
            new Location("Sheohar", "Sheohar", 26.5167, 85.2833),
            new Location("Sitamarhi", "Sitamarhi", 26.5833, 85.4833),
            new Location("Siwan", "Siwan", 26.2167, 84.3667),
            new Location("Supaul", "Supaul", 26.1167, 86.6000),
            new Location("Hajipur (Vaishali)", "Vaishali", 25.6833, 85.2167),
            new Location("Bettiah (West Champaran)", "West Champaran", 26.8000, 84.5000)
    );

    public static Location findByName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return ALL.stream()
                .filter(l -> l.getName().equalsIgnoreCase(trimmed) || l.getDistrict().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(null);
    }
}
