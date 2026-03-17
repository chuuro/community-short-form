package com.shortform.backend.service;

import com.shortform.backend.config.AppProperties;
import com.shortform.backend.repository.MediaItemRepository;
import com.shortform.backend.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class TempFileService {

    private static final Logger log = LoggerFactory.getLogger(TempFileService.class);

    private final AppProperties appProperties;
    private final ProjectRepository projectRepository;
    private final MediaItemRepository mediaItemRepository;

    public TempFileService(AppProperties appProperties,
                           ProjectRepository projectRepository,
                           MediaItemRepository mediaItemRepository) {
        this.appProperties = appProperties;
        this.projectRepository = projectRepository;
        this.mediaItemRepository = mediaItemRepository;
    }

    // 삭제된 프로젝트의 Temp 파일 즉시 삭제
    @Transactional
    public void deleteTempFilesForProject(Long projectId) {
        Path tempDir = Paths.get(appProperties.getStorage().getTempDir(),
                                 String.valueOf(projectId));

        if (Files.exists(tempDir)) {
            deleteDirectory(tempDir);
            log.info("Temp 파일 삭제 완료: {}", tempDir);
        }
    }

    // 매일 새벽 3시: TTL 초과된 Temp 파일 자동 정리
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanExpiredTempFiles() {
        log.info("만료 Temp 파일 정리 시작");

        Path tempRoot = Paths.get(appProperties.getStorage().getTempDir());
        if (!Files.exists(tempRoot)) return;

        long ttlHours = appProperties.getStorage().getTempTtlHours();
        LocalDateTime expiredBefore = LocalDateTime.now().minusHours(ttlHours);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempRoot)) {
            for (Path projectDir : stream) {
                if (!Files.isDirectory(projectDir)) continue;

                BasicFileAttributes attrs = Files.readAttributes(projectDir,
                        BasicFileAttributes.class);
                LocalDateTime createdAt = LocalDateTime.ofInstant(
                        attrs.creationTime().toInstant(), ZoneId.systemDefault());

                if (createdAt.isBefore(expiredBefore)) {
                    deleteDirectory(projectDir);
                    log.info("만료 Temp 디렉토리 삭제: {}", projectDir);
                }
            }
        } catch (IOException e) {
            log.error("Temp 파일 정리 실패", e);
        }

        log.info("만료 Temp 파일 정리 완료");
    }

    // Temp 폴더 총 용량 계산 (모니터링용)
    public long calculateTempDirSize() {
        Path tempRoot = Paths.get(appProperties.getStorage().getTempDir());
        if (!Files.exists(tempRoot)) return 0;

        try {
            return Files.walk(tempRoot)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try { return Files.size(path); } catch (IOException e) { return 0; }
                    })
                    .sum();
        } catch (IOException e) {
            log.error("Temp 용량 계산 실패", e);
            return -1;
        }
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc)
                        throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("디렉토리 삭제 실패: {}", dir, e);
        }
    }
}
