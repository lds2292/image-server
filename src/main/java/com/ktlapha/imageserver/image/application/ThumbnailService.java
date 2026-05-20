package com.ktlapha.imageserver.image.application;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.ImageFilter;
import net.coobird.thumbnailator.resizers.configurations.ScalingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
     * 동시에 같은 파일 요청이 들어와도 생성은 1번만 실행되고 나머지는 결과를 대기한다.
     * 리사이즈 파일은 원본과 분리된 resizeBaseDir 아래 동일한 상대 경로에 저장된다.
     * 파일명 규칙: {base}_w{width}.{ext}  예) banner.jpg + width=200 → banner_w200.jpg
     *
     * @param originalPath 원본 이미지 파일 절대 경로
     * @param relativePath 베이스 디렉토리 기준 상대 경로 (e.g. giftishow_panchok/seller/6/file.jpg)
     * @param width        출력 너비(px); 세로는 비율 유지로 자동 계산
     * @return 리사이즈된 이미지 파일의 경로
     */
    public Path ensureResizedExists(Path originalPath, String relativePath, int width) throws IOException {
        String fileName = originalPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            throw new IllegalArgumentException("Unsupported filename: " + fileName);
        }

        String base = fileName.substring(0, dot);
        String ext = fileName.substring(dot + 1);
        String extLower = ext.toLowerCase();
        String resizedName = base + "_w" + width + "." + ext;

        Path relativeDir = Path.of(relativePath).getParent();
        Path resizedPath = (relativeDir != null)
                ? resizeBaseDir.resolve(relativeDir).resolve(resizedName).normalize()
                : resizeBaseDir.resolve(resizedName).normalize();

        if (Files.exists(resizedPath) && Files.isRegularFile(resizedPath)) {
            return resizedPath;
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
            Path result = doGenerate(originalPath, resizedPath, width, ext, extLower);
            newFuture.complete(result);
            return result;
        } catch (IOException e) {
            newFuture.completeExceptionally(e);
            throw e;
        } finally {
            inProgress.remove(key, newFuture);
        }
    }

    private Path doGenerate(Path originalPath, Path resizedPath, int width, String ext, String extLower) throws IOException {
        Files.createDirectories(resizedPath.getParent());

        String base = resizedPath.getFileName().toString();
        base = base.substring(0, base.lastIndexOf('.'));

        String tmpExt = ("jpg".equals(extLower) || "jpeg".equals(extLower)) ? "jpg" : extLower;
        Path tmpPath = resizedPath.getParent().resolve(base + ".tmp." + UUID.randomUUID() + "." + tmpExt).normalize();
        try {
            Thumbnails.Builder<?> builder = Thumbnails.of(originalPath.toFile())
                    .width(width)
                    .scalingMode(ScalingMode.PROGRESSIVE_BILINEAR)
                    .addFilter(UNSHARP_MASK);

            if ("jpg".equals(extLower) || "jpeg".equals(extLower)) {
                builder.outputFormat("jpg").outputQuality(0.85f);
            } else {
                builder.outputFormat(extLower);
            }

            builder.toFile(tmpPath.toFile());
            atomicMove(tmpPath, resizedPath);
            return resizedPath;
        } catch (Exception e) {
            Files.deleteIfExists(tmpPath);
            log.warn("Thumbnail generation failed for {} with ext {}. Falling back to PNG. cause={}",
                    originalPath.getFileName(), extLower, e.toString());

            String fallbackName = base.replaceAll("_w\\d+$", "") + "_w" + width + ".png";
            Path fallbackPath = resizedPath.getParent().resolve(fallbackName).normalize();
            Path fallbackTmp = resizedPath.getParent().resolve(base + ".tmp." + UUID.randomUUID() + ".png").normalize();
            try {
                Thumbnails.of(originalPath.toFile())
                        .width(width)
                        .scalingMode(ScalingMode.PROGRESSIVE_BILINEAR)
                        .addFilter(UNSHARP_MASK)
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

    private static final ImageFilter UNSHARP_MASK = (BufferedImage img) -> {
        float a = 0.075f;
        float c = 1f + 8 * a;
        Kernel kernel = new Kernel(3, 3, new float[]{
                -a, -a, -a,
                -a,  c, -a,
                -a, -a, -a
        });
        return new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(img, null);
    };

    private void atomicMove(Path src, Path dest) throws IOException {
        try {
            Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
