package com.bakeaura.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    public Map<String, Object> uploadVideo(MultipartFile file, String folderName) throws IOException {
        log.info("Uploading video to Cloudinary, folder: {}, size: {} bytes",
                folderName, file.getSize());

        Map<String, Object> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "resource_type", "video",           // MUST be "video" for mp4/mov/webm
                        "folder",        folderName,         // organizes files in Cloudinary
                        "eager", new Object[]{               // generates a thumbnail automatically
                                ObjectUtils.asMap(
                                        "width", 400, "height", 711, // 9:16 vertical ratio (like Reels)
                                        "crop", "fill",
                                        "format", "jpg"
                                )
                        },
                        "eager_async", false  // wait for thumbnail before returning
                )
        );

        log.info("Cloudinary upload successful. Public ID: {}", uploadResult.get("public_id"));
        return uploadResult;
    }

    /**
     * Uploads an image to Cloudinary (used later for Stories and product photos)
     */
    public Map<String, Object> uploadImage(MultipartFile file, String folderName) throws IOException {
        return cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "resource_type", "image",
                        "folder",        folderName,
                        "transformation", ObjectUtils.asMap(
                                "quality", "auto",   // Cloudinary auto-optimizes quality
                                "fetch_format", "auto" // serves WebP to browsers that support it
                        )
                )
        );
    }

    /**
     * Deletes a file from Cloudinary by its public_id.
     * Call this when a seller deletes a reel or story.
     */
    public void deleteFile(String publicId, String resourceType) throws IOException {
        cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap("resource_type", resourceType)
        );
        log.info("Deleted from Cloudinary: {}", publicId);
    }
}
