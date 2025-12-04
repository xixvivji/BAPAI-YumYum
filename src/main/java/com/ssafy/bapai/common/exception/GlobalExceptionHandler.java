package com.ssafy.bapai.common.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // @Valid 유효성 검사 실패 시 실행됨
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();

        // 첫 번째 에러 메시지만 가져오기 (예: "이메일은 필수입니다.")
        String errorMessage = bindingResult.getFieldError().getDefaultMessage();

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", errorMessage);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 그 외 모든 에러 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAllExceptions(Exception ex) {
        ex.printStackTrace(); // 콘솔에 에러 출력
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "서버 오류: " + ex.getMessage()));
    }
}