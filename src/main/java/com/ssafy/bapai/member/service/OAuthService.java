package com.ssafy.bapai.member.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class OAuthService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${oauth2.google.client-id}")
    private String googleClientId;
    @Value("${oauth2.google.client-secret}")
    private String googleClientSecret;
    @Value("${oauth2.google.redirect-uri}")
    private String googleRedirectUri;
    @Value("${oauth2.google.token-uri}")
    private String googleTokenUri;
    @Value("${oauth2.google.user-info-uri}")
    private String googleUserInfoUri;

    @Value("${oauth2.kakao.client-id}")
    private String kakaoClientId;
    @Value("${oauth2.kakao.redirect-uri}")
    private String kakaoRedirectUri;
    @Value("${oauth2.kakao.token-uri}")
    private String kakaoTokenUri;
    @Value("${oauth2.kakao.user-info-uri}")
    private String kakaoUserInfoUri;

    @Value("${oauth2.naver.client-id}")
    private String naverClientId;
    @Value("${oauth2.naver.client-secret}")
    private String naverClientSecret;
    @Value("${oauth2.naver.redirect-uri}")
    private String naverRedirectUri;
    @Value("${oauth2.naver.token-uri}")
    private String naverTokenUri;
    @Value("${oauth2.naver.user-info-uri}")
    private String naverUserInfoUri;

    // 1. 액세스 토큰 받기
    public String getAccessToken(String provider, String code) {
        String url;
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("grant_type", "authorization_code");

        if ("GOOGLE".equalsIgnoreCase(provider)) {
            url = googleTokenUri;
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("redirect_uri", googleRedirectUri);
        } else if ("KAKAO".equalsIgnoreCase(provider)) {
            url = kakaoTokenUri;
            params.add("client_id", kakaoClientId);
            params.add("redirect_uri", kakaoRedirectUri);
        } else if ("NAVER".equalsIgnoreCase(provider)) {
            url = naverTokenUri;
            params.add("client_id", naverClientId);
            params.add("client_secret", naverClientSecret);
            params.add("redirect_uri", naverRedirectUri);
            params.add("state", "test_state"); // 네이버 필수값
        } else {
            throw new RuntimeException("지원하지 않는 소셜 로그인입니다: " + provider);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            Map response = restTemplate.postForObject(url, request, Map.class);
            if (response == null || response.get("access_token") == null) {
                throw new RuntimeException("토큰 응답이 없습니다.");
            }
            return (String) response.get("access_token");
        } catch (Exception e) {
            throw new RuntimeException("토큰 요청 실패: " + e.getMessage());
        }
    }

    // 2. 사용자 정보 가져오기
    public Map<String, Object> getUserInfo(String provider, String accessToken) {
        String url;

        //  Provider에 따라 URL 분기 처리
        if ("GOOGLE".equalsIgnoreCase(provider)) {
            url = googleUserInfoUri;
        } else if ("KAKAO".equalsIgnoreCase(provider)) {
            url = kakaoUserInfoUri;
        } else if ("NAVER".equalsIgnoreCase(provider)) {
            url = naverUserInfoUri;
        } else {
            throw new RuntimeException("지원하지 않는 소셜 로그인입니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            Map response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class).getBody();

            if (response == null) {
                throw new RuntimeException("유저 정보 응답이 비어있습니다.");
            }

            Map<String, Object> result = new HashMap<>();

            if ("KAKAO".equalsIgnoreCase(provider)) {
                Map<String, Object> account = (Map<String, Object>) response.get("kakao_account");
                if (account == null) {
                    throw new RuntimeException("카카오 계정 정보가 없습니다.");
                }

                Map<String, Object> profile = (Map<String, Object>) account.get("profile");
                String nickname = (profile != null && profile.get("nickname") != null) ?
                        (String) profile.get("nickname") : "KakaoUser";
                String email = (account.get("email") != null) ? (String) account.get("email") :
                        "kakao_no_email_" + response.get("id");

                result.put("email", email);
                result.put("name", nickname);
                result.put("providerId", String.valueOf(response.get("id")));

            } else if ("NAVER".equalsIgnoreCase(provider)) { // 네이버 파싱 로직
                Map<String, Object> responseObj = (Map<String, Object>) response.get("response");

                result.put("email", responseObj.get("email"));
                result.put("name", responseObj.get("name"));
                result.put("providerId", (String) responseObj.get("id"));

            } else { // GOOGLE
                String providerId = (String) response.get("sub");
                if (providerId == null) {
                    providerId = (String) response.get("id");
                }

                result.put("email", response.get("email"));
                result.put("name", response.get("name"));
                result.put("providerId", providerId);
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("사용자 정보 파싱 실패: " + e.getMessage());
        }
    }
}