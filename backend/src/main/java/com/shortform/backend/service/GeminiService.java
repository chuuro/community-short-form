package com.shortform.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortform.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini (텍스트) + Imagen 3 (이미지) 서비스
 * - 지식숏폼(KNOWLEDGE) 타입 전용
 * - 뉴스/커뮤니티 기능은 여전히 OpenAIService 사용
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    // Disney/Pixar 스타일 공통 접미사 (모든 이미지에 통일감 부여)
    private static final String DISNEY_STYLE_SUFFIX =
            ", Disney Pixar 3D animated style, vibrant warm colors, magical CGI quality, " +
            "stylized cartoon characters, high-quality 3D render, cinematic soft lighting, " +
            "vertical composition 9:16, no text, no watermark";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public GeminiService(AppProperties appProperties,
                         ObjectMapper objectMapper,
                         WebClient.Builder webClientBuilder) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. Gemini 텍스트: 지식숏폼 씬 스크립트 JSON 배열 생성
    // ─────────────────────────────────────────────────────────────────

    /**
     * Gemini로 지식 숏폼 영상 씬 스크립트 JSON 배열을 생성합니다.
     *
     * 반환 형식 (JSON 배열):
     * [
     *   {
     *     "scene_id": 1,
     *     "narration": "한국어 내레이션",
     *     "dalle_prompt": "English Imagen 3 prompt (Disney Pixar style)"
     *   },
     *   ...
     * ]
     */
    public JsonNode generateKnowledgeScript(String topic, int sceneCount) {
        String apiKey = appProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API 키 없음 — Knowledge 스크립트 생성 불가");
            return null;
        }

        // 구조별 씬 배분 (OpenAIService와 동일 로직)
        int hookScenes    = 1;
        int outroScenes   = 1;
        int contentScenes = Math.max(1, sceneCount - hookScenes - outroScenes - 1);
        int counterScenes = sceneCount >= 5 ? 1 : 0;

        String systemText = """
                You are an expert AI scriptwriter for Korean shortform (Shorts/Reels/TikTok) videos.
                Always respond with a valid JSON array only — no markdown, no extra text.
                Each object must contain: scene_id (int), narration (Korean text), dalle_prompt (English Imagen 3 prompt).
                For dalle_prompt: ALWAYS generate Disney/Pixar 3D animated style image prompts.
                Every dalle_prompt MUST include: "Disney Pixar 3D animated style, vibrant warm colors, magical CGI quality, stylized cartoon characters, high-quality 3D render, cinematic soft lighting, vertical composition 9:16"
                """;

        String userText = String.format("""
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
                - "dalle_prompt": Disney/Pixar 3D animated style Imagen 3 prompt. Must include:
                  "Disney Pixar 3D animated style, vibrant warm colors, magical CGI quality, stylized cartoon characters, high-quality 3D render, cinematic soft lighting, vertical composition 9:16"
                  Describe the scene visually in English. Match the narration topic.

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

        WebClient client = buildGeminiClient();
        String model = appProperties.getGemini().getModel();

        try {
            JsonNode response = client.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .bodyValue(Map.of(
                            "systemInstruction", Map.of(
                                    "parts", List.of(Map.of("text", systemText))
                            ),
                            "contents", List.of(
                                    Map.of("role", "user",
                                           "parts", List.of(Map.of("text", userText)))
                            ),
                            "generationConfig", Map.of(
                                    "maxOutputTokens", 4000,
                                    "temperature", 0.75,
                                    // Gemini 2.5 Flash는 thinking 모델 — JSON 출력이 잘리지 않도록 thinking 비활성화
                                    "thinkingConfig", Map.of("thinkingBudget", 0)
                            )
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String raw = response != null
                    ? response.path("candidates").get(0)
                              .path("content").path("parts").get(0)
                              .path("text").asText(null)
                    : null;

            if (raw == null || raw.isBlank()) {
                log.error("Gemini 응답이 비어있음");
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
            log.info("Gemini Knowledge 스크립트 생성 완료: topic='{}', scenes={}", topic, parsed.size());
            return parsed;

        } catch (Exception e) {
            log.error("Gemini Knowledge 스크립트 생성 실패: {}", e.getMessage());
            throw new RuntimeException("Gemini 스크립트 생성 실패: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. 나노바나나2 (nano-banana-pro-preview) / Imagen: 씬 이미지 생성
    //    9:16 비율, Disney/Pixar 스타일 자동 적용
    // ─────────────────────────────────────────────────────────────────

    /**
     * 설정된 모델로 씬 이미지를 생성하고 byte[]를 반환합니다.
     * - nano-banana-pro-preview (나노바나나2): generateContent + IMAGE modality → JPEG
     * - imagen-4.0-*: :predict API → PNG
     * Disney/Pixar 스타일 자동 적용.
     *
     * @param prompt 이미지 프롬프트 (Gemini가 생성한 dalle_prompt 필드값)
     * @return 이미지 byte[], 실패 시 null
     */
    public byte[] generateImagenImage(String prompt) {
        String apiKey = appProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API 키 없음 — 이미지 생성 불가");
            return null;
        }

        // Disney 스타일 강제 적용 (프롬프트에 없을 경우 대비)
        String finalPrompt = prompt.toLowerCase().contains("disney")
                ? prompt
                : prompt + DISNEY_STYLE_SUFFIX;

        String imageModel = appProperties.getGemini().getImagenModel();

        // nano-banana 계열(Gemini 이미지 모델)은 generateContent + IMAGE modality 사용
        if (imageModel.contains("nano-banana") || imageModel.contains("gemini") && imageModel.contains("image")) {
            return generateWithNanoBanana(finalPrompt, imageModel);
        } else {
            return generateWithImagen(finalPrompt, imageModel);
        }
    }

    /** 나노바나나2 (nano-banana-pro-preview): generateContent + responseModalities=IMAGE */
    private byte[] generateWithNanoBanana(String prompt, String model) {
        WebClient client = buildGeminiImageClient();
        try {
            JsonNode response = client.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .bodyValue(Map.of(
                            "contents", List.of(
                                    Map.of("parts", List.of(
                                            Map.of("text", "Generate an image: " + prompt
                                                    + " vertical 9:16 composition, no text, no watermark")
                                    ))
                            ),
                            "generationConfig", Map.of(
                                    "responseModalities", List.of("IMAGE")
                            )
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                log.error("나노바나나2 응답 없음");
                return null;
            }

            JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
            for (JsonNode part : parts) {
                if (part.has("inlineData")) {
                    String base64 = part.path("inlineData").path("data").asText(null);
                    if (base64 != null && !base64.isBlank()) {
                        byte[] bytes = Base64.getDecoder().decode(base64);
                        log.info("나노바나나2 이미지 생성 완료: {}KB", bytes.length / 1024);
                        return bytes;
                    }
                }
            }
            log.error("나노바나나2 응답에 이미지 데이터 없음: {}", response.toPrettyString().substring(0, Math.min(300, response.toPrettyString().length())));
            return null;

        } catch (Exception e) {
            log.warn("나노바나나2 이미지 생성 불가 (DALL-E 3 폴백): {}", e.getMessage());
            return null;
        }
    }

    /** Imagen (imagen-4.0-*): :predict API */
    private byte[] generateWithImagen(String prompt, String model) {
        WebClient client = buildGeminiImageClient();
        try {
            JsonNode response = client.post()
                    .uri("/v1beta/models/{model}:predict", model)
                    .bodyValue(Map.of(
                            "instances", List.of(
                                    Map.of("prompt", prompt)
                            ),
                            "parameters", Map.of(
                                    "sampleCount", 1,
                                    "aspectRatio", "9:16",
                                    "safetyFilterLevel", "block_some",
                                    "personGeneration", "allow_adult"
                            )
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("predictions") || response.path("predictions").isEmpty()) {
                log.error("Imagen 응답에 이미지 데이터 없음");
                return null;
            }

            String base64 = response.path("predictions").get(0)
                                    .path("bytesBase64Encoded").asText(null);
            if (base64 == null || base64.isBlank()) {
                log.error("Imagen base64 데이터 없음");
                return null;
            }

            byte[] bytes = Base64.getDecoder().decode(base64);
            log.info("Imagen 이미지 생성 완료: {}KB", bytes.length / 1024);
            return bytes;

        } catch (Exception e) {
            log.warn("Imagen 이미지 생성 불가 (DALL-E 3 폴백): {}", e.getMessage());
            return null;
        }
    }

    private WebClient buildGeminiClient() {
        return webClientBuilder
                .baseUrl(appProperties.getGemini().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader("x-goog-api-key", appProperties.getGemini().getApiKey())
                .build();
    }

    /**
     * Imagen 전용 WebClient — 응답 바디가 수 MB(base64 이미지)이므로 32MB 버퍼 설정
     */
    private WebClient buildGeminiImageClient() {
        ExchangeStrategies largeBuffer = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();
        return WebClient.builder()
                .baseUrl(appProperties.getGemini().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader("x-goog-api-key", appProperties.getGemini().getApiKey())
                .exchangeStrategies(largeBuffer)
                .build();
    }
}
