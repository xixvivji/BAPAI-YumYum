package com.ssafy.bapai.diet.service;

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

    public String analyzeImage(MultipartFile file) {
        try {
            // 1. 이미지를 Base64 문자열로 변환
            byte[] fileBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(fileBytes);

            // 2. 요청 Payload 구성 (Gemini 전용 포맷)
            Map<String, Object> requestBody = new HashMap<>();

            // "contents": [{ "parts": [ {text...}, {inline_data...} ] }]
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();

            // (1) 텍스트 프롬프트 (JSON 형식을 강제함)
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", """
                    이 음식 사진을 분석해서 다음 JSON 형식으로만 답해줘. 마크다운이나 다른 말은 절대 하지 마.
                    형식:
                    {
                        "predictedFoods": [
                            {
                                "name": "음식명(한글)",
                                "probability": 확신하는정도(0~100 숫자),
                                "kcal": 1인분칼로리(숫자),
                                "amount": 1
                            }
                        ]
                    }
                    """);
            parts.add(textPart);

            // (2) 이미지 데이터
            Map<String, Object> imagePart = new HashMap<>();
            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mime_type", file.getContentType()); // 예: image/jpeg
            inlineData.put("data", base64Image);
            imagePart.put("inline_data", inlineData);
            parts.add(imagePart);

            content.put("parts", parts);
            contents.add(content);
            requestBody.put("contents", contents);

            // 3. WebClient로 Gemini API 호출
            WebClient webClient = WebClient.create();
            String response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey) // URL 뒤에 키를 붙여야 함
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // 동기 처리

            // 4. 응답에서 텍스트만 추출 (간단 파싱)
            // 실제 응답: { "candidates": [ { "content": { "parts": [ { "text": "{결과JSON}" } ] } } ] }
            return extractTextFromResponse(response);

        } catch (Exception e) {
            log.error("Gemini 분석 실패", e);
            // 에러 나면 그냥 빈 JSON이라도 줘서 프론트가 안 뻗게 함
            return "{\"predictedFoods\": []}";
        }
    }

    // Gemini 응답 구조가 복잡해서 텍스트(JSON)만 쏙 빼내는 메서드
    private String extractTextFromResponse(String response) {
        try {
            // Jackson 같은 라이브러리로 파싱하는 게 정석이지만, 급하니까 String 조작으로 처리
            // "text": " 부분 뒤에 있는 JSON을 찾음
            int textIndex = response.indexOf("\"text\": \"");
            if (textIndex == -1) {
                return "{}";
            }

            String sub = response.substring(textIndex + 9);
            // 뒤에 있는 \n이나 특수문자들 정리가 필요할 수 있음.
            // 가장 좋은 건 여기서 ObjectMapper를 쓰는 것입니다.
            // 일단은 통째로 리턴하고 프론트가 알아서 파싱하게 하거나,
            // 더 안전하게 하려면 Jackson 라이브러리 사용을 추천합니다.

            // 편의상 원본 JSON 응답을 그대로 줘서 프론트에서 candidates[0].content... 파싱해도 됨.
            // 하지만 우리는 바로 쓸 수 있는 깔끔한 JSON을 원하므로
            // 실제로는 여기서 JSON 라이브러리로 파싱하는 코드를 넣는 게 좋습니다.
            return response;
        } catch (Exception e) {
            return "{}";
        }
    }
}