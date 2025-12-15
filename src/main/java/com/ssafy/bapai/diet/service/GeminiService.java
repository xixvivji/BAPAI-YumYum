package com.ssafy.bapai.diet.service;

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

    private final ObjectMapper objectMapper; // JSON 파싱용 (Spring이 자동 주입)

    // 메인 메서드: 이미지 받아서 분석 결과(JSON String) 리턴
    public String analyzeImage(MultipartFile file) {
        try {
            // 1. 이미지 -> Base64 변환
            byte[] fileBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(fileBytes);

            // 2. 요청 Body 만들기 (Gemini 전용 규격)
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();

            // (1) 프롬프트 (명령어)
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", """
                    이 음식 사진을 분석해서 다음 JSON 형식으로만 답해줘. 설명이나 마크다운(```json)은 절대 쓰지 마.
                    형식:
                    {
                        "predictedFoods": [
                            {
                                "name": "음식명(한글)",
                                "probability": 85.5,
                                "kcal": 300,
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

            // 3. Google Gemini API 호출
            WebClient webClient = WebClient.create();
            String response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey) // URL 뒤에 키 붙임
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // 결과가 올 때까지 기다림 (동기)

            // 4. 응답에서 알맹이(JSON)만 추출
            return extractJsonFromResponse(response);

        } catch (Exception e) {
            log.error("Gemini 분석 중 오류 발생", e);
            // 에러 나면 빈 리스트 리턴 (프론트 에러 방지)
            return "{\"predictedFoods\": []}";
        }
    }

    // Gemini의 복잡한 응답 구조에서 텍스트만 쏙 뽑아내는 헬퍼 메서드
    private String extractJsonFromResponse(String response) {
        try {
            // Jackson 라이브러리로 구조 파싱
            JsonNode root = objectMapper.readTree(response);

            // 경로: candidates[0] -> content -> parts[0] -> text
            String text = root.path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            // AI가 가끔 ```json ... ``` 이런 식으로 마크다운을 붙여서 줄 때가 있음. 제거 로직
            if (text.startsWith("```")) {
                text = text.replaceAll("```json", "").replaceAll("```", "");
            }
            return text.trim();

        } catch (Exception e) {
            log.error("응답 파싱 실패: " + response, e);
            return "{\"predictedFoods\": []}";
        }
    }

    // 텍스트 기반 식단 추천 메서드 (추가)
    public String recommendMenu(String dailyDietLog) {
        try {
            // 1. 프롬프트 작성
            String prompt = """
                    사용자가 오늘 먹은 음식 목록이야:
                    %s
                    
                    이걸 분석해서 부족한 영양소를 파악하고, 다음 끼니로 적절한 메뉴 3가지를 추천해줘.
                    응답은 오직 아래 JSON 형식으로만 줘. (설명 금지)
                    {
                        "recommendedMenus": ["메뉴1", "메뉴2", "메뉴3"],
                        "reason": "나트륨 섭취가 많아 칼륨이 풍부한 음식이 필요합니다."
                    }
                    """.formatted(dailyDietLog);

            // 2. 요청 Body 생성 (텍스트 전용)
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();

            parts.add(Map.of("text", prompt)); // 텍스트만 보냄
            content.put("parts", parts);
            contents.add(content);
            requestBody.put("contents", contents);

            // 3. API 호출
            WebClient webClient = WebClient.create();
            String response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 4. JSON 추출 (기존 메서드 재활용)
            return extractJsonFromResponse(response);

        } catch (Exception e) {
            log.error("식단 추천 실패", e);
            // 에러 시 기본값 리턴
            return "{\"recommendedMenus\": [\"샐러드\", \"비빔밥\"], \"reason\": \"AI 분석 중 오류가 발생하여 기본 메뉴를 추천합니다.\"}";
        }
    }


    // 키워드로 챌린지 추천 받기 (Raw JSON String 반환)
    public String recommendChallengesByAI(List<String> keywords) {
        try {
            String keywordStr = (keywords == null || keywords.isEmpty()) ? "건강, 식습관, 운동" :
                    String.join(", ", keywords);

            // 프롬프트 엔지니어링: 응답 형식을 DTO 필드명과 정확히 일치시켜야 파싱이 쉽습니다.
            String prompt = """
                    사용자 관심사: [%s]
                    
                    위 관심사에 맞는 3명의 팀원이 함께할 수 있는 재미있는 챌린지 3개를 추천해줘.
                    응답은 반드시 아래 JSON 배열 포맷으로만 출력해. (설명, 마크다운, 코드블럭 절대 금지)
                    
                    [
                        {
                            "title": "챌린지 제목 (이모지 포함)",
                            "content": "구체적인 인증 방법 설명",
                            "goalType": "COUNT",
                            "targetCount": 5,
                            "keyword": "관련키워드"
                        }
                    ]
                    """.formatted(keywordStr);

            // (이하 기존 analyzeImage 메서드와 동일한 방식의 호출 로직)
            // 텍스트 전용 모델을 호출하므로 parts에 text만 넣어서 보냅니다.
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();

            parts.add(Map.of("text", prompt));
            content.put("parts", parts);
            contents.add(content);
            requestBody.put("contents", contents);

            WebClient webClient = WebClient.create();
            String response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractJsonFromResponse(response);

        } catch (Exception e) {
            log.error("Gemini 챌린지 추천 실패", e);
            return "[]"; // 에러 시 빈 배열 반환
        }
    }
}