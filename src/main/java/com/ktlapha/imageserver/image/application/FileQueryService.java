package com.ktlapha.imageserver.image.application;

import com.ktlapha.imageserver.common.exception.InvalidWidthException;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileQueryService {

    private final ThumbnailService thumbnailService;

    private static final Set<Integer> ALLOWED_WIDTHS = Set.of(45, 80, 100, 150, 200, 250, 300, 400, 600, 800);

    @Value("${env.image-dir}")
    private Path BASE_DIR;

    public FileResourceResult fetch(String filepath, Integer width, boolean acceptWebP) throws IOException {
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

        // w 파라미터가 있으면 허용 사이즈 검증 후 리사이즈 파일({base}_w{width}.{ext|webp}) 반환
        if (width != null) {
            if (!ALLOWED_WIDTHS.contains(width)) {
                throw new InvalidWidthException(width);
            }
            try {
                Path resized = thumbnailService.ensureResizedExists(target, filepath, width, acceptWebP);
                file = resized.toFile();
                target = resized;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to create/find resized image for {} (w={}): {}", filepath, width, e.getMessage(), e);
            }
        } else if (acceptWebP) {
            try {
                Path webpPath = thumbnailService.ensureWebPExists(target, filepath);
                file = webpPath.toFile();
                target = webpPath;
            } catch (Exception e) {
                log.warn("WebP conversion failed for {}: {}", filepath, e.getMessage(), e);
                // 실패 시 원본 그대로 서빙
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
