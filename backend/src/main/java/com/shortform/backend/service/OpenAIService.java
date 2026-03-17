package com.shortform.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortform.backend.config.AppProperties;
import com.shortform.backend.domain.entity.MediaItem;
import com.shortform.backend.domain.entity.Project;
import com.shortform.backend.domain.entity.Subtitle;
import com.shortform.backend.domain.enums.MediaType;
import com.shortform.backend.repository.SubtitleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class OpenAIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    private final AppProperties appProperties;
    private final SubtitleRepository subtitleRepository;
    private final WebClient.Builder webClientBuilder;

    public OpenAIService(AppProperties appProperties,
                         SubtitleRepository subtitleRepository,
                         WebClient.Builder webClientBuilder) {
        this.appProperties = appProperties;
        this.subtitleRepository = subtitleRepository;
        this.webClientBuilder = webClientBuilder;
    }

    // 게시글 내용 + 미디어 분석 → 숏폼 대본 생성
    // REQUIRES_NEW: OpenAI 실패가 부모 트랜잭션 롤백을 오염시키지 않도록 분리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Subtitle> generateScript(Project project, List<MediaItem> mediaItems) {
        String apiKey = appProperties.getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API 키가 설정되지 않아 대본 생성을 건너뜁니다. (OPENAI_API_KEY 환경변수를 설정하세요)");
            return List.of();
        }

        String prompt = buildScriptPrompt(project, mediaItems);
        String script = callChatCompletion(prompt);
        return parseAndSaveSubtitles(project, script);
    }

    // 비디오 오디오 → Whisper로 자막 추출
    public List<Subtitle> transcribeAudio(Project project, String audioFilePath) {
        log.info("Whisper 자막 추출 시작: {}", audioFilePath);

        // Whisper API 호출 (multipart/form-data)
        WebClient client = buildOpenAIClient();

        try {
            byte[] audioBytes = java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(audioFilePath));

            JsonNode response = client.post()
                    .uri("/audio/transcriptions")
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(buildWhisperBody(audioBytes, audioFilePath))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String transcribedText = response != null ? response.path("text").asText() : "";
            return parseAndSaveSubtitles(project, transcribedText);
        } catch (Exception e) {
            log.error("Whisper 자막 추출 실패", e);
            throw new RuntimeException("자막 추출에 실패했습니다: " + e.getMessage());
        }
    }

    // 이미지 분석 (GPT-4o Vision)
    public String analyzeImage(String imageUrl) {
        log.info("이미지 분석 시작: {}", imageUrl);

        String prompt = "이 이미지의 내용을 한국어로 간결하게 설명해주세요. 숏폼 영상의 자막으로 사용할 내용입니다.";

        WebClient client = buildOpenAIClient();

        JsonNode response = client.post()
                .uri("/chat/completions")
                .bodyValue(Map.of(
                        "model", appProperties.getOpenai().getModel(),
                        "messages", List.of(Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", prompt),
                                        Map.of("type", "image_url",
                                               "image_url", Map.of("url", imageUrl))
                                )
                        )),
                        "max_tokens", 300
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return response != null
                ? response.path("choices").get(0).path("message").path("content").asText()
                : "";
    }

    private String callChatCompletion(String prompt) {
        WebClient client = buildOpenAIClient();

        JsonNode response = client.post()
                .uri("/chat/completions")
                .bodyValue(Map.of(
                        "model", appProperties.getOpenai().getModel(),
                        "messages", List.of(
                                Map.of("role", "system", "content",
                                        "당신은 커뮤니티 게시글을 숏폼 영상으로 만드는 전문 편집자입니다. " +
                                        "게시글 내용을 바탕으로 짧고 임팩트 있는 영상 대본을 작성해주세요."),
                                Map.of("role", "user", "content", prompt)
                        ),
                        "max_tokens", 1000
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return response != null
                ? response.path("choices").get(0).path("message").path("content").asText()
                : "";
    }

    private List<Subtitle> parseAndSaveSubtitles(Project project, String script) {
        // 줄바꿈 단위로 자막 분리 저장
        String[] lines = script.split("\n");
        subtitleRepository.deleteAllByProjectId(project.getId());

        int order = 0;
        double timeOffset = 0.0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            double duration = Math.max(2.0, trimmed.length() * 0.07);

            Subtitle subtitle = Subtitle.builder()
                    .project(project)
                    .originalContent(trimmed)
                    .content(trimmed)
                    .startTime(timeOffset)
                    .endTime(timeOffset + duration)
                    .orderIndex(order++)
                    .build();

            subtitleRepository.save(subtitle);
            timeOffset += duration;
        }

        return subtitleRepository.findByProjectIdOrderByOrderIndexAsc(project.getId());
    }

    private String buildScriptPrompt(Project project, List<MediaItem> mediaItems) {
        long videoCount = mediaItems.stream()
                .filter(m -> m.getMediaType() == MediaType.VIDEO)
                .count();
        long imageCount = mediaItems.stream()
                .filter(m -> m.getMediaType() == MediaType.IMAGE)
                .count();
        List<String> comments = mediaItems.stream()
                .filter(MediaItem::isPopularComment)
                .map(MediaItem::getAltText)
                .toList();

        return String.format("""
                게시글 제목: %s
                게시글 내용: %s
                미디어 구성: 동영상 %d개, 이미지 %d개
                인기 댓글: %s
                
                위 게시글을 바탕으로 60초 이내 숏폼 영상 대본을 작성해주세요.
                각 자막은 한 줄에 하나씩, 간결하게 작성해주세요.
                """,
                project.getTitle(),
                project.getDescription(),
                videoCount,
                imageCount,
                String.join(" / ", comments)
        );
    }

    private WebClient buildOpenAIClient() {
        return webClientBuilder
                .baseUrl(appProperties.getOpenai().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                               "Bearer " + appProperties.getOpenai().getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    private org.springframework.util.MultiValueMap<String, Object> buildWhisperBody(
            byte[] audioBytes, String filePath) {
        org.springframework.util.LinkedMultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("model", appProperties.getOpenai().getWhisperModel());
        body.add("response_format", "text");
        body.add("language", "ko");
        body.add("file", new org.springframework.core.io.ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return java.nio.file.Paths.get(filePath).getFileName().toString();
            }
        });
        return body;
    }
}
