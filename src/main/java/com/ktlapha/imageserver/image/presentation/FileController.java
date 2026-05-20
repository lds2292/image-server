package com.ktlapha.imageserver.image.presentation;

import com.ktlapha.imageserver.image.application.FileQueryService;
import com.ktlapha.imageserver.image.application.FileResourceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileQueryService fileQueryService;

    @Value("${env.biz-dir}")
    private String bizDir;

    @Value("${env.panchok-dir}")
    private String panchokDir;

    // /files/{filename} → {image-dir}/{biz-dir}/{filename}
    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> getBizFile(
            @PathVariable String filename,
            @RequestParam(name = "w", required = false) Integer width) throws IOException {

        return serve(bizDir + "/" + filename, width);
    }

    // /Resource/{*path} → {image-dir}/{path}
    @GetMapping("/Resource/{*path}")
    public ResponseEntity<Resource> getGoods(
            @PathVariable String path,
            @RequestParam(name = "w", required = false) Integer width) throws IOException {

        return serve(path.substring(1), width);
    }

    // /image_panc/{*path} → {image-dir}/{panchok-dir}/{path}
    @GetMapping("/image_panc/{*path}")
    public ResponseEntity<Resource> getPanchokImage(
            @PathVariable String path,
            @RequestParam(name = "w", required = false) Integer width) throws IOException {

        return serve(panchokDir + path, width);
    }

    private ResponseEntity<Resource> serve(String filepath, Integer width) throws IOException {
        FileResourceResult result = fileQueryService.fetch(filepath, width);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + result.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(result.getContentType()))
                .contentLength(result.getContentLength())
                .body(result.getResource());
    }
}
