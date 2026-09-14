package com.myfinancemanager.controller;

import com.myfinancemanager.common.dto.PageResponse;
import com.myfinancemanager.domain.InsightStatus;
import com.myfinancemanager.dto.insights.AIInsightResponse;
import com.myfinancemanager.dto.insights.GenerateInsightsRequest;
import com.myfinancemanager.dto.insights.UpdateInsightStatusRequest;
import com.myfinancemanager.security.SecurityUtils;
import com.myfinancemanager.service.InsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "AI Insights")
@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @Operation(summary = "List generated financial insights")
    @GetMapping
    public ResponseEntity<PageResponse<AIInsightResponse>> list(
            @RequestParam(required = false) InsightStatus status,
            @PageableDefault(size = 20, sort = "generatedAt") Pageable pageable) {
        return ResponseEntity.ok(insightService.list(SecurityUtils.currentUserId(), status, pageable));
    }

    @Operation(summary = "Generate fresh insights from the user's financial data")
    @PostMapping("/generate")
    public ResponseEntity<List<AIInsightResponse>> generate(
            @Valid @RequestBody(required = false) GenerateInsightsRequest request) {
        return ResponseEntity.ok(insightService.generate(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "Get an insight")
    @GetMapping("/{id}")
    public ResponseEntity<AIInsightResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(insightService.get(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "Save or dismiss an insight")
    @PatchMapping("/{id}/status")
    public ResponseEntity<AIInsightResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInsightStatusRequest request) {
        return ResponseEntity.ok(insightService.updateStatus(SecurityUtils.currentUserId(), id, request.status()));
    }

    @Operation(summary = "Delete an insight")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        insightService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
