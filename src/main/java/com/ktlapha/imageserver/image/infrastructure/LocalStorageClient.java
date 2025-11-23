package com.ktlapha.imageserver.image.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일 시스템에 저장하는 스텁 구현체.
 * 실제 저장은 하지 않고, 접근 가능한 URL 형태의 문자열만 반환합니다.
 */
@Component
public class LocalStorageClient implements StorageClient {

    @Override
    public String store(String folder, MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        return "/static/" + folder + "/" + filename;
    }
}
