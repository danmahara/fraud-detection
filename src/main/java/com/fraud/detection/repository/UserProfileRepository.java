package com.fraud.detection.repository;

import com.fraud.detection.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    // Find a user's profile via the FK column user_id -> user.id.
    Optional<UserProfile> findByUser_Id(Long userId);
}