package com.myfinancemanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "imported_transactions", indexes = {
        @Index(name = "idx_imported_tx_batch", columnList = "batch_id, status")
})
public class ImportedTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private StagedTransactionType transactionType;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "merchant", length = 200)
    private String merchant;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "category", length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 30)
    private PaymentMode paymentMode;

    @Column(name = "source", length = 200)
    private String source;

    @Column(name = "raw_line", length = 2000)
    private String rawLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StagedTransactionStatus status = StagedTransactionStatus.PENDING;

    @Column(name = "duplicate", nullable = false)
    private boolean duplicate = false;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "committed_record_id")
    private UUID committedRecordId;

    @Column(name = "fingerprint", length = 64)
    private String fingerprint;
}
