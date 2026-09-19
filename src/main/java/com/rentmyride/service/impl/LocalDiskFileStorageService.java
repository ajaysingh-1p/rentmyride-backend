package com.rentmyride.service.impl;

import com.rentmyride.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Current/default storage backend — writes to the local filesystem, exactly as
 * FileUploadController used to do inline. Fine for local dev and a single, always-on instance.
 *
 * NOT suitable for an ephemeral-filesystem production host or multiple instances (issue #28) —
 * for that, add a FileStorageService implementation backed by S3/Cloudinary/GCS and select it via
 * file.storage-provider, instead of this one.
 */
@Service
public class LocalDiskFileStorageService implements FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file, String filename) throws IOException {
        Path targetPath = Path.of(uploadDir).resolve(filename);
        // filename may include a subfolder (e.g. "cars/uuid.jpg" vs "documents/uuid.jpg") — make
        // sure that subfolder exists too, not just the top-level upload dir.
        Files.createDirectories(targetPath.getParent());
        Files.copy(file.getInputStream(), targetPath);
        return "/uploads/" + filename;
    }
}
