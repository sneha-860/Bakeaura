package com.bakeaura.roleapplication;

import com.bakeaura.enums.ApplicationStatus;
import com.bakeaura.enums.Role;
import com.bakeaura.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleApplicationRepository extends JpaRepository<RoleApplication, Long> {
    boolean existsByUserAndRequestedRoleAndStatus(User user, Role requestedRole, ApplicationStatus status);

    boolean existsByUserAndStatus(User user, ApplicationStatus status);

    List<RoleApplication> findByUserOrderByCreatedAtDesc(User user);

    List<RoleApplication> findByStatusOrderByCreatedAtDesc(ApplicationStatus status);

    List<RoleApplication> findAllByOrderByCreatedAtDesc();
}
