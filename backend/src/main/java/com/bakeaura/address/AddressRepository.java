package com.bakeaura.address;

import com.bakeaura.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserOrderByDefaultAddressDescCreatedAtDesc(User user);

    @Modifying
    @Query("UPDATE Address a SET a.defaultAddress = false WHERE a.user = :user AND a.id <> :exceptId")
    void clearDefaultsExcept(@Param("user") User user, @Param("exceptId") Long exceptId);

    @Modifying
    @Query("UPDATE Address a SET a.defaultAddress = false WHERE a.user = :user")
    void clearAllDefaults(@Param("user") User user);
}
