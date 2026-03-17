package com.shortform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private OpenAI openai = new OpenAI();
    private Storage storage = new Storage();
    private RabbitMQ rabbitmq = new RabbitMQ();
    private Reddit reddit = new Reddit();

    public OpenAI getOpenai() { return openai; }
    public void setOpenai(OpenAI openai) { this.openai = openai; }

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public RabbitMQ getRabbitmq() { return rabbitmq; }
    public void setRabbitmq(RabbitMQ rabbitmq) { this.rabbitmq = rabbitmq; }

    public Reddit getReddit() { return reddit; }
    public void setReddit(Reddit reddit) { this.reddit = reddit; }

    public static class OpenAI {
        private String apiKey;
        private String model = "gpt-4o";
        private String whisperModel = "whisper-1";
        private String baseUrl = "https://api.openai.com/v1";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getWhisperModel() { return whisperModel; }
        public void setWhisperModel(String whisperModel) { this.whisperModel = whisperModel; }

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
