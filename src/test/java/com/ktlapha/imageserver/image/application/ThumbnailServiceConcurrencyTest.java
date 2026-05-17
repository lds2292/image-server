package com.ktlapha.imageserver.image.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ThumbnailServiceConcurrencyTest {

    private static final int THREAD_COUNT = 30;

    private final ThumbnailService thumbnailService = new ThumbnailService();

    @TempDir
    Path tempDir;

    /**
     * 핵심 경합 시나리오: 동일 파일 + 동일 w 파라미터로 N개 스레드가 동시에 진입.
     * - 모든 스레드가 정상적으로 경로를 반환해야 한다.
     * - 결과 파일이 완전한(valid) 이미지여야 한다.
     * - 너비가 targetWidth와 일치하고, 세로는 원본 비율(800×600 → 4:3)을 유지해야 한다.
     * - 임시 파일(.tmp.*)이 잔류하지 않아야 한다.
     */
    @Test
    void sameFileAndWidthConcurrently_allThreadsSucceedAndFileIsValid() throws Exception {
        // 원본: 800×600 (4:3)
        Path original = createTestImage("source.jpg", 800, 600, "jpg");
        int targetWidth = 200;
        int expectedHeight = 150; // 600 * (200/800) = 150

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<Path>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                startGate.await(); // 모든 스레드를 동시에 출발시켜 경합 극대화
                return thumbnailService.ensureResizedExists(original, targetWidth);
            }));
        }

        startGate.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);
        assertThat(finished).as("스레드 실행이 30초 내에 완료되지 않았습니다").isTrue();

        // 모든 스레드가 예외 없이 완료
        List<Path> results = new ArrayList<>();
        for (Future<Path> f : futures) {
            results.add(f.get()); // ExecutionException 발생 시 테스트 실패
        }

        // 모든 스레드가 동일한 경로를 반환
        Set<Path> distinct = results.stream().collect(Collectors.toSet());
        assertThat(distinct).hasSize(1);

        // 결과 파일이 완전한 이미지인지 검증 (corrupted이면 ImageIO.read → null)
        Path resultPath = distinct.iterator().next();
        assertThat(Files.exists(resultPath)).isTrue();

        BufferedImage resultImage = ImageIO.read(resultPath.toFile());
        assertThat(resultImage).as("결과 파일이 손상(corrupt)되어 이미지를 읽을 수 없습니다").isNotNull();
        assertThat(resultImage.getWidth()).isEqualTo(targetWidth);
        assertThat(resultImage.getHeight()).isEqualTo(expectedHeight);

        // 잔류 임시 파일 없음
        assertNoTmpFilesIn(tempDir);
    }

    /**
     * PNG 원본에 대해서도 동일한 경합 시나리오 검증.
     * 원본: 500×400 (5:4) → width=150 → 결과: 150×120
     */
    @Test
    void sameFileAndWidthConcurrently_png_allThreadsSucceedAndFileIsValid() throws Exception {
        Path original = createTestImage("source.png", 500, 400, "png");
        int targetWidth = 150;
        int expectedHeight = 120; // 400 * (150/500) = 120

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<Path>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                startGate.await();
                return thumbnailService.ensureResizedExists(original, targetWidth);
            }));
        }

        startGate.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        for (Future<Path> f : futures) {
            Path result = f.get();
            BufferedImage img = ImageIO.read(result.toFile());
            assertThat(img).as("PNG 결과 파일 손상").isNotNull();
            assertThat(img.getWidth()).isEqualTo(targetWidth);
            assertThat(img.getHeight()).isEqualTo(expectedHeight);
        }

        assertNoTmpFilesIn(tempDir);
    }

    /**
     * 여러 다른 너비를 동시에 요청하는 시나리오.
     * 각 너비별로 올바른 파일이 생성되어야 한다.
     * 원본: 1000×800 (5:4)
     */
    @Test
    void differentWidthsConcurrently_eachWidthProducesCorrectFile() throws Exception {
        int origW = 1000, origH = 800;
        Path original = createTestImage("multi.jpg", origW, origH, "jpg");
        int[] widths = {80, 100, 150, 200, 300, 400};

        ExecutorService executor = Executors.newFixedThreadPool(widths.length * 5);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Path>> futures = new ArrayList<>();

        for (int i = 0; i < widths.length * 5; i++) {
            int w = widths[i % widths.length];
            futures.add(executor.submit(() -> {
                startGate.await();
                return thumbnailService.ensureResizedExists(original, w);
            }));
        }

        startGate.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        for (int i = 0; i < futures.size(); i++) {
            int expectedWidth = widths[i % widths.length];
            int expectedHeight = origH * expectedWidth / origW;
            Path result = futures.get(i).get();
            BufferedImage img = ImageIO.read(result.toFile());
            assertThat(img).as("너비 %d 결과 파일 손상", expectedWidth).isNotNull();
            assertThat(img.getWidth()).isEqualTo(expectedWidth);
            assertThat(img.getHeight()).isEqualTo(expectedHeight);
        }

        assertNoTmpFilesIn(tempDir);
    }

    /**
     * 이미 썸네일이 존재하는 상태에서 동시 요청이 와도 안전한지 검증 (캐시 히트 경로).
     * 캐시된 파일이 덮어써지지 않아야 한다.
     */
    @Test
    void cachedThumbnailConcurrently_allThreadsReturnExistingFile() throws Exception {
        Path original = createTestImage("cached.jpg", 600, 400, "jpg");
        int targetWidth = 100;

        // 먼저 썸네일을 미리 생성
        Path prebuilt = thumbnailService.ensureResizedExists(original, targetWidth);
        long prebuiltLastModified = Files.getLastModifiedTime(prebuilt).toMillis();

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<Path>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                startGate.await();
                return thumbnailService.ensureResizedExists(original, targetWidth);
            }));
        }

        startGate.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        for (Future<Path> f : futures) {
            assertThat(f.get()).isEqualTo(prebuilt);
        }

        // 캐시된 파일이 덮어써지지 않아야 한다
        long afterLastModified = Files.getLastModifiedTime(prebuilt).toMillis();
        assertThat(afterLastModified).isEqualTo(prebuiltLastModified);

        assertNoTmpFilesIn(tempDir);
    }

    // --- helpers ---

    private Path createTestImage(String filename, int width, int height, String format) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.RED);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("test", width / 2 - 20, height / 2);
        g.dispose();

        Path path = tempDir.resolve(filename);
        ImageIO.write(img, format, path.toFile());
        return path;
    }

    private void assertNoTmpFilesIn(Path dir) throws IOException {
        List<Path> tmpFiles = Files.list(dir)
                .filter(p -> p.getFileName().toString().contains(".tmp."))
                .collect(Collectors.toList());
        assertThat(tmpFiles)
                .as("임시 파일이 잔류하고 있습니다: %s", tmpFiles)
                .isEmpty();
    }
}
