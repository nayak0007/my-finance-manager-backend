package com.myfinancemanager.service;

import com.myfinancemanager.domain.ExpenseRecord;
import com.myfinancemanager.domain.ImportBatch;
import com.myfinancemanager.domain.ImportedTransaction;
import com.myfinancemanager.domain.IncomeRecord;
import com.myfinancemanager.domain.ImportStatus;
import com.myfinancemanager.domain.InvestmentRecord;
import com.myfinancemanager.domain.StagedTransactionType;
import com.myfinancemanager.integration.statement.ParsedTransaction;
import com.myfinancemanager.repository.ExpenseRepository;
import com.myfinancemanager.repository.ImportBatchRepository;
import com.myfinancemanager.repository.IncomeRepository;
import com.myfinancemanager.repository.InvestmentRepository;
import com.myfinancemanager.service.util.TransactionFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportProcessingService {

    private final ImportBatchRepository importBatchRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final InvestmentRepository investmentRepository;
    private final StatementParserService statementParserService;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;

    @Async("importTaskExecutor")
    public void process(UUID batchId) {
        ImportBatch batch = transactionTemplate.execute(status -> {
            ImportBatch loaded = importBatchRepository.findById(batchId).orElse(null);
            if (loaded == null) {
                return null;
            }
            if (loaded.getStatus() == ImportStatus.CANCELLED) {
                // The user aborted between the upload and this pass; nothing to parse.
                return null;
            }
            loaded.setStatus(ImportStatus.PROCESSING);
            return importBatchRepository.save(loaded);
        });
        if (batch == null) {
            log.info("Import batch {} no longer exists or was cancelled; skipping processing", batchId);
            return;
        }

        Path file = batch.getStoragePath() != null ? Path.of(batch.getStoragePath()) : null;
        long start = System.currentTimeMillis();
        try {
            StatementParserService.ParseResult result =
                    statementParserService.parse(file, batch.getSourceFileName(), batch.getContentType());
            transactionTemplate.executeWithoutResult(status -> persistParsed(batchId, result));
            log.info("[Import] Batch {} (\"{}\") reached {} with {} staged transaction(s) in {} ms",
                    batchId, batch.getSourceFileName(), ImportStatus.READY_FOR_REVIEW,
                    result.transactions().size(), System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.warn("[Import] Batch {} (\"{}\") failed after {} ms: {}",
                    batchId, batch.getSourceFileName(), System.currentTimeMillis() - start,
                    ex.getMessage(), ex);
            transactionTemplate.executeWithoutResult(status ->
                    importBatchRepository.findById(batchId).ifPresent(loaded -> {
                        if (loaded.getStatus() == ImportStatus.CANCELLED) {
                            // A cancel wins over the parse failure: the user already gave up on it.
                            log.info("[Import] Batch {} was cancelled while parsing; keeping the cancelled state",
                                    batchId);
                            return;
                        }
                        loaded.setStatus(ImportStatus.FAILED);
                        loaded.setErrorMessage(truncate(ex.getMessage(), 2000));
                        importBatchRepository.save(loaded);
                    }));
        } finally {
            if (file != null) {
                fileStorageService.deleteQuietly(file);
            }
        }
    }

    private void persistParsed(UUID batchId, StatementParserService.ParseResult result) {
        ImportBatch batch = importBatchRepository.findById(batchId).orElseThrow();
        if (batch.getStatus() == ImportStatus.CANCELLED) {
            // Cancelled while the parser ran: the rows must not be staged into the batch.
            log.info("[Import] Batch {} was cancelled before its {} parsed row(s) were staged",
                    batchId, result.transactions().size());
            return;
        }
        List<ParsedTransaction> parsed = result.transactions();

        Set<String> batchFingerprints = new HashSet<>();
        int skipped = 0;
        for (ParsedTransaction transaction : parsed) {
            if (transaction.amount() == null || transaction.amount().signum() <= 0) {
                skipped++;
                continue;
            }
            ImportedTransaction staged = new ImportedTransaction();
            staged.setBatch(batch);
            staged.setTransactionType(transaction.type());
            staged.setTransactionDate(transaction.transactionDate());
            staged.setDescription(transaction.description());
            staged.setMerchant(transaction.merchant());
            staged.setAmount(transaction.amount());
            staged.setCategory(transaction.category());
            staged.setPaymentMode(transaction.paymentMode());
            staged.setSource(transaction.source());
            staged.setRawLine(truncate(transaction.rawLine(), 2000));
            staged.setConfidence(transaction.confidence());

            String fingerprint = TransactionFingerprint.of(
                    transaction.type(),
                    transaction.amount(),
                    transaction.transactionDate(),
                    transaction.merchant() != null ? transaction.merchant() : transaction.description());
            staged.setFingerprint(fingerprint);

            UUID existingId = findExistingRecordId(batch.getUser().getId(), transaction.type(), fingerprint);
            if (existingId != null) {
                staged.setDuplicate(true);
                staged.setDuplicateOfId(existingId);
            } else if (!batchFingerprints.add(fingerprint)) {
                staged.setDuplicate(true);
            }

            batch.getTransactions().add(staged);
        }

        batch.setExtractionMethod(result.method());
        batch.setTotalTransactions(batch.getTransactions().size());
        batch.setStatus(batch.getTransactions().isEmpty() ? ImportStatus.FAILED : ImportStatus.READY_FOR_REVIEW);
        if (batch.getTransactions().isEmpty()) {
            batch.setErrorMessage("No transactions with a valid amount were found in the statement");
            log.warn("[Import] Batch {} staged no transactions from the parser's {} row(s)",
                    batchId, parsed.size());
        } else if (skipped > 0) {
            batch.setErrorMessage(skipped + " statement row(s) without a valid amount were skipped");
            log.info("[Import] Batch {} staged {} row(s); {} parser row(s) skipped for having no amount",
                    batchId, batch.getTransactions().size(), skipped);
        }
        importBatchRepository.save(batch);
    }

    private UUID findExistingRecordId(UUID userId, StagedTransactionType type, String fingerprint) {
        return switch (type) {
            case INCOME -> incomeRepository.findFirstByUserIdAndFingerprint(userId, fingerprint)
                    .map(IncomeRecord::getId).orElse(null);
            case EXPENSE -> expenseRepository.findFirstByUserIdAndFingerprint(userId, fingerprint)
                    .map(ExpenseRecord::getId).orElse(null);
            case INVESTMENT -> investmentRepository.findFirstByUserIdAndFingerprint(userId, fingerprint)
                    .map(InvestmentRecord::getId).orElse(null);
        };
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
