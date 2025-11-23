package com.ktlapha.imageserver.image.application;

import com.ktlapha.imageserver.image.domain.Image;
import com.ktlapha.imageserver.image.dto.ImageResponse;
import com.ktlapha.imageserver.image.infrastructure.StorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final StorageClient storageClient;

    public ImageResponse upload(String folder, MultipartFile file) {
        String url = storageClient.store(folder, file);
        Image image = Image.builder()
                .id(UUID.randomUUID().toString())
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .url(url)
                .build();
        return new ImageResponse(image.getId(), image.getFilename(), image.getUrl());
    }
}
