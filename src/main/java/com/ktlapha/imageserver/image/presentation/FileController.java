package com.ktlapha.imageserver.image.presentation;

import com.ktlapha.imageserver.image.application.FileQueryService;
import com.ktlapha.imageserver.image.application.FileResourceResult;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@Validated
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileQueryService fileQueryService;

    @GetMapping({"/api/files/{*filepath}", "/api/images/{*filepath}"})
    public ResponseEntity<Resource> getFile(
        @PathVariable("filepath") @NotBlank String filepath,
        @RequestParam(name = "w", required = false) Integer width) throws IOException {

        log.info("filepath: {}, width: {}", filepath, width);

        // filepath는 PathVariable 전체를 그대로 사용 (불필요한 substring 제거)
        FileResourceResult result = fileQueryService.fetch(filepath.substring(1), width);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + result.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(result.getContentType()))
                .contentLength(result.getContentLength())
                .body(result.getResource());
    }
}
