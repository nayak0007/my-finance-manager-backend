package com.myfinancemanager.controller;

import com.myfinancemanager.common.dto.PageResponse;
import com.myfinancemanager.dto.imports.CommitImportRequest;
import com.myfinancemanager.dto.imports.ImportBatchDetailResponse;
import com.myfinancemanager.dto.imports.ImportBatchResponse;
import com.myfinancemanager.security.SecurityUtils;
import com.myfinancemanager.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Smart Import")
@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @Operation(summary = "Upload a bank/credit-card statement for parsing")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportBatchResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(importService.createBatch(SecurityUtils.currentUserId(), file));
    }

    @Operation(summary = "List statement import batches")
    @GetMapping
    public ResponseEntity<PageResponse<ImportBatchResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(importService.list(SecurityUtils.currentUserId(), pageable));
    }

    @Operation(summary = "Get an import batch status")
    @GetMapping("/{id}")
    public ResponseEntity<ImportBatchResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(importService.get(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "Get an import batch with its parsed transactions for review")
    @GetMapping("/{id}/detail")
    public ResponseEntity<ImportBatchDetailResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(importService.detail(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "Commit reviewed transactions into income/expense/investment records")
    @PostMapping("/{id}/commit")
    public ResponseEntity<ImportBatchResponse> commit(@PathVariable UUID id,
                                                      @Valid @RequestBody(required = false) CommitImportRequest request) {
        return ResponseEntity.ok(importService.commit(SecurityUtils.currentUserId(), id, request));
    }

    @Operation(summary = "Delete an import batch and its staged transactions")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        importService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
