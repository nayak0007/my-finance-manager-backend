package com.myfinancemanager.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "import_batches", indexes = {
        @Index(name = "idx_import_batches_user", columnList = "user_id, created_at")
})
public class ImportBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_file_name", nullable = false, length = 500)
    private String sourceFileName;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImportStatus status = ImportStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_method", length = 30)
    private ExtractionMethod extractionMethod = ExtractionMethod.NONE;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "total_transactions", nullable = false)
    private int totalTransactions = 0;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ImportedTransaction> transactions = new ArrayList<>();
}
