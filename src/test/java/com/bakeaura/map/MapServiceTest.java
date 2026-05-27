package com.bakeaura.map;

import com.bakeaura.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapServiceTest {

    @Test
    void calculateDistanceReturnsZeroForSameLocation() {
        MapService mapService = mapService();

        double distance = mapService.calculateDistance(28.6139, 77.2090, 28.6139, 77.2090);

        assertThat(distance).isZero();
    }

    @Test
    void calculateDistanceReturnsApproximateDistanceBetweenKnownPoints() {
        MapService mapService = mapService();

        double distance = mapService.calculateDistance(28.6139, 77.2090, 28.7041, 77.1025);

        assertThat(distance).isBetween(14.0, 16.0);
    }

    @Test
    void calculateEstimatedRoadDistanceAppliesRoadDistanceFactor() {
        MapService mapService = mapService();

        double distance = mapService.calculateEstimatedRoadDistance(28.6139, 77.2090, 28.7041, 77.1025);

        assertThat(distance).isBetween(18.0, 21.0);
    }

    @Test
    void getEstimatedDeliveryMinutesUsesConfiguredSpeedAndBaseTime() {
        MapService mapService = mapService();

        Integer eta = mapService.getEstimatedDeliveryMinutes(28.6139, 77.2090, 28.7041, 77.1025);

        assertThat(eta).isBetween(47, 50);
    }

    @Test
    void isWithinDeliveryRadiusReturnsTrueWhenDistanceIsAllowed() {
        MapService mapService = mapService();

        assertThat(mapService.isWithinDeliveryRadius(9.5)).isTrue();
    }

    @Test
    void isWithinDeliveryRadiusReturnsFalseWhenDistanceIsTooFar() {
        MapService mapService = mapService();

        assertThat(mapService.isWithinDeliveryRadius(10.5)).isFalse();
    }

    @Test
    void invalidCoordinatesAreRejected() {
        MapService mapService = mapService();

        assertThatThrownBy(() -> mapService.calculateDistance(91, 77.2090, 28.7041, 77.1025))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid latitude or longitude");
    }

    @Test
    void invalidConfigurationIsRejected() {
        MapService mapService = new MapService(10, 10, 0, 1.3);

        assertThatThrownBy(mapService::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.delivery.average-speed-kmph must be greater than 0");
    }

    private MapService mapService() {
        MapService mapService = new MapService(10, 10, 30, 1.3);
        mapService.validateConfiguration();
        return mapService;
    }
}
