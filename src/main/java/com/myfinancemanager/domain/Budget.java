package com.myfinancemanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A monthly spending limit for one expense category.
 *
 * The category is the natural key rather than the row id: a user has at most one budget per
 * category, which is what makes the client's upsert idempotent and lets a delete be replayed
 * safely. Categories are stored as the same upper-cased strings expense records use.
 */
@Getter
@Setter
@Entity
@Table(name = "budgets", indexes = {
        @Index(name = "idx_budgets_user", columnList = "user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_budgets_user_category", columnNames = {"user_id", "category"})
})
public class Budget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "monthly_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyLimit;
}
