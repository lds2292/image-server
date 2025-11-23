package com.ktlapha.imageserver.image.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
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
        String extLower = ext.toLowerCase();

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

        BufferedImage resized = resizeHighQuality(original, width, height);

        Files.createDirectories(resizedPath.getParent());

        boolean ok = writeWithFormatQuality(resized, extLower, resizedPath);
        if (!ok) {
            String fallback = base + "_w" + size + ".png";
            Path fallbackPath = originalPath.getParent().resolve(fallback);
            ImageIO.write(resized, "png", fallbackPath.toFile());
            return fallbackPath;
        }
        return resizedPath;
    }

    /**
     * 고품질 리사이즈: 다운스케일 시 멀티스텝(점진적) 스케일링과 Bicubic 보간을 사용한다.
     */
    private BufferedImage resizeHighQuality(BufferedImage src, int targetW, int targetH) {
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        int w = src.getWidth();
        int h = src.getHeight();

        // 업스케일 또는 동일 크기: 한 번에 고품질 스케일
        if (targetW >= w && targetH >= h) {
            BufferedImage dst = new BufferedImage(targetW, targetH, type);
            Graphics2D g2 = dst.createGraphics();
            try {
                applyQualityHints(g2);
                g2.drawImage(src, 0, 0, targetW, targetH, null);
            } finally {
                g2.dispose();
            }
            return dst;
        }

        BufferedImage current = src;
        int cw = w;
        int ch = h;
        // 다운스케일: 두 배씩 줄이면서 목표 크기에 가까워질 때까지 반복
        while (cw / 2 >= targetW && ch / 2 >= targetH) {
            cw = Math.max(targetW, cw / 2);
            ch = Math.max(targetH, ch / 2);
            BufferedImage tmp = new BufferedImage(cw, ch, type);
            Graphics2D g2 = tmp.createGraphics();
            try {
                applyQualityHints(g2);
                g2.drawImage(current, 0, 0, cw, ch, null);
            } finally {
                g2.dispose();
            }
            if (current != src) {
                current.flush();
            }
            current = tmp;
        }

        // 마지막 목표 크기로 한 번 더
        if (cw != targetW || ch != targetH) {
            BufferedImage dst = new BufferedImage(targetW, targetH, type);
            Graphics2D g2 = dst.createGraphics();
            try {
                applyQualityHints(g2);
                g2.drawImage(current, 0, 0, targetW, targetH, null);
            } finally {
                g2.dispose();
            }
            if (current != src) {
                current.flush();
            }
            current = dst;
        }

        return current;
    }

    private void applyQualityHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * 포맷에 맞게 저장. JPEG의 경우 명시적 품질(기본 0.92)을 사용하고 알파 채널을 제거(흰 배경)한다.
     * 반환값: true면 지정 포맷으로 저장 성공, false면 표준 ImageIO가 writer를 찾지 못한 경우.
     */
    private boolean writeWithFormatQuality(BufferedImage image, String extLower, Path outPath) throws IOException {
        if ("jpg".equals(extLower) || "jpeg".equals(extLower)) {
            // JPEG은 알파 미지원 → 알파가 있다면 흰 배경으로 합성
            if (image.getColorModel().hasAlpha()) {
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = rgb.createGraphics();
                try {
                    g2.setColor(Color.WHITE);
                    g2.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    applyQualityHints(g2);
                    g2.drawImage(image, 0, 0, null);
                } finally {
                    g2.dispose();
                }
                image = rgb;
            } else if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = rgb.createGraphics();
                try {
                    applyQualityHints(g2);
                    g2.drawImage(image, 0, 0, null);
                } finally {
                    g2.dispose();
                }
                image = rgb;
            }

            ImageWriter writer = null;
            try {
                writer = ImageIO.getImageWritersByFormatName("jpg").next();
            } catch (Exception ignore) {
                // no writer
            }
            if (writer == null) {
                return false;
            }
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outPath.toFile())) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.92f); // 고품질 JPEG
                }
                writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
                writer.dispose();
                return true;
            }
        } else {
            return ImageIO.write(image, extLower, outPath.toFile());
        }
    }
}
