package com.shortform.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public OpenAIService(AppProperties appProperties,
                         SubtitleRepository subtitleRepository,
                         WebClient.Builder webClientBuilder,
                         ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.subtitleRepository = subtitleRepository;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 뉴스 기사 → 숏폼 메타데이터 추출 (Script, 썸네일, 번역, 멀티미디어 검색어 등)
     * JSON 형식으로 반환
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JsonNode extractNewsMetadata(String title, String description, String content) {
        String apiKey = appProperties.getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API 키가 설정되지 않아 뉴스 메타데이터 추출을 건너뜁니다.");
            return null;
        }

        String prompt = buildNewsMetadataPrompt(title, description, content);
        String raw = callChatCompletionForNewsMetadata(prompt);

        if (raw == null || raw.isBlank()) return null;

        try {
            // JSON 블록 추출 (```json ... ``` 형태일 수 있음)
            String jsonStr = raw.trim();
            if (jsonStr.contains("```json")) {
                int start = jsonStr.indexOf("```json") + 7;
                int end = jsonStr.indexOf("```", start);
                jsonStr = jsonStr.substring(start, end > 0 ? end : jsonStr.length()).trim();
            } else if (jsonStr.contains("```")) {
                int start = jsonStr.indexOf("```") + 3;
                int end = jsonStr.indexOf("```", start);
                jsonStr = jsonStr.substring(start, end > 0 ? end : jsonStr.length()).trim();
            }
            return objectMapper.readTree(jsonStr);
        } catch (Exception e) {
            log.error("뉴스 메타데이터 JSON 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String buildNewsMetadataPrompt(String title, String description, String content) {
        return String.format("""
                다음 뉴스 기사를 숏폼 영상(30~40초)으로 제작하기 위한 메타데이터를 추출해주세요.
                반드시 아래 JSON 형식으로만 응답하세요. 다른 설명은 없이 JSON만 출력하세요.

                ## 뉴스 기사
                제목: %s
                요약: %s
                본문: %s

                ## 출력 JSON 형식
                {
                  "script": "숏폼 대본. 줄바꿈 단위로 자막 분리. 각 줄은 1~2문장. 총 30~40초 분량(약 10~15줄). 짧고 임팩트 있게, 핵심만 전달.",
                  "translatedTitle": "한글로 자연스럽게 번역한 제목",
                  "translatedContent": "한글로 자연스럽게 번역한 본문 요약 (2~3문장)",
                  "thumbnailKeywords": ["썸네일 검색 키워드1", "키워드2"],
                  "imageSearchKeywords": ["구간1 이미지 검색어", "구간2 이미지 검색어", "..."],
                  "videoSearchKeywords": ["구간1 영상 검색어", "구간2 영상 검색어", "..."],
                  "estimatedDurationSeconds": 35.0
                }

                - script: 대본은 한국어로, 줄바꿈(\\n)으로 구분. 반드시 30~40초 분량(10~15줄)으로 충분히 작성
                - imageSearchKeywords, videoSearchKeywords: script 구간별로 검색에 적합한 영어 키워드 배열
                - estimatedDurationSeconds: 읽기 속도 기준 예상 길이(초), 30~40 범위
                """,
                title != null ? title : "",
                description != null ? description : "",
                content != null ? content : ""
        );
    }

    private String callChatCompletionForNewsMetadata(String prompt) {
        WebClient client = buildOpenAIClient();

        JsonNode response = client.post()
                .uri("/chat/completions")
                .bodyValue(Map.of(
                        "model", appProperties.getOpenai().getModel(),
                        "messages", List.of(
                                Map.of("role", "system", "content",
                                        "당신은 뉴스 기사를 숏폼 영상용 메타데이터로 변환하는 전문가입니다. " +
                                        "대본은 반드시 30~40초 분량(약 10~15줄)으로 충분히 작성하세요. " +
                                        "반드시 요청된 JSON 형식으로만 응답하세요."),
                                Map.of("role", "user", "content", prompt)
                        ),
                        "max_tokens", 2500
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return response != null
                ? response.path("choices").get(0).path("message").path("content").asText(null)
                : null;
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

    // ─────────────────────────────────────────────────────────────────
    // KNOWLEDGE 타입: AI 지식 숏폼 스크립트 + DALL-E 3 이미지 생성
    // ─────────────────────────────────────────────────────────────────

    /**
     * GPT-4o로 지식 숏폼 영상 씬 스크립트 JSON 배열을 생성합니다.
     *
     * 반환 형식 (JSON 배열):
     * [
     *   {
     *     "scene_id": 1,
     *     "narration": "한국어 내레이션",
     *     "dalle_prompt": "English DALL-E 3 prompt"
     *   },
     *   ...
     * ]
     */
    public JsonNode generateKnowledgeScript(String topic, int sceneCount) {
        String apiKey = appProperties.getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API 키 없음 — Knowledge 스크립트 생성 불가");
            return null;
        }

        String systemPrompt = """
                You are an expert AI scriptwriter for Korean shortform (Shorts/Reels/TikTok) videos.
                Always respond with a valid JSON array only — no markdown, no extra text.
                Each object must contain: scene_id (int), narration (Korean text), dalle_prompt (English DALL-E 3 prompt).
                """;

        // 구조별 씬 배분 계산
        // 필수: 1(훅) + 1(결말) = 2, 나머지는 본문/반론으로 채움
        int hookScenes    = 1;
        int outroScenes   = 1;
        int contentScenes = Math.max(1, sceneCount - hookScenes - outroScenes - 1); // 본문
        int counterScenes = sceneCount >= 5 ? 1 : 0;                                // 반론(씬5 이상일 때만)

        String userPrompt = String.format("""
                [Role]
                You are an expert AI scriptwriter for Korean knowledge-based shortform videos.

                [Task]
                Generate a JSON script for a 9:16 vertical shortform video on the topic: "%s".
                The script must have exactly %d scenes.

                [CRITICAL — Narration Length]
                Each narration MUST be 80 to 120 Korean characters long (spaces included).
                Short narrations (under 60 characters) are STRICTLY FORBIDDEN.
                Write naturally spoken Korean — like a confident narrator on TV.
                Use vivid expressions, concrete examples, and engaging sentence rhythms.

                [Script Structure — follow this exactly]
                Scene 1 (Hook): Open with a surprising fact, recent trend, or provocative question about the topic to grab attention immediately.
                               Example style: "혹시 알고 계셨나요? ~", "최근 ~라는 충격적인 연구 결과가 발표됐습니다."
                Scenes 2~%d (Main Content): Explain the core content with specific facts, examples, and data. Each scene covers one distinct point.
                %s
                Scene %d (Outro): End with an open question, thought-provoking statement, or call to action.
                               Example style: "여러분은 어떻게 생각하시나요?", "이 사실을 알고 나서도 생각이 같으신가요?", "댓글로 의견 남겨주세요!"

                [Output Format]
                Valid JSON array only. Each object:
                - "scene_id": integer (1-based)
                - "narration": 80~120 Korean characters. Natural spoken Korean. NO short sentences.
                - "dalle_prompt": Detailed English prompt for DALL-E 3, 9:16 vertical. Include: "cinematic, photorealistic, ultra-detailed, 8k, natural lighting, vertical composition"

                Respond with ONLY the JSON array. No markdown, no explanation.
                """,
                topic,
                sceneCount,
                hookScenes + contentScenes,
                counterScenes > 0
                    ? String.format("Scene %d (Counter-argument): Present an opposing viewpoint or common misconception about the topic, then briefly address it. Style: \"물론 ~라는 시각도 있습니다. 하지만...\"", hookScenes + contentScenes + 1)
                    : "",
                sceneCount
        );

        WebClient client = buildOpenAIClient();

        try {
            JsonNode response = client.post()
                    .uri("/chat/completions")
                    .bodyValue(Map.of(
                            "model", appProperties.getOpenai().getModel(),
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)
                            ),
                            "max_tokens", 4000,
                            "temperature", 0.75
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String raw = response != null
                    ? response.path("choices").get(0).path("message").path("content").asText(null)
                    : null;

            if (raw == null || raw.isBlank()) {
                log.error("GPT 응답이 비어있음");
                return null;
            }

            // JSON 블록 추출 (```json ... ``` 형태 제거)
            String jsonStr = raw.trim();
            if (jsonStr.contains("```json")) {
                int start = jsonStr.indexOf("```json") + 7;
                int end = jsonStr.indexOf("```", start);
                jsonStr = jsonStr.substring(start, end > 0 ? end : jsonStr.length()).trim();
            } else if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3, jsonStr.lastIndexOf("```")).trim();
            }

            JsonNode parsed = objectMapper.readTree(jsonStr);
            log.info("Knowledge 스크립트 생성 완료: topic='{}', scenes={}", topic, parsed.size());
            return parsed;

        } catch (Exception e) {
            log.error("GPT Knowledge 스크립트 생성 실패: {}", e.getMessage());
            throw new RuntimeException("스크립트 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * DALL-E 3로 씬 이미지를 생성하고 임시 URL을 반환합니다.
     * 9:16 비율 (1024x1792) 이미지를 생성합니다.
     *
     * @param prompt DALL-E 3 프롬프트
     * @return 임시 이미지 URL (1시간 유효)
     */
    public String generateDalle3Image(String prompt) {
        String apiKey = appProperties.getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API 키 없음 — DALL-E 이미지 생성 불가");
            return null;
        }

        WebClient client = buildOpenAIClient();

        try {
            JsonNode response = client.post()
                    .uri("/images/generations")
                    .bodyValue(Map.of(
                            "model", appProperties.getOpenai().getDalleModel(),
                            "prompt", prompt,
                            "n", 1,
                            "size", "1024x1792",
                            "quality", "standard",
                            "response_format", "url"
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data") || response.path("data").isEmpty()) {
                log.error("DALL-E 응답에 이미지 데이터 없음");
                return null;
            }

            String url = response.path("data").get(0).path("url").asText(null);
            log.info("DALL-E 3 이미지 생성 완료: {}", url != null ? "(ok)" : "null");
            return url;

        } catch (Exception e) {
            log.error("DALL-E 3 이미지 생성 실패: {}", e.getMessage());
            throw new RuntimeException("DALL-E 이미지 생성 실패: " + e.getMessage(), e);
        }
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
