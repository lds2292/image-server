package com.ktlapha.imageserver.image.application;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.resizers.configurations.ScalingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class ThumbnailService {

    @Value("${env.resize-dir}")
    private Path resizeBaseDir;

    // 동일 경로에 대한 중복 생성을 방지하기 위한 진행 중 작업 추적 맵
    private final ConcurrentHashMap<String, CompletableFuture<Path>> inProgress = new ConcurrentHashMap<>();

    /**
     * 리사이즈 파일이 없으면 생성하고, 존재하면 해당 경로를 반환한다.
     * webp=true 이면 파일명: {base}_w{width}.webp
     * webp=false 이면 파일명: {base}_w{width}.{ext}
     */
    public Path ensureResizedExists(Path originalPath, String relativePath, int width, boolean webp) throws IOException {
        String fileName = originalPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            throw new IllegalArgumentException("Unsupported filename: " + fileName);
        }

        String base = fileName.substring(0, dot);
        String ext = fileName.substring(dot + 1);
        String extLower = ext.toLowerCase();
        String resizedName = webp
                ? base + "_w" + width + ".webp"
                : base + "_w" + width + "." + ext;

        Path relativeDir = Path.of(relativePath).getParent();
        Path resizedPath = (relativeDir != null)
                ? resizeBaseDir.resolve(relativeDir).resolve(resizedName).normalize()
                : resizeBaseDir.resolve(resizedName).normalize();

        if (Files.exists(resizedPath) && Files.isRegularFile(resizedPath)) {
            return resizedPath;
        }

        // 요청 너비가 원본 너비 이상이면 업스케일 — WebP면 원본을 WebP로 변환, 아니면 원본 그대로 반환
        if (width >= readImageWidth(originalPath)) {
            return webp ? ensureWebPExists(originalPath, relativePath) : originalPath;
        }

        String key = resizedPath.toString();
        CompletableFuture<Path> newFuture = new CompletableFuture<>();
        CompletableFuture<Path> existing = inProgress.putIfAbsent(key, newFuture);

        if (existing != null) {
            try {
                return existing.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) throw (IOException) cause;
                throw new IOException(cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for thumbnail generation", e);
            }
        }

        try {
            Path result = doGenerate(originalPath, resizedPath, width, extLower, webp);
            newFuture.complete(result);
            return result;
        } catch (IOException e) {
            newFuture.completeExceptionally(e);
            throw e;
        } finally {
            inProgress.remove(key, newFuture);
        }
    }

    /**
     * 원본 이미지를 리사이징 없이 WebP로 변환한다.
     * 저장 경로: {resize-dir}/{relativeDir}/{base}.webp
     */
    public Path ensureWebPExists(Path originalPath, String relativePath) throws IOException {
        String fileName = originalPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            throw new IllegalArgumentException("Unsupported filename: " + fileName);
        }

        String base = fileName.substring(0, dot);
        String webpName = base + ".webp";

        Path relativeDir = Path.of(relativePath).getParent();
        Path webpPath = (relativeDir != null)
                ? resizeBaseDir.resolve(relativeDir).resolve(webpName).normalize()
                : resizeBaseDir.resolve(webpName).normalize();

        if (Files.exists(webpPath) && Files.isRegularFile(webpPath)) {
            return webpPath;
        }

        String key = webpPath.toString();
        CompletableFuture<Path> newFuture = new CompletableFuture<>();
        CompletableFuture<Path> existing = inProgress.putIfAbsent(key, newFuture);

        if (existing != null) {
            try {
                return existing.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) throw (IOException) cause;
                throw new IOException(cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for WebP conversion", e);
            }
        }

        try {
            Path result = doConvertToWebP(originalPath, webpPath);
            newFuture.complete(result);
            return result;
        } catch (IOException e) {
            newFuture.completeExceptionally(e);
            throw e;
        } finally {
            inProgress.remove(key, newFuture);
        }
    }

    private Path doGenerate(Path originalPath, Path resizedPath, int width, String extLower, boolean webp) throws IOException {
        Files.createDirectories(resizedPath.getParent());

        String base = resizedPath.getFileName().toString();
        base = base.substring(0, base.lastIndexOf('.'));

        String tmpExt = webp ? "webp" : (("jpg".equals(extLower) || "jpeg".equals(extLower)) ? "jpg" : extLower);
        Path tmpPath = resizedPath.getParent().resolve(base + ".tmp." + UUID.randomUUID() + "." + tmpExt).normalize();
        try {
            Thumbnails.Builder<?> builder = Thumbnails.of(originalPath.toFile())
                    .width(width)
                    .scalingMode(ScalingMode.PROGRESSIVE_BILINEAR);

            if (webp) {
                builder.outputFormat("webp");
            } else if ("jpg".equals(extLower) || "jpeg".equals(extLower)) {
                builder.outputFormat("jpg").outputQuality(0.92f);
            } else {
                builder.outputFormat(extLower);
            }

            builder.toFile(tmpPath.toFile());
            atomicMove(tmpPath, resizedPath);
            return resizedPath;
        } catch (Exception e) {
            Files.deleteIfExists(tmpPath);
            if (webp) {
                throw new IOException("WebP resize failed for " + originalPath.getFileName(), e);
            }
            log.warn("Thumbnail generation failed for {} with ext {}. Falling back to PNG. cause={}",
                    originalPath.getFileName(), extLower, e.toString());

            String fallbackName = base.replaceAll("_w\\d+$", "") + "_w" + width + ".png";
            Path fallbackPath = resizedPath.getParent().resolve(fallbackName).normalize();
            Path fallbackTmp = resizedPath.getParent().resolve(base + ".tmp." + UUID.randomUUID() + ".png").normalize();
            try {
                Thumbnails.of(originalPath.toFile())
                        .width(width)
                        .scalingMode(ScalingMode.PROGRESSIVE_BILINEAR)
                        .outputFormat("png")
                        .toFile(fallbackTmp.toFile());
                atomicMove(fallbackTmp, fallbackPath);
            } catch (Exception ex) {
                Files.deleteIfExists(fallbackTmp);
                throw ex;
            }
            return fallbackPath;
        }
    }

    private Path doConvertToWebP(Path originalPath, Path webpPath) throws IOException {
        Files.createDirectories(webpPath.getParent());

        String base = webpPath.getFileName().toString();
        base = base.substring(0, base.lastIndexOf('.'));
        Path tmpPath = webpPath.getParent().resolve(base + ".tmp." + UUID.randomUUID() + ".webp").normalize();
        try {
            Thumbnails.of(originalPath.toFile())
                    .scale(1.0)
                    .outputFormat("webp")
                    .toFile(tmpPath.toFile());
            atomicMove(tmpPath, webpPath);
            return webpPath;
        } catch (Exception e) {
            Files.deleteIfExists(tmpPath);
            throw new IOException("WebP conversion failed for " + originalPath.getFileName(), e);
        }
    }

    // 이미지 전체를 메모리에 올리지 않고 헤더만 읽어 너비를 반환
    private int readImageWidth(Path imagePath) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(imagePath.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis, true, true);
                    return reader.getWidth(0);
                } finally {
                    reader.dispose();
                }
            }
        }
        throw new IOException("이미지 크기를 읽을 수 없습니다: " + imagePath);
    }

    private void atomicMove(Path src, Path dest) throws IOException {
        try {
            Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
