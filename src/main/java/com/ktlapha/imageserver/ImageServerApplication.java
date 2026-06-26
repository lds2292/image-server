package com.ktlapha.imageserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.imageio.ImageIO;
import java.util.Arrays;

@SpringBootApplication
public class ImageServerApplication {

    public static void main(String[] args) {
        System.out.println("[ImageIO] writerFormats: " + Arrays.toString(ImageIO.getWriterFormatNames()));
        SpringApplication.run(ImageServerApplication.class, args);
    }

}
