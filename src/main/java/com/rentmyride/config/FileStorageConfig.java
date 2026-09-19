package com.rentmyride.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Slf4j
@Configuration
public class FileStorageConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = new File(uploadDir).getAbsolutePath();
        // Logged on every startup so a "No static resource cars/*.jpg" 404 is diagnosable in
        // thirty seconds instead of guesswork: compare this line against where
        // LocalDiskFileStorageService actually wrote the file (same property, so normally these
        // always agree — the historical bug was launching the app from two different working
        // directories across two runs, which this absolute, ${user.home}-anchored default fixes).
        log.info("[DDT] Serving /uploads/** from: {}", absolutePath);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + File.separator);
    }
}
