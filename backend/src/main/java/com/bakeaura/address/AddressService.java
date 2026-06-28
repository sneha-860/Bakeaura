package com.bakeaura.address;

import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    public List<AddressDto> getAddresses(Long userId) {
        User user = getUser(userId);
        return addressRepository.findByUserOrderByDefaultAddressDescCreatedAtDesc(user).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AddressDto createAddress(Long userId, AddressRequest request) {
        User user = getUser(userId);
        Address address = new Address();
        address.setUser(user);
        apply(address, request);
        if (Boolean.TRUE.equals(address.getDefaultAddress())) {
            clearDefaults(user, null);
        }
        return toDto(addressRepository.save(address));
    }

    @Transactional
    public AddressDto updateAddress(Long userId, Long id, AddressRequest request) {
        User user = getUser(userId);
        Address address = getOwnedAddress(id, user);
        apply(address, request);
        if (Boolean.TRUE.equals(address.getDefaultAddress())) {
            clearDefaults(user, id);
        }
        return toDto(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long userId, Long id) {
        User user = getUser(userId);
        addressRepository.delete(getOwnedAddress(id, user));
    }

    @Transactional
    public AddressDto setDefault(Long userId, Long id) {
        User user = getUser(userId);
        Address address = getOwnedAddress(id, user);
        clearDefaults(user, id);
        address.setDefaultAddress(true);
        return toDto(addressRepository.save(address));
    }

    private void apply(Address address, AddressRequest request) {
        address.setLabel(request.getLabel());
        address.setAddressLine(request.getAddressLine());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        address.setDefaultAddress(Boolean.TRUE.equals(request.getDefaultAddress()));
    }

    private void clearDefaults(User user, Long exceptId) {
        addressRepository.findByUserOrderByDefaultAddressDescCreatedAtDesc(user).forEach(address -> {
            if (exceptId == null || !address.getId().equals(exceptId)) {
                address.setDefaultAddress(false);
                addressRepository.save(address);
            }
        });
    }

    private Address getOwnedAddress(Long id, User user) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (!address.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied");
        }
        return address;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private AddressDto toDto(Address address) {
        return new AddressDto(
                address.getId(),
                address.getLabel(),
                address.getAddressLine(),
                address.getLatitude(),
                address.getLongitude(),
                address.getDefaultAddress()
        );
    }
}
