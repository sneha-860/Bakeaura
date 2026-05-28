package com.bakeaura.address;

import com.bakeaura.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserOrderByDefaultAddressDescCreatedAtDesc(User user);
}
