package com.ktlapha.imageserver.image.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record ImageUploadRequest(
        @NotNull(message = "file is required") MultipartFile file,
        @NotBlank(message = "folder cannot be blank") String folder
) {}
