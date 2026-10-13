package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMobileNumber(String mobileNumber);
    List<User> findByRole(UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    List<User> findByRoleAndSellerApprovalStatus(UserRole role, SellerApprovalStatus status);
    long countByRole(UserRole role);
    long countByRoleAndSellerApprovalStatus(UserRole role, SellerApprovalStatus status);
}

