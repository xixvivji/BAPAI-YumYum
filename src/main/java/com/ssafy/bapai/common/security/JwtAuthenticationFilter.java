package com.ssafy.bapai.common.security;

import com.ssafy.bapai.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 요청 URL 확인 (로그인 등은 통과)
        String requestURI = request.getRequestURI();

        // 2. 헤더 확인
        String header = request.getHeader("Authorization");

        //  로그
        System.out.println("============== [필터 시작] ==============");
        System.out.println("요청 URL: " + requestURI);
        System.out.println("헤더 값: " + header);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // "Bearer " 제거
            System.out.println("추출된 토큰: " + token); // 토큰 값 확인

            try {
                // 3. 토큰 검증
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserId(header);
                    System.out.println("✅ 토큰 검증 성공! ID: " + userId);

                    // 인증 객체 생성 & 저장
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    System.out.println("✅ SecurityContext 인증 정보 저장 완료");
                } else {
                    System.out.println("🚨 토큰 검증 실패 (validateToken false 반환)");
                }
            } catch (Exception e) {
                System.out.println("🚨 에러 발생: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("⚠️ 헤더가 없거나 Bearer 형식이 아님 (익명 사용자로 진행)");
        }

        System.out.println("============== [필터 끝] ==============");

        filterChain.doFilter(request, response);
    }
}
