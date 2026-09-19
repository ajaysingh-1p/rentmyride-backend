package com.rentmyride.controller;

import com.rentmyride.custom_exceptions.FileUploadException;
import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    // Issue #28 fix: storage now goes through this interface instead of this controller talking
    // to java.nio.file.Files directly — swapping to durable/cloud storage for production is a
    // one-class change (implement FileStorageService, wire it in), not a rewrite of this controller.
    private final FileStorageService fileStorageService;

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final java.util.Set<String> ALLOWED_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/jpg", "image/webp", "application/pdf");
    // Map each allowed extension to the file extension we'll actually save it under, so it always
    // matches what the sniffed content type turned out to be — never the (spoofable) client Content-Type.
    private static final Map<String, String> EXTENSION_BY_SNIFFED_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "application/pdf", ".pdf"
    );

    // Used for uploading car photos (public, admin only) or Driving License / Aadhar photos
    // (private, customer/driver own documents).
    // Bug fix: files used to all land in one shared "/uploads" folder that required login to
    // view at all — that's the right call for sensitive ID documents, but it silently broke
    // EVERY plain <img src="..."> in the app (car photos on the public browse page, admin's
    // car-management previews, etc.) because a plain <img> tag has no way to attach the
    // Authorization header the backend was now demanding. Car photos need to be genuinely public;
    // ID documents need to stay protected. "type" now decides which folder — and only the
    // protected one requires auth to view (see SecurityConfig).
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "document") String type) {
        if (file.isEmpty())
            throw new FileUploadException("No file provided.");

        if (file.getSize() > MAX_SIZE_BYTES)
            throw new FileUploadException("File too large — max 5MB allowed.");

        // Issue #5 fix: the client-supplied Content-Type header is just a label the uploader
        // chooses — a malicious .exe/.html/.php renamed with a .jpg extension and a spoofed
        // "image/jpeg" Content-Type would previously sail straight through this check. Instead,
        // sniff the file's real type from its first bytes (magic numbers) and validate THAT.
        String sniffedType;
        try {
            sniffedType = sniffContentType(file);
        } catch (IOException e) {
            throw new FileUploadException("Could not read uploaded file.");
        }

        if (sniffedType == null || !ALLOWED_TYPES.contains(sniffedType))
            throw new FileUploadException("Only genuine JPG, PNG, WEBP, or PDF files are allowed.");

        try {
            // Extension is derived from the sniffed (real) type, not the original filename/Content-Type,
            // so a file can't be saved with a misleading extension either.
            String extension = EXTENSION_BY_SNIFFED_TYPE.get(sniffedType);
            // "handover" -> self-drive pickup/return evidence photos. Kept in their own
            // subfolder rather than dumped into "documents" (Aadhar/DL) so admin tooling can
            // tell them apart, but — like "documents" — anything that isn't "car" is NOT in
            // SecurityConfig's permitAll list, so it falls through to anyRequest().authenticated()
            // and still requires login to view. Damage evidence tied to a specific customer's
            // trip has no business being publicly fetchable.
            String subfolder = "car".equalsIgnoreCase(type) ? "cars"
                    : "handover".equalsIgnoreCase(type) ? "handover" : "documents";
            String fileName = subfolder + "/" + UUID.randomUUID() + extension;

            String url = fileStorageService.store(file, fileName);

            Map<String, String> result = new HashMap<>();
            result.put("url", url);

            return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("File uploaded.", result));
        } catch (IOException e) {
            throw new FileUploadException("File upload failed: " + e.getMessage());
        }
    }

    /** Reads the first 16 bytes and matches them against known magic numbers for our allowed types. */
    private String sniffContentType(MultipartFile file) throws IOException {
        byte[] header = new byte[16];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        }
        if (read < 4) return null;

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF)
            return "image/jpeg";

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A)
            return "image/png";

        // PDF: "%PDF"
        if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46)
            return "application/pdf";

        // WEBP: "RIFF" .... "WEBP"
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P')
            return "image/webp";

        return null;
    }
}
