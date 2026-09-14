package com.myfinancemanager.service;

import com.myfinancemanager.common.dto.PageResponse;
import com.myfinancemanager.common.exception.BadRequestException;
import com.myfinancemanager.common.exception.ResourceNotFoundException;
import com.myfinancemanager.domain.ImportBatch;
import com.myfinancemanager.domain.ImportedTransaction;
import com.myfinancemanager.domain.ImportStatus;
import com.myfinancemanager.domain.StagedTransactionStatus;
import com.myfinancemanager.domain.TransactionOrigin;
import com.myfinancemanager.dto.imports.CommitImportRequest;
import com.myfinancemanager.dto.imports.ImportBatchDetailResponse;
import com.myfinancemanager.dto.imports.ImportBatchResponse;
import com.myfinancemanager.dto.imports.ImportedTransactionResponse;
import com.myfinancemanager.repository.ImportBatchRepository;
import com.myfinancemanager.repository.ImportedTransactionRepository;
import com.myfinancemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImportService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "csv", "xlsx", "xls");

    private final ImportBatchRepository importBatchRepository;
    private final ImportedTransactionRepository importedTransactionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ImportProcessingService importProcessingService;
    private final TransactionWriter transactionWriter;

    public ImportBatchResponse createBatch(UUID userId, MultipartFile file) {
        String filename = file.getOriginalFilename();
        validateExtension(filename);

        ImportBatch batch = new ImportBatch();
        batch.setUser(userRepository.getReferenceById(userId));
        batch.setSourceFileName(filename == null ? "statement" : filename);
        batch.setContentType(file.getContentType());
        batch.setFileSize(file.getSize());
        batch.setStatus(ImportStatus.QUEUED);
        batch = importBatchRepository.save(batch);

        Path stored = fileStorageService.store(userId, batch.getId(), file);
        batch.setStoragePath(stored.toString());
        batch = importBatchRepository.save(batch);

        importProcessingService.process(batch.getId());
        return ImportBatchResponse.from(batch);
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportBatchResponse> list(UUID userId, Pageable pageable) {
        return PageResponse.from(
                importBatchRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                ImportBatchResponse::from);
    }

    @Transactional(readOnly = true)
    public ImportBatchResponse get(UUID userId, UUID batchId) {
        return ImportBatchResponse.from(load(userId, batchId));
    }

    @Transactional(readOnly = true)
    public ImportBatchDetailResponse detail(UUID userId, UUID batchId) {
        ImportBatch batch = load(userId, batchId);
        List<ImportedTransactionResponse> transactions =
                importedTransactionRepository.findByBatchIdOrderByTransactionDateAscIdAsc(batchId).stream()
                        .map(ImportedTransactionResponse::from)
                        .toList();
        return ImportBatchDetailResponse.of(ImportBatchResponse.from(batch), transactions);
    }

    @Transactional
    public ImportBatchResponse commit(UUID userId, UUID batchId, CommitImportRequest request) {
        ImportBatch batch = load(userId, batchId);
        if (batch.getStatus() != ImportStatus.READY_FOR_REVIEW && batch.getStatus() != ImportStatus.PARTIALLY_COMMITTED) {
            throw new BadRequestException("Import batch is not ready for review");
        }

        Map<UUID, CommitImportRequest.CommitItem> overrides = new LinkedHashMap<>();
        if (request != null && request.items() != null) {
            for (CommitImportRequest.CommitItem item : request.items()) {
                if (item.id() != null) {
                    overrides.put(item.id(), item);
                }
            }
        }

        List<ImportedTransaction> transactions =
                importedTransactionRepository.findByBatchIdOrderByTransactionDateAscIdAsc(batchId);

        for (ImportedTransaction transaction : transactions) {
            if (transaction.getStatus() != StagedTransactionStatus.PENDING) {
                continue;
            }
            CommitImportRequest.CommitItem item = overrides.get(transaction.getId());
            if (overrides.isEmpty()) {
                if (transaction.isDuplicate()) {
                    continue;
                }
            } else if (item == null) {
                continue;
            }
            if (item != null) {
                applyOverrides(transaction, item);
            }
            if (item != null && Boolean.FALSE.equals(item.include())) {
                transaction.setStatus(StagedTransactionStatus.REJECTED);
                continue;
            }
            UUID recordId = transactionWriter.write(
                    userId,
                    transaction.getTransactionType(),
                    transaction.getAmount(),
                    transaction.getTransactionDate(),
                    transaction.getDescription(),
                    transaction.getMerchant(),
                    transaction.getCategory(),
                    transaction.getPaymentMode(),
                    transaction.getSource(),
                    TransactionOrigin.IMPORT,
                    "Imported from " + transaction.getBatch().getSourceFileName());
            transaction.setStatus(StagedTransactionStatus.COMMITTED);
            transaction.setCommittedRecordId(recordId);
        }

        importedTransactionRepository.saveAll(transactions);
        updateBatchStatus(batch, transactions);
        return ImportBatchResponse.from(importBatchRepository.save(batch));
    }

    @Transactional
    public void delete(UUID userId, UUID batchId) {
        ImportBatch batch = load(userId, batchId);
        Path stored = batch.getStoragePath() != null ? Path.of(batch.getStoragePath()) : null;
        importBatchRepository.delete(batch);
        if (stored != null) {
            fileStorageService.deleteQuietly(stored);
        }
    }

    private void updateBatchStatus(ImportBatch batch, List<ImportedTransaction> transactions) {
        int pending = 0;
        int committed = 0;
        int rejected = 0;
        for (ImportedTransaction transaction : transactions) {
            switch (transaction.getStatus()) {
                case PENDING -> pending++;
                case COMMITTED -> committed++;
                case REJECTED -> rejected++;
            }
        }
        if (pending > 0) {
            batch.setStatus(ImportStatus.READY_FOR_REVIEW);
        } else if (committed > 0 && rejected == 0) {
            batch.setStatus(ImportStatus.COMMITTED);
        } else {
            batch.setStatus(ImportStatus.PARTIALLY_COMMITTED);
        }
    }

    private void applyOverrides(ImportedTransaction transaction, CommitImportRequest.CommitItem item) {
        if (item.transactionType() != null) {
            transaction.setTransactionType(item.transactionType());
        }
        if (item.transactionDate() != null) {
            transaction.setTransactionDate(item.transactionDate());
        }
        if (item.description() != null) {
            transaction.setDescription(item.description());
        }
        if (item.merchant() != null) {
            transaction.setMerchant(item.merchant());
        }
        if (item.amount() != null) {
            transaction.setAmount(item.amount());
        }
        if (item.category() != null) {
            transaction.setCategory(item.category());
        }
        if (item.paymentMode() != null) {
            transaction.setPaymentMode(item.paymentMode());
        }
        if (item.source() != null) {
            transaction.setSource(item.source());
        }
    }

    private ImportBatch load(UUID userId, UUID batchId) {
        return importBatchRepository.findByIdAndUserId(batchId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found"));
    }

    private void validateExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BadRequestException("A statement file with a valid extension is required");
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported statement format. Allowed: pdf, csv, xlsx, xls");
        }
    }
}
