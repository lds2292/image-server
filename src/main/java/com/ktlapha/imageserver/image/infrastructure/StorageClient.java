package com.ktlapha.imageserver.image.infrastructure;

import org.springframework.web.multipart.MultipartFile;

public interface StorageClient {
    String store(String folder, MultipartFile file);
}
