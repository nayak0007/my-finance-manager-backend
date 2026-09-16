package com.myfinancemanager.controller;

import com.myfinancemanager.dto.user.UpdateProfileRequest;
import com.myfinancemanager.dto.user.UserResponse;
import com.myfinancemanager.security.SecurityUtils;
import com.myfinancemanager.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Account")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get the current user's profile")
    @GetMapping
    public ResponseEntity<UserResponse> profile() {
        return ResponseEntity.ok(userService.getProfile(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Update the current user's profile and preferences")
    @PutMapping
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "Export all of the current user's data as JSON")
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export() {
        return ResponseEntity.ok(userService.exportData(SecurityUtils.currentUserId()));
    }

    /**
     * Deletes the local profile and every financial record owned by it. The Neon Auth identity
     * is deleted separately by the app, which is the only party holding the password.
     */
    @Operation(summary = "Delete the current user's account and all associated data")
    @DeleteMapping
    public ResponseEntity<Void> delete() {
        userService.deleteAccount(SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
