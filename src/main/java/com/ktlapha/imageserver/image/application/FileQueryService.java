package com.ktlapha.imageserver.image.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileQueryService {

    private final ThumbnailService thumbnailService;

    // Encapsulated NAS base directory
    @Value("${env.image-dir}")
    private Path BASE_DIR;

    public FileResourceResult fetch(String filepath, Integer width) throws IOException {
        if (filepath == null || filepath.isBlank()) {
            throw new IllegalArgumentException("filepath must not be blank");
        }

        Path target = BASE_DIR.resolve(filepath).normalize();
        if (!target.startsWith(BASE_DIR)) {
            throw new IllegalArgumentException("Invalid path");
        }

        File file = target.toFile();
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("File not found: " + filepath);
        }

        // If width specified, validate and use resized square image named base_w{width}.ext
        if (width != null) {
            if (width < 100 || width > 600) {
                throw new IllegalArgumentException("w must be between 100 and 600");
            }
            try {
                Path resized = thumbnailService.ensureSquareResizedExists(target, width);
                file = resized.toFile();
                target = resized;
            } catch (IllegalArgumentException e) {
                // invalid for non-image, propagate as 400 via exception handler
                throw e;
            } catch (Exception e) {
                log.warn("Failed to create/find resized image for {} (w={}): {}", filepath, width, e.getMessage());
            }
        }

        String contentType = Files.probeContentType(target);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        Resource resource = new FileSystemResource(file);
        long contentLength = resource.contentLength();
        return new FileResourceResult(resource, file.getName(), contentType, contentLength);
    }
}
