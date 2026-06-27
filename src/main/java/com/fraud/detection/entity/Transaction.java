package com.fraud.detection.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fraud.detection.entity.enums.Channel;
import com.fraud.detection.entity.enums.RiskLevel;
import com.fraud.detection.entity.enums.TransactionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FIX: Map as a proper relational object referencing the Account entity
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Size(max = 255)
    @Column(nullable = false)
    private String merchant;

    @Size(max = 60)
    @Column(name = "merchant_category", length = 60)
    private String merchantCategory;

    @Column(name = "location_lat", precision = 9, scale = 6)
    private BigDecimal locationLat;

    @Column(name = "location_lon", precision = 9, scale = 6)
    private BigDecimal locationLon;

    @Size(max = 255)
    @Column(name = "device_id")
    private String deviceId;

    @Size(max = 45)
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @NotNull
    @Builder.Default
    @Column(name = "transaction_time", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime transactionTime = OffsetDateTime.now();

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "fraud_score")
    private Double fraudScore;

    @Column(name = "isolation_forest_score")
    private Double isolationForestScore;

    @Column(name = "xgboost_probability")
    private Double xgboostProbability;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "flag_reasons", nullable = false, columnDefinition = "jsonb")
    private List<String> flagReasons = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @NotNull
    @Builder.Default
    @Column(name = "is_flagged", nullable = false)
    private Boolean isFlagged = false;

    @Column(name = "user_confirmed")
    private Boolean userConfirmed;

    // FIX: Map as a proper relational object referencing the User entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @NotNull
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Channel channel;
}
