package com.fraud.detection.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // model string, e.g. "grocery_pos"

    @Column(name = "display_name", nullable = false)
    private String displayName; // friendly label, e.g. "Grocery (in-store)"

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

}