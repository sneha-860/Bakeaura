package com.bakeaura.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<User> findAll(Pageable pageable);

    Page<User> findByRole(com.bakeaura.enums.Role role, Pageable pageable);

    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByPendingEmailToken(String token);

    Optional<User> findByResetPasswordToken(String token);
}
