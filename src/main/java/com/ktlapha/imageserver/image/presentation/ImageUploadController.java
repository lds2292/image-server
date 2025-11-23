package com.ktlapha.imageserver.image.presentation;

import com.ktlapha.imageserver.image.application.ImageService;
import com.ktlapha.imageserver.image.dto.ImageResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Validated
public class ImageUploadController {

    private final ImageService imageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageResponse upload(@RequestParam("file") MultipartFile file,
                                @RequestParam("folder") @NotBlank String folder) {
        return imageService.upload(folder, file);
    }
}
