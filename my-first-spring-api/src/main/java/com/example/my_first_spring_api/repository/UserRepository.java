package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMobileNumber(String mobileNumber);
}
