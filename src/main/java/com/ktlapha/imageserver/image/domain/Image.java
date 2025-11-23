package com.ktlapha.imageserver.image.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Image {
    private String id;
    private String filename;
    private String contentType;
    private long size;
    private String url;
}
