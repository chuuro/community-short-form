package com.shortform.backend.service;

import com.shortform.backend.config.AppProperties;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO Presigned URL 재발급 (minio:9000 → localhost:9000)
 * 브라우저에서 영상 재생 가능하도록
 */
@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioService(AppProperties appProperties) {
        AppProperties.Minio cfg = appProperties.getMinio();
        this.minioClient = MinioClient.builder()
                .endpoint("http://" + cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();
        this.bucket = cfg.getBucket();
    }

    /**
     * outputFilePath(전체 URL)에서 object key 추출 후 localhost 기준 Presigned URL 생성
     * 예: http://minio:9000/shortform/renders/1/output_xxx.mp4?params → renders/1/output_xxx.mp4
     */
    public String getPlayableUrl(String outputFilePath) {
        if (outputFilePath == null || outputFilePath.isBlank()) {
            return null;
        }
        if (!outputFilePath.contains("minio") && !outputFilePath.contains("localhost")) {
            return outputFilePath;
        }
        try {
            String objectKey = extractObjectKey(outputFilePath);
            if (objectKey == null) return outputFilePath;

            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(2, TimeUnit.HOURS)
                            .build()
            );
            log.debug("Presigned URL 재발급: {} -> {}", objectKey, url != null ? "(ok)" : "null");
            return url;
        } catch (Exception e) {
            log.warn("Presigned URL 생성 실패: {}", e.getMessage());
            return outputFilePath;
        }
    }

    private String extractObjectKey(String url) {
        try {
            URL u = new URL(url);
            String path = u.getPath();
            if (path.startsWith("/")) path = path.substring(1);
            if (path.startsWith(bucket + "/")) {
                return path.substring(bucket.length() + 1);
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 멀티미디어 파일 업로드 (이미지/영상)
     * @return object key (예: media/1/uuid.jpg)
     */
    public String uploadMediaFile(MultipartFile file, Long articleId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어 있습니다.");
        }
        String ext = getExtension(file.getOriginalFilename(), file.getContentType());
        String objectKey = "media/" + articleId + "/" + UUID.randomUUID() + ext;
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("미디어 업로드 완료: {}", objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("미디어 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("파일 업로드 실패: " + e.getMessage());
        }
    }

    /** object key로 Presigned URL 생성 (브라우저 표시용) */
    public String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(2, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Presigned URL 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    /** object key로 파일 스트림 반환 (프록시 서빙용) */
    public Resource getObjectAsResource(String objectKey) {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return new InputStreamResource(stream);
        } catch (Exception e) {
            log.warn("MinIO 객체 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    private String getExtension(String filename, String contentType) {
        if (filename != null) {
            int i = filename.lastIndexOf('.');
            if (i >= 0) return filename.substring(i).toLowerCase();
        }
        if (contentType != null) {
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("gif")) return ".gif";
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("mp4")) return ".mp4";
            if (contentType.contains("webm")) return ".webm";
        }
        return ".jpg";
    }
}
