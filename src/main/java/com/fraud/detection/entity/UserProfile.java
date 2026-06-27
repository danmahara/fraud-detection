package com.fraud.detection.entity;

import com.fraud.detection.entity.enums.ProfileMaturity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "total_transactions", nullable = false)
    private Integer totalTransactions = 0;

    @Column(name = "avg_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal avgAmount = BigDecimal.ZERO;

    @Column(name = "max_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAmount = BigDecimal.ZERO;

    @Column(name = "large_purchase_count", nullable = false)
    private Integer largePurchaseCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "large_purchase_categories", nullable = false, columnDefinition = "jsonb")
    private List<String> largePurchaseCategories = new ArrayList<>();

    @Column(name = "last_known_device")
    private String lastKnownDevice;

    @Column(name = "home_lat", precision = 9, scale = 6)
    private BigDecimal homeLat;

    @Column(name = "home_lon", precision = 9, scale = 6)
    private BigDecimal homeLon;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "known_locations", nullable = false, columnDefinition = "jsonb")
    private List<String> knownLocations = new ArrayList<>();

    @Column(name = "has_late_night_history", nullable = false)
    private Boolean hasLateNightHistory = false;

    @Column(name = "account_age_days", nullable = false)
    private Integer accountAgeDays = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_maturity", nullable = false, length = 20)
    private ProfileMaturity profileMaturity = ProfileMaturity.NEW;

    // ---- V10: cardholder attributes the ML model needs ----
    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "gender", length = 1)
    private String gender;

    @Column(name = "city_pop")
    private Integer cityPop;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}