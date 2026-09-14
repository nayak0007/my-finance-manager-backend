package com.myfinancemanager.controller;

import com.myfinancemanager.common.dto.PageResponse;
import com.myfinancemanager.domain.AutoCaptureStatus;
import com.myfinancemanager.dto.autocapture.AutoCaptureItemRequest;
import com.myfinancemanager.dto.autocapture.AutoCaptureItemResponse;
import com.myfinancemanager.dto.autocapture.AutoCaptureReviewRequest;
import com.myfinancemanager.dto.autocapture.AutoCaptureSettingsRequest;
import com.myfinancemanager.dto.autocapture.AutoCaptureSettingsResponse;
import com.myfinancemanager.security.SecurityUtils;
import com.myfinancemanager.service.AutoCaptureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Auto-Capture")
@RestController
@RequestMapping("/api/v1/auto-capture")
@RequiredArgsConstructor
public class AutoCaptureController {

    private final AutoCaptureService autoCaptureService;

    @Operation(summary = "Submit a device-parsed SMS/email transaction to the review queue")
    @PostMapping
    public ResponseEntity<AutoCaptureItemResponse> submit(@Valid @RequestBody AutoCaptureItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(autoCaptureService.submit(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "List items in the auto-capture review queue")
    @GetMapping
    public ResponseEntity<PageResponse<AutoCaptureItemResponse>> list(
            @RequestParam(required = false) AutoCaptureStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(autoCaptureService.list(SecurityUtils.currentUserId(), status, pageable));
    }

    @Operation(summary = "Get auto-capture sender and permission settings")
    @GetMapping("/settings")
    public ResponseEntity<AutoCaptureSettingsResponse> getSettings() {
        return ResponseEntity.ok(autoCaptureService.getSettings(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Update auto-capture settings")
    @PutMapping("/settings")
    public ResponseEntity<AutoCaptureSettingsResponse> updateSettings(
            @Valid @RequestBody AutoCaptureSettingsRequest request) {
        return ResponseEntity.ok(autoCaptureService.updateSettings(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "Get a review queue item")
    @GetMapping("/{id}")
    public ResponseEntity<AutoCaptureItemResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(autoCaptureService.get(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "Update a pending review queue item")
    @PutMapping("/{id}")
    public ResponseEntity<AutoCaptureItemResponse> update(@PathVariable UUID id,
                                                          @Valid @RequestBody AutoCaptureItemRequest request) {
        return ResponseEntity.ok(autoCaptureService.update(SecurityUtils.currentUserId(), id, request));
    }

    @Operation(summary = "Confirm a queued item and create the corresponding record")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<AutoCaptureItemResponse> confirm(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AutoCaptureReviewRequest request) {
        return ResponseEntity.ok(autoCaptureService.confirm(SecurityUtils.currentUserId(), id, request));
    }

    @Operation(summary = "Reject a queued item")
    @PostMapping("/{id}/reject")
    public ResponseEntity<AutoCaptureItemResponse> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(autoCaptureService.reject(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "Delete a queued item")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        autoCaptureService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
