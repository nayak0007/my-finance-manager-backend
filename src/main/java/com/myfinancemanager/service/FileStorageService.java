package com.myfinancemanager.service;

import com.myfinancemanager.common.exception.BadRequestException;
import com.myfinancemanager.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final Path rootDirectory;

    public FileStorageService(StorageProperties properties) {
        this.rootDirectory = Paths.get(properties.location()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create storage directory: " + rootDirectory, ex);
        }
    }

    public Path store(UUID userId, UUID batchId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }
        String safeName = sanitize(file.getOriginalFilename());
        Path userDirectory = rootDirectory.resolve(userId.toString()).resolve(batchId.toString());
        try {
            Files.createDirectories(userDirectory);
            Path target = userDirectory.resolve(safeName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store uploaded statement", ex);
        }
    }

    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Unable to delete stored file {}: {}", path, ex.getMessage());
        }
    }

    private String sanitize(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "statement";
        }
        String name = Paths.get(originalName).getFileName().toString();
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
