package com.bakeaura.map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MapService {

    @Value("${google.maps.max-delivery-radius-km}")
    private double maxDeliveryRadiusKm;

    @Value("${app.delivery.base-time-minutes}")
    private int baseTimeMinutes;

    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int earthRadiusKm = 6371;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadiusKm * c;
    }

    public boolean isWithinDeliveryRadius(double distanceKm) {
        return distanceKm <= maxDeliveryRadiusKm;
    }

    public Integer getEstimatedDeliveryMinutes(
            double sellerLat,
            double sellerLon,
            double deliveryLat,
            double deliveryLon
    ) {
        double straightLineKm = calculateDistance(sellerLat, sellerLon, deliveryLat, deliveryLon);
        double roadDistanceKm = straightLineKm * 1.3;
        int travelMinutes = (int) ((roadDistanceKm / 30.0) * 60);

        return travelMinutes + baseTimeMinutes;
    }
}
