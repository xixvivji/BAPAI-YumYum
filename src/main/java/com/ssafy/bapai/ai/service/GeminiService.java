package com.ssafy.bapai.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.url}")
    private String apiUrl;

    private final ObjectMapper objectMapper;

    /**
     * 1. 이미지 분석 (Vision)
     * - AiService의 analyzeImage()에서 호출
     */
    public String analyzeFoodImage(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(fileBytes);

            String prompt = """
                    이 음식 사진을 분석해서 다음 JSON 형식으로만 답해줘. 설명이나 마크다운(```json)은 절대 쓰지 마.
                    형식:
                    {
                        "menuName": "음식명(한글)",
                        "calories": 300,
                        "carbs": 50,
                        "protein": 20,
                        "fat": 10,
                        "score": 85
                    }
                    """;

            return callGeminiApi(prompt, base64Image);

        } catch (Exception e) {
            log.error("Gemini 이미지 분석 오류", e);
            return "{}"; // 실패 시 빈 JSON 반환
        }
    }

    /**
     * 2. 텍스트 질의 (Text Only)
     * - AiService의 리포트, 메뉴 추천, 챌린지 추천 등 모든 곳에서 재사용
     */
    public String chatWithText(String prompt) {
        try {
            return callGeminiApi(prompt, null);
        } catch (Exception e) {
            log.error("Gemini 텍스트 분석 오류", e);
            return "AI 분석을 불러오지 못했습니다.";
        }
    }

    // --- 내부 통신 로직 (WebClient 사용) ---

    private String callGeminiApi(String text, String base64Image) {
        // 1. 요청 바디 생성
        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();

        // (1) 텍스트 프롬프트
        parts.add(Map.of("text", text));

        // (2) 이미지 (있을 경우에만 추가)
        if (base64Image != null) {
            Map<String, Object> imagePart = new HashMap<>();
            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mime_type", "image/jpeg");
            inlineData.put("data", base64Image);
            imagePart.put("inline_data", inlineData);
            parts.add(imagePart);
        }

        content.put("parts", parts);
        contents.add(content);
        requestBody.put("contents", contents);

        // 2. WebClient 호출
        WebClient webClient = WebClient.create();
        String response = webClient.post()
                .uri(apiUrl + "?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(); // 동기 처리

        // 3. 결과 파싱
        return extractJsonFromResponse(response);
    }

    private String extractJsonFromResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            // 마크다운 제거 (```json, ``` 등)
            if (text.startsWith("```")) {
                text = text.replaceAll("```json", "").replaceAll("```", "");
            }
            return text.trim();

        } catch (Exception e) {
            log.error("응답 파싱 실패: " + response, e);
            return response; // 파싱 실패하면 원본 텍스트라도 리턴
        }
    }
}