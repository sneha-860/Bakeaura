package com.bakeaura.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;


import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Uploads a video to Cloudinary.
     * Returns a map with: secure_url, public_id, duration, thumbnail_url
     *
     * BEGINNER NOTE: ObjectUtils.asMap() is Cloudinary's way of building
     * a config map. "resource_type" must be "video" for video files —
     * if you leave it as default "image", the upload will fail.
     */
    @CircuitBreaker(name = "cloudinary", fallbackMethod = "uploadVideoFallback")
    public Map<String, Object> uploadVideo(MultipartFile file, String folderName) throws IOException {
        log.info("Uploading video to Cloudinary, folder: {}, size: {} bytes",
                folderName, file.getSize());

        Map<String, Object> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "resource_type", "video",
                        "folder",        folderName,
                        "eager", new Object[]{
                                ObjectUtils.asMap(
                                        "width", 400, "height", 711,
                                        "crop", "fill",
                                        "format", "jpg"
                                )
                        },
                        "eager_async", false
                )
        );

        log.info("Cloudinary upload successful. Public ID: {}", uploadResult.get("public_id"));
        return uploadResult;
    }

    public Map<String, Object> uploadVideoFallback(MultipartFile file, String folderName, Throwable t) {
        log.error("Cloudinary circuit breaker triggered for video upload. Cause: {}", t.getMessage());
        throw new RuntimeException("Media upload service is temporarily unavailable. Please try again in a moment.");
    }

    @CircuitBreaker(name = "cloudinary", fallbackMethod = "uploadImageFallback")
    public Map<String, Object> uploadImage(MultipartFile file, String folderName) throws IOException {
        return cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "resource_type", "image",
                        "folder",        folderName,
                        "transformation", ObjectUtils.asMap(
                                "quality", "auto",
                                "fetch_format", "auto"
                        )
                )
        );
    }

    public Map<String, Object> uploadImageFallback(MultipartFile file, String folderName, Throwable t) {
        log.error("Cloudinary circuit breaker triggered for image upload. Cause: {}", t.getMessage());
        throw new RuntimeException("Media upload service is temporarily unavailable. Please try again in a moment.");
    }

    @CircuitBreaker(name = "cloudinary", fallbackMethod = "deleteFileFallback")
    public void deleteFile(String publicId, String resourceType) throws IOException {
        cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap("resource_type", resourceType)
        );
        log.info("Deleted from Cloudinary: {}", publicId);
    }

    public void deleteFileFallback(String publicId, String resourceType, Throwable t) {
        log.warn("Cloudinary circuit breaker triggered for delete. Public ID: {}. Cause: {}", publicId, t.getMessage());
    }

    @CircuitBreaker(name = "cloudinary", fallbackMethod = "uploadImageBytesFallback")
    public Map<String, Object> uploadImageBytes(byte[] imageBytes, String folderName) throws IOException {
        return cloudinary.uploader().upload(
                imageBytes,
                ObjectUtils.asMap(
                        "resource_type", "image",
                        "folder",        folderName,
                        "transformation", ObjectUtils.asMap(
                                "quality", "auto",
                                "fetch_format", "auto"
                        )
                )
        );
    }

    public Map<String, Object> uploadImageBytesFallback(byte[] imageBytes, String folderName, Throwable t) {
        log.error("Cloudinary circuit breaker triggered for AI image upload. Cause: {}", t.getMessage());
        throw new RuntimeException("Media upload service is temporarily unavailable. Please try again in a moment.");
    }
}
