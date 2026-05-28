package com.bakeaura.seller;

import com.bakeaura.enums.Role;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.product.ProductRepository;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerService {
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public List<SellerProfileDto> getSellers() {
        return userRepository.findByRoleAndIsActiveTrue(Role.SELLER).stream()
                .map(this::toDto)
                .toList();
    }

    public SellerProfileDto getSeller(Long id) {
        User seller = userRepository.findById(id)
                .filter(user -> user.getRole() == Role.SELLER)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        return toDto(seller);
    }

    public List<SellerProfileDto> getNearbySellers(double latitude, double longitude, double radius) {
        return userRepository.findByRoleAndIsActiveTrue(Role.SELLER).stream()
                .filter(seller -> seller.getLatitude() != null && seller.getLongitude() != null)
                .filter(seller -> calculateDistance(latitude, longitude, seller.getLatitude(), seller.getLongitude()) <= radius)
                .map(this::toDto)
                .toList();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula
        double earthRadius = 6371.0; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private SellerProfileDto toDto(User seller) {
        long productCount = productRepository.findBySellerId(seller.getId()).stream()
                .filter(product -> Boolean.TRUE.equals(product.getIsAvailable()))
                .count();
        return new SellerProfileDto(
                seller.getId(),
                seller.getName(),
                seller.getEmail(),
                seller.getLatitude(),
                seller.getLongitude(),
                productCount
        );
    }
}
