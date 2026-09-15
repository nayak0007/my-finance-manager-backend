package com.myfinancemanager.service;

import com.myfinancemanager.common.dto.PageResponse;
import com.myfinancemanager.common.exception.BadRequestException;
import com.myfinancemanager.common.exception.ResourceNotFoundException;
import com.myfinancemanager.domain.AutoCaptureQueueItem;
import com.myfinancemanager.domain.AutoCaptureSettings;
import com.myfinancemanager.domain.AutoCaptureStatus;
import com.myfinancemanager.domain.PaymentMode;
import com.myfinancemanager.domain.StagedTransactionType;
import com.myfinancemanager.domain.TransactionOrigin;
import com.myfinancemanager.dto.autocapture.AutoCaptureItemRequest;
import com.myfinancemanager.dto.autocapture.AutoCaptureItemResponse;
import com.myfinancemanager.dto.autocapture.AutoCaptureReviewRequest;
import com.myfinancemanager.dto.autocapture.AutoCaptureSettingsRequest;
import com.myfinancemanager.dto.autocapture.AutoCaptureSettingsResponse;
import com.myfinancemanager.repository.AutoCaptureQueueRepository;
import com.myfinancemanager.repository.AutoCaptureSettingsRepository;
import com.myfinancemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoCaptureService {

    private final AutoCaptureQueueRepository queueRepository;
    private final AutoCaptureSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final TransactionWriter transactionWriter;

    @Transactional
    public AutoCaptureItemResponse submit(UUID userId, AutoCaptureItemRequest request) {
        AutoCaptureQueueItem item = new AutoCaptureQueueItem();
        item.setUser(userRepository.getReferenceById(userId));
        item.setSourceType(request.sourceType());
        item.setSender(request.sender());
        item.setRawText(request.rawText());
        item.setParsedType(request.parsedType());
        item.setParsedData(request.parsedData() != null ? new LinkedHashMap<>(request.parsedData()) : new LinkedHashMap<>());
        item.setConfidence(request.confidence());
        item.setStatus(AutoCaptureStatus.PENDING);
        return AutoCaptureItemResponse.from(queueRepository.save(item));
    }

    @Transactional(readOnly = true)
    public PageResponse<AutoCaptureItemResponse> list(UUID userId, AutoCaptureStatus status, Pageable pageable) {
        if (status != null) {
            return PageResponse.from(
                    queueRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable),
                    AutoCaptureItemResponse::from);
        }
        return PageResponse.from(
                queueRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                AutoCaptureItemResponse::from);
    }

    @Transactional(readOnly = true)
    public AutoCaptureItemResponse get(UUID userId, UUID id) {
        return AutoCaptureItemResponse.from(load(userId, id));
    }

    @Transactional
    public AutoCaptureItemResponse update(UUID userId, UUID id, AutoCaptureItemRequest request) {
        AutoCaptureQueueItem item = load(userId, id);
        if (item.getStatus() != AutoCaptureStatus.PENDING) {
            throw new BadRequestException("Only pending items can be edited");
        }
        if (request.sender() != null) {
            item.setSender(request.sender());
        }
        if (request.rawText() != null) {
            item.setRawText(request.rawText());
        }
        if (request.parsedType() != null) {
            item.setParsedType(request.parsedType());
        }
        if (request.parsedData() != null) {
            item.setParsedData(new LinkedHashMap<>(request.parsedData()));
        }
        if (request.confidence() != null) {
            item.setConfidence(request.confidence());
        }
        return AutoCaptureItemResponse.from(queueRepository.save(item));
    }

    @Transactional
    public AutoCaptureItemResponse confirm(UUID userId, UUID id, AutoCaptureReviewRequest review) {
        AutoCaptureQueueItem item = load(userId, id);
        if (item.getStatus() != AutoCaptureStatus.PENDING) {
            throw new BadRequestException("Only pending items can be confirmed");
        }
        Map<String, Object> data = item.getParsedData();
        StagedTransactionType type = review != null && review.transactionType() != null
                ? review.transactionType()
                : resolveType(item.getParsedType(), data);
        BigDecimal amount = review != null && review.amount() != null
                ? review.amount()
                : resolveAmount(data);
        LocalDate date = review != null && review.transactionDate() != null
                ? review.transactionDate()
                : resolveDate(data);
        String description = review != null && review.description() != null
                ? review.description()
                : resolveString(data, "description", "narration", "body");
        String merchant = review != null && review.merchant() != null
                ? review.merchant()
                : resolveString(data, "merchant", "payee");
        String category = review != null && review.category() != null
                ? review.category()
                : resolveString(data, "category");
        PaymentMode paymentMode = review != null && review.paymentMode() != null
                ? review.paymentMode()
                : resolvePaymentMode(resolveString(data, "paymentMode", "mode"));
        String source = review != null && review.source() != null
                ? review.source()
                : resolveString(data, "source", "account");

        UUID recordId = transactionWriter.write(
                userId, type, amount, date, description, merchant, category, paymentMode, source,
                item.getSourceType() == com.myfinancemanager.domain.AutoCaptureSource.EMAIL
                        ? TransactionOrigin.EMAIL
                        : TransactionOrigin.SMS,
                "Auto-detected from " + item.getSourceType());
        item.setStatus(AutoCaptureStatus.CONFIRMED);
        item.setCommittedRecordId(recordId);
        return AutoCaptureItemResponse.from(queueRepository.save(item));
    }

    @Transactional
    public AutoCaptureItemResponse reject(UUID userId, UUID id) {
        AutoCaptureQueueItem item = load(userId, id);
        if (item.getStatus() != AutoCaptureStatus.PENDING) {
            throw new BadRequestException("Only pending items can be rejected");
        }
        item.setStatus(AutoCaptureStatus.REJECTED);
        return AutoCaptureItemResponse.from(queueRepository.save(item));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        queueRepository.delete(load(userId, id));
    }

    @Transactional
    public AutoCaptureSettingsResponse getSettings(UUID userId) {
        return AutoCaptureSettingsResponse.from(loadOrCreateSettings(userId));
    }

    @Transactional
    public AutoCaptureSettingsResponse updateSettings(UUID userId, AutoCaptureSettingsRequest request) {
        AutoCaptureSettings settings = loadOrCreateSettings(userId);
        if (request.enabled() != null) {
            settings.setEnabled(request.enabled());
        }
        if (request.smsEnabled() != null) {
            settings.setSmsEnabled(request.smsEnabled());
        }
        if (request.emailEnabled() != null) {
            settings.setEmailEnabled(request.emailEnabled());
        }
        if (request.senderAllowList() != null) {
            settings.setSenderAllowList(new ArrayList<>(request.senderAllowList()));
        }
        if (request.senderBlockList() != null) {
            settings.setSenderBlockList(new ArrayList<>(request.senderBlockList()));
        }
        return AutoCaptureSettingsResponse.from(settingsRepository.save(settings));
    }

    private AutoCaptureSettings loadOrCreateSettings(UUID userId) {
        return settingsRepository.findByUserId(userId).orElseGet(() -> {
            AutoCaptureSettings settings = new AutoCaptureSettings();
            settings.setUser(userRepository.getReferenceById(userId));
            return settingsRepository.save(settings);
        });
    }

    private AutoCaptureQueueItem load(UUID userId, UUID id) {
        return queueRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Auto-capture item not found"));
    }

    private StagedTransactionType resolveType(StagedTransactionType parsedType, Map<String, Object> data) {
        if (parsedType != null) {
            return parsedType;
        }
        String raw = resolveString(data, "type", "transactionType");
        if (raw != null) {
            try {
                return StagedTransactionType.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to income/expense detection below
            }
        }
        String amount = resolveString(data, "direction", "drCr");
        if (amount != null && amount.toLowerCase(Locale.ROOT).contains("c")) {
            return StagedTransactionType.INCOME;
        }
        return StagedTransactionType.EXPENSE;
    }

    private BigDecimal resolveAmount(Map<String, Object> data) {
        Object value = firstValue(data, "amount", "transactionAmount", "value");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String cleaned = value.toString().replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned).abs();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate resolveDate(Map<String, Object> data) {
        String raw = resolveString(data, "date", "transactionDate");
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private PaymentMode resolvePaymentMode(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return PaymentMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PaymentMode.OTHER;
        }
    }

    private String resolveString(Map<String, Object> data, String... keys) {
        Object value = firstValue(data, keys);
        return value == null ? null : value.toString();
    }

    private Object firstValue(Map<String, Object> data, String... keys) {
        if (data == null) {
            return null;
        }
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return null;
    }

}
