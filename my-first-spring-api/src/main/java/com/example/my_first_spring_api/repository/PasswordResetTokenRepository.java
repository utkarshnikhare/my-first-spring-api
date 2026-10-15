package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.PasswordResetToken;
import com.example.my_first_spring_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Repository for the single-use, time-limited reset tokens. */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByUser(User user);

    void deleteByUserId(Long userId);

    void deleteByToken(String token);
}
