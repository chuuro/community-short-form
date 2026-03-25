package com.shortform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private OpenAI openai = new OpenAI();
    private Gemini gemini = new Gemini();
    private Storage storage = new Storage();
    private RabbitMQ rabbitmq = new RabbitMQ();
    private Reddit reddit = new Reddit();
    private NewsApi newsApi = new NewsApi();
    private Pexels pexels = new Pexels();
    private Minio minio = new Minio();

    public OpenAI getOpenai() { return openai; }
    public void setOpenai(OpenAI openai) { this.openai = openai; }

    public Gemini getGemini() { return gemini; }
    public void setGemini(Gemini gemini) { this.gemini = gemini; }

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public RabbitMQ getRabbitmq() { return rabbitmq; }
    public void setRabbitmq(RabbitMQ rabbitmq) { this.rabbitmq = rabbitmq; }

    public Reddit getReddit() { return reddit; }
    public void setReddit(Reddit reddit) { this.reddit = reddit; }

    public NewsApi getNewsApi() { return newsApi; }
    public void setNewsApi(NewsApi newsApi) { this.newsApi = newsApi; }

    public static class NewsApi {
        private String apiKey;
        private String baseUrl = "https://newsapi.org/v2";
        private int fetchLimitOnStartup = 3;
        private String defaultLanguage = "en";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getFetchLimitOnStartup() { return fetchLimitOnStartup; }
        public void setFetchLimitOnStartup(int fetchLimitOnStartup) { this.fetchLimitOnStartup = fetchLimitOnStartup; }

        public String getDefaultLanguage() { return defaultLanguage; }
        public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
    }

    public static class Minio {
        private String endpoint = "localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin123";
        private String bucket = "shortform";
        private boolean secure = false;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }

        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }
    }

    public Pexels getPexels() { return pexels; }
    public void setPexels(Pexels pexels) { this.pexels = pexels; }

    public Minio getMinio() { return minio; }
    public void setMinio(Minio minio) { this.minio = minio; }

    public static class Pexels {
        private String apiKey;
        private String baseUrl = "https://api.pexels.com/v1";
        private int photosPerKeyword = 3;
        private int videosPerKeyword = 2;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getPhotosPerKeyword() { return photosPerKeyword; }
        public void setPhotosPerKeyword(int photosPerKeyword) { this.photosPerKeyword = photosPerKeyword; }

        public int getVideosPerKeyword() { return videosPerKeyword; }
        public void setVideosPerKeyword(int videosPerKeyword) { this.videosPerKeyword = videosPerKeyword; }
    }

    public static class OpenAI {
        private String apiKey;
        private String model = "gpt-4o";
        private String whisperModel = "whisper-1";
        private String dalleModel = "dall-e-3";
        private String baseUrl = "https://api.openai.com/v1";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getWhisperModel() { return whisperModel; }
        public void setWhisperModel(String whisperModel) { this.whisperModel = whisperModel; }

        public String getDalleModel() { return dalleModel; }
        public void setDalleModel(String dalleModel) { this.dalleModel = dalleModel; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Gemini {
        private String apiKey;
        private String model = "gemini-2.5-flash";
        private String imagenModel = "imagen-4.0-fast-generate-001";
        private String baseUrl = "https://generativelanguage.googleapis.com";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getImagenModel() { return imagenModel; }
        public void setImagenModel(String imagenModel) { this.imagenModel = imagenModel; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Storage {
        private String tempDir = "./temp";
        private String outputDir = "./output";
        private String bgmDir = "./bgm";
        private int tempTtlHours = 24;

        public String getTempDir() { return tempDir; }
        public void setTempDir(String tempDir) { this.tempDir = tempDir; }

        public String getOutputDir() { return outputDir; }
        public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

        public String getBgmDir() { return bgmDir; }
        public void setBgmDir(String bgmDir) { this.bgmDir = bgmDir; }

        public int getTempTtlHours() { return tempTtlHours; }
        public void setTempTtlHours(int tempTtlHours) { this.tempTtlHours = tempTtlHours; }
    }

    public static class RabbitMQ {
        private String renderQueue = "render.queue";
        private String previewQueue = "preview.queue";
        private String exchange = "shortform.exchange";

        public String getRenderQueue() { return renderQueue; }
        public void setRenderQueue(String renderQueue) { this.renderQueue = renderQueue; }

        public String getPreviewQueue() { return previewQueue; }
        public void setPreviewQueue(String previewQueue) { this.previewQueue = previewQueue; }

        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
    }

    public static class Reddit {
        private String clientId;
        private String clientSecret;
        private String userAgent = "CommunityShortform/1.0";

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    }
}
