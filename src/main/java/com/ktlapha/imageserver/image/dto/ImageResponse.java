package com.ktlapha.imageserver.image.dto;

public record ImageResponse(
        String id,
        String filename,
        String url
) {}
