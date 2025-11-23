package com.ktlapha.imageserver.image.application;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.core.io.Resource;

@Getter
@AllArgsConstructor
public class FileResourceResult {
    private final Resource resource;
    private final String filename;
    private final String contentType;
    private final long contentLength;
}
