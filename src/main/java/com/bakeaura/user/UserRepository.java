package com.bakeaura.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleAndIsActiveTrue(com.bakeaura.enums.Role role);

    List<User> findByRole(com.bakeaura.enums.Role role);
}
