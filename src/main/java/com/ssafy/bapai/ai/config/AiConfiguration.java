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

    @Value("${spring.ai.google.genai.api-key}")
    private String googleKey;

    @Value("${spring.ai.google.genai.base-url}")
    private String googleUrl;

    // ✅ 모델명 외부화(운영에서 바꾸기 쉽게)
    @Value("${app.ai.vision.model:gpt-4o}")
    private String visionModelName;

    @Value("${app.ai.report.model:gpt-5}")
    private String reportModelName;

    // ✅ GMS 400/파싱 이슈 추적에 도움 + 타임아웃 추가
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(factory));
    }

    // 1) Vision (멀티모달)
    @Bean(name = "visionChatModel")
    @Primary
    public ChatModel visionChatModel(RestClient.Builder restClientBuilder) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(openAiUrl)
                .apiKey(openAiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        return new OpenAiChatModel(api, OpenAiChatOptions.builder()
                .model(visionModelName) // ✅ 기본 gpt-4o
                .temperature(0.3)
                .build());
    }

    @Bean(name = "visionChatClient")
    public ChatClient visionChatClient(@Qualifier("visionChatModel") ChatModel model) {
        return ChatClient.create(model);
    }

    // 2) Report (텍스트 고급)
    @Bean(name = "reportChatModel")
    public ChatModel reportChatModel(RestClient.Builder restClientBuilder) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(googleUrl)   // ✅ GMS(OpenAI 호환) 엔드포인트로 사용 중인 것으로 보임
                .apiKey(googleKey)
                .restClientBuilder(restClientBuilder)
                .build();

        return new OpenAiChatModel(api, OpenAiChatOptions.builder()
                .model(reportModelName) // ✅ 기본 gpt-5 (지원 안 되면 gpt-4.1로 변경)
                .temperature(0.7)
                .build());
    }

    @Bean(name = "reportChatClient")
    public ChatClient reportChatClient(@Qualifier("reportChatModel") ChatModel model) {
        return ChatClient.create(model);
    }
}