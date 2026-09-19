package com.rentmyride.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Issue #28 fix: FileUploadController used to write straight to the local disk via
 * java.nio.file.Files. That's fine on a single always-on server, but breaks on any
 * ephemeral-filesystem host (Heroku dynos, most container platforms, serverless) — a redeploy or
 * restart wipes the "uploads" directory, and with more than one instance running, an upload that
 * lands on instance A is invisible to instance B.
 *
 * This interface is the seam: FileUploadController now depends on THIS, not on java.nio.file
 * directly. LocalDiskFileStorageService (the current @Primary bean) preserves today's exact
 * behavior for local dev / a single-instance deploy. To move to durable storage, implement this
 * interface against S3 (or Cloudinary, GCS, etc.), annotate it @Service, and flip
 * file.storage-provider to select it — no controller/service code elsewhere needs to change.
 */
public interface FileStorageService {

    /**
     * Stores the file's bytes under the given filename and returns the URL the frontend should
     * use to display/download it (what previously always looked like "/uploads/&lt;filename&gt;").
     */
    String store(MultipartFile file, String filename) throws IOException;
}
