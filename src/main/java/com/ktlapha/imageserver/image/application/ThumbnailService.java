package com.ktlapha.imageserver.image.application;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.ImageFilter;
import net.coobird.thumbnailator.resizers.configurations.ScalingMode;
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

@Slf4j
@Service
public class ThumbnailService {

    /**
     * 너비를 width로 리사이즈한 파일이 없으면 생성하고, 존재하면 해당 경로를 반환한다.
     * 세로는 원본 비율을 유지하여 자동 계산된다.
     * 파일명 규칙: {base}_w{width}.{ext}  예) banner.jpg + width=200 → banner_w200.jpg
     * 최대/최소 검증은 호출부에서 수행한다.
     *
     * @param originalPath 원본 이미지 파일 경로
     * @param width        출력 너비(px); 세로는 비율 유지로 자동 계산
     * @return 리사이즈된 이미지 파일의 경로
     */
    public Path ensureResizedExists(Path originalPath, int width) throws IOException {
        int size = width;
        String fileName = originalPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            throw new IllegalArgumentException("Unsupported filename: " + fileName);
        }

        String base = fileName.substring(0, dot);
        String ext = fileName.substring(dot + 1);
        String extLower = ext.toLowerCase();

        String resizedName = base + "_w" + size + "." + ext;
        Path resizedPath = originalPath.getParent().resolve(resizedName).normalize();

        if (Files.exists(resizedPath) && Files.isRegularFile(resizedPath)) {
            return resizedPath;
        }

        Files.createDirectories(resizedPath.getParent());

        // 임시 파일에 먼저 쓴 후 원자적으로 이동 — 동시 요청이 와도 항상 완전한 파일만 노출된다.
        // 주의: Thumbnailator는 outputFormat과 파일 확장자가 불일치하면 자동으로 확장자를 덧붙이므로,
        //       임시 파일명도 최종 확장자로 끝나야 한다. 예) {base}_w{size}.tmp.{uuid}.jpg
        String tmpExt = ("jpg".equals(extLower) || "jpeg".equals(extLower)) ? "jpg" : extLower;
        Path tmpPath = originalPath.getParent().resolve(base + "_w" + size + ".tmp." + UUID.randomUUID() + "." + tmpExt).normalize();
        try {
            Thumbnails.Builder<?> builder = Thumbnails.of(originalPath.toFile())
                    .width(size)
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
            log.warn("Thumbnail generation failed for {} with ext {}. Falling back to PNG. cause={}", fileName, extLower, e.toString());
            String fallback = base + "_w" + size + ".png";
            Path fallbackPath = originalPath.getParent().resolve(fallback).normalize();
            Path fallbackTmp = originalPath.getParent().resolve(base + "_w" + size + ".tmp." + UUID.randomUUID() + ".png").normalize();
            Files.createDirectories(fallbackPath.getParent());
            try {
                Thumbnails.of(originalPath.toFile())
                        .width(size)
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

    // 리사이즈 후 윤곽선 복원을 위한 언샤프 마스크 (amount≈0.6, radius=1px 상당)
    // 커널: 중심에 1+8*a 배치, 주변 8칸에 -a 배치 (a = amount/8 ≈ 0.075)
    private static final ImageFilter UNSHARP_MASK = (BufferedImage img) -> {
        float a = 0.075f; // amount / 8
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
            // 파일시스템이 atomic move를 미지원하는 경우 non-atomic으로 폴백 (NAS 등)
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
