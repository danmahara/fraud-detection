package com.fraud.detection.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.fraud.detection.entity.Merchant;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    // For "pay by handle" lookups.
    Optional<Merchant> findByEmail(String email);

    Optional<Merchant> findByPhone(String phone);

    // For the customer dropdown — only active merchants.
    List<Merchant> findByActiveTrueOrderByNameAsc();

    // Uniqueness checks for create/edit.
    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    // Single merchant WITH its category (avoids lazy-init on toView).
    @Query("SELECT m FROM Merchant m JOIN FETCH m.category WHERE m.id = :id")
    Optional<Merchant> findByIdWithCategory(@org.springframework.data.repository.query.Param("id") Long id);

    // Fetch merchants WITH their category in one query (avoids lazy-init on
    // toView).
    @Query("SELECT m FROM Merchant m JOIN FETCH m.category ORDER BY m.name ASC")
    List<Merchant> findAllWithCategory();

    // Active-only, with category, for the customer dropdown.
    @Query("SELECT m FROM Merchant m JOIN FETCH m.category WHERE m.active = true ORDER BY m.name ASC")
    List<Merchant> findActiveWithCategory();
}