package com.ktlapha.imageserver.image.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class ThumbnailService {

    /**
     * 요청한 사이즈의 정사각형 리사이즈 파일이 없으면 생성하고, 존재하면 해당 경로를 반환한다.
     * 파일명 규칙: {base}_w{size}.{ext}
     * - 예) gicon.png + size=100 -> gicon_w100.png
     * 최대/최소 사이즈 검증은 호출부에서 수행한다.
     *
     * @param originalPath 원본 이미지 파일 경로
     * @param size         정사각형 한 변의 길이(px)
     * @return 리사이즈된 이미지 파일의 경로
     */
    public Path ensureSquareResizedExists(Path originalPath, int size) throws IOException {
        String fileName = originalPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.')
                ;
        if (dot <= 0 || dot == fileName.length() - 1) {
            throw new IllegalArgumentException("Unsupported filename: " + fileName);
        }

        String base = fileName.substring(0, dot);
        String ext = fileName.substring(dot + 1);

        String resizedName = base + "_w" + size + "." + ext;
        Path resizedPath = originalPath.getParent().resolve(resizedName).normalize();

        if (Files.exists(resizedPath) && Files.isRegularFile(resizedPath)) {
            return resizedPath; // already exists
        }

        BufferedImage original = ImageIO.read(originalPath.toFile());
        if (original == null) {
            throw new IllegalArgumentException("Not an image or unsupported format: " + fileName);
        }

        int width = size;
        int height = size;

        int imageType = original.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(width, height, imageType);
        Graphics2D g2d = resized.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(original, 0, 0, width, height, null);
        } finally {
            g2d.dispose();
        }

        Files.createDirectories(resizedPath.getParent());

        boolean ok = ImageIO.write(resized, ext, resizedPath.toFile());
        if (!ok) {
            String fallback = base + "_w" + size + ".png";
            Path fallbackPath = originalPath.getParent().resolve(fallback);
            ImageIO.write(resized, "png", fallbackPath.toFile());
            return fallbackPath;
        }
        return resizedPath;
    }

    public void ensureThumbnailExists(Path originalPath) throws IOException {
        String fileName = originalPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return; // no extension or invalid
        }

        String base = fileName.substring(0, dot);
        String ext = fileName.substring(dot + 1);

        String thumbName = base + "_thumbnail." + ext;
        Path thumbPath = originalPath.getParent().resolve(thumbName).normalize();

        if (Files.exists(thumbPath) && Files.isRegularFile(thumbPath)) {
            return; // already exists
        }

        BufferedImage original = ImageIO.read(originalPath.toFile());
        if (original == null) {
            return; // not an image or unsupported
        }

        int width = 100;
        int height = 100;

        int imageType = original.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(width, height, imageType);
        Graphics2D g2d = resized.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(original, 0, 0, width, height, null);
        } finally {
            g2d.dispose();
        }

        Files.createDirectories(thumbPath.getParent());

        boolean ok = ImageIO.write(resized, ext, thumbPath.toFile());
        if (!ok) {
            String fallback = base + "_thumbnail.png";
            Path fallbackPath = originalPath.getParent().resolve(fallback);
            ImageIO.write(resized, "png", fallbackPath.toFile());
        }
    }
}
