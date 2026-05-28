package com.bakeaura.map;

import com.bakeaura.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MapService {

    private static final int EARTH_RADIUS_KM = 6371;

    private final double maxDeliveryRadiusKm;
    private final int baseTimeMinutes;
    private final double averageSpeedKmph;
    private final double roadDistanceFactor;

    public MapService(
            @Value("${app.delivery.max-radius-km:10}") double maxDeliveryRadiusKm,
            @Value("${app.delivery.base-time-minutes:10}") int baseTimeMinutes,
            @Value("${app.delivery.average-speed-kmph:30}") double averageSpeedKmph,
            @Value("${app.delivery.road-distance-factor:1.3}") double roadDistanceFactor
    ) {
        this.maxDeliveryRadiusKm = maxDeliveryRadiusKm;
        this.baseTimeMinutes = baseTimeMinutes;
        this.averageSpeedKmph = averageSpeedKmph;
        this.roadDistanceFactor = roadDistanceFactor;
    }

    @PostConstruct
    void validateConfiguration() {
        if (maxDeliveryRadiusKm <= 0) {
            throw new IllegalStateException("app.delivery.max-radius-km must be greater than 0");
        }
        if (baseTimeMinutes < 0) {
            throw new IllegalStateException("app.delivery.base-time-minutes cannot be negative");
        }
        if (averageSpeedKmph <= 0) {
            throw new IllegalStateException("app.delivery.average-speed-kmph must be greater than 0");
        }
        if (roadDistanceFactor < 1) {
            throw new IllegalStateException("app.delivery.road-distance-factor must be greater than or equal to 1");
        }
    }

    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        validateCoordinates(lat1, lon1);
        validateCoordinates(lat2, lon2);

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    public double calculateEstimatedRoadDistance(double lat1, double lon1, double lat2, double lon2) {
        return calculateDistance(lat1, lon1, lat2, lon2) * roadDistanceFactor;
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
        double roadDistanceKm = calculateEstimatedRoadDistance(sellerLat, sellerLon, deliveryLat, deliveryLon);
        int travelMinutes = (int) Math.ceil((roadDistanceKm / averageSpeedKmph) * 60);

        return travelMinutes + baseTimeMinutes;
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BadRequestException("Invalid latitude or longitude");
        }
    }
}
