package com.ssafy.bapai.ai.config;

import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfiguration {

    @Value("${spring.ai.openai.api-key}")
    private String openAiKey;

    @Value("${spring.ai.openai.base-url}")
    private String openAiUrl;

    // ✅ 모델/온도: 환경변수로 스위치 가능
    @Value("${APP_AI_VISION_MODEL:gpt-4o}")
    private String visionModel;

    @Value("${APP_AI_VISION_TEMPERATURE:0.3}")
    private Double visionTemp;

    @Value("${APP_AI_REPORT_MODEL:gpt-5}")
    private String reportModel;

    @Value("${APP_AI_REPORT_TEMPERATURE:0.7}")
    private Double reportTemp;

    // ✅ 네트워크 무한대기 방지(장애 시 스레드 고갈/지연 방지)
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(factory));
    }

    // 1) Vision (이미지 분석/가벼운 추천)
    @Bean(name = "visionChatModel")
    @Primary
    public ChatModel visionChatModel(RestClient.Builder restClientBuilder) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(openAiUrl)
                .apiKey(openAiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        return new OpenAiChatModel(api, OpenAiChatOptions.builder()
                .model(visionModel)
                .temperature(visionTemp)
                .build());
    }

    @Bean(name = "visionChatClient")
    public ChatClient visionChatClient(@Qualifier("visionChatModel") ChatModel model) {
        return ChatClient.create(model);
    }

    // 2) Report (주간/월간/갭 등 고급 텍스트)
    @Bean(name = "reportChatModel")
    public ChatModel reportChatModel(RestClient.Builder restClientBuilder) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(openAiUrl) // ✅ 둘 다 OpenAI 호환 GMS로 통일
                .apiKey(openAiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        return new OpenAiChatModel(api, OpenAiChatOptions.builder()
                .model(reportModel) // ✅ 기본 gpt-5 (미지원/불안정하면 gpt-4.1로 env 변경)
                .temperature(reportTemp)
                .build());
    }

    @Bean(name = "reportChatClient")
    public ChatClient reportChatClient(@Qualifier("reportChatModel") ChatModel model) {
        return ChatClient.create(model);
    }
}