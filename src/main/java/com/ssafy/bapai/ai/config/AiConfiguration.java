package com.ssafy.bapai.ai.config;

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

    @Value("${spring.ai.google.genai.api-key}")
    private String googleKey;

    @Value("${spring.ai.google.genai.base-url}")
    private String googleUrl;

    // GMS 400 에러 해결을 위한 버퍼링 빌더
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(
                        new SimpleClientHttpRequestFactory()));
    }

    // 1. [Vision용] GPT-4o-mini
    @Bean(name = "visionChatModel")
    @Primary
    public ChatModel visionChatModel(RestClient.Builder restClientBuilder) {
        // ★ [수정] 생성자 대신 Builder 패턴 사용 (M6 버전 이상 권장)
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(openAiUrl)
                .apiKey(openAiKey)
                .restClientBuilder(restClientBuilder) // 버퍼링 설정 주입
                .build();

        return new OpenAiChatModel(api, OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.3)
                .build());
    }

    @Bean(name = "visionChatClient")
    public ChatClient visionChatClient(@Qualifier("visionChatModel") ChatModel model) {
        return ChatClient.create(model);
    }

    // 2. [Report용] Gemini-2.5-Pro
    @Bean(name = "reportChatModel")
    public ChatModel reportChatModel(RestClient.Builder restClientBuilder) {
        // ★ [수정] 생성자 대신 Builder 패턴 사용
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(googleUrl)
                .apiKey(googleKey)
                .restClientBuilder(restClientBuilder) // 버퍼링 설정 주입
                .build();

        return new OpenAiChatModel(api, OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.7)
                .build());
    }

    @Bean(name = "reportChatClient")
    public ChatClient reportChatClient(@Qualifier("reportChatModel") ChatModel model) {
        return ChatClient.create(model);
    }
}