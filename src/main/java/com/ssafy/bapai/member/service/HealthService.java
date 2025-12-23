package com.ssafy.bapai.member.service;

import com.ssafy.bapai.member.dto.MemberDto;
import com.ssafy.bapai.member.dto.MemberGoalDto;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    // MemberDto(신체정보)를 받아서 -> MemberGoalDto(분석결과)를 반환
    public MemberGoalDto calculateHealthMetrics(MemberDto memberDto) {

        // 1. 나이 계산
        int currentYear = LocalDate.now().getYear();
        int age = currentYear - memberDto.getBirthYear();

        // 2. 기초대사량(BMR) 계산 (Mifflin-St Jeor 공식)
        double bmr = calculateBMR(memberDto, age);

        // 3. 활동대사량(TDEE) 계산 (유지 칼로리)
        double tdee = calculateTDEE(bmr, memberDto.getActivityLevel());

        // 4. 목표(DietGoal)에 따른 칼로리 조정 및 탄단지 비율 설정
        double targetCalories = tdee; // 기본은 유지 칼로리
        double ratioCarbs, ratioProtein, ratioFat;

        // null 방어 로직 (기본값: MAINTAIN)
        String goal = memberDto.getDietGoal() != null ? memberDto.getDietGoal() : "MAINTAIN";

        switch (goal) {
            case "LOSS":
                // [다이어트]
                // 1. 칼로리: TDEE에서 500kcal 감소 (약 0.5kg/주 감량 목표)
                targetCalories -= 500;
                // 2. 비율: 탄4 : 단4 : 지2 (근손실 방지를 위해 단백질 강화)
                ratioCarbs = 0.4;
                ratioProtein = 0.4;
                ratioFat = 0.2;
                break;

            case "GAIN":
                // [벌크업]
                // 1. 칼로리: TDEE에서 300kcal 증가 (체지방 최소화 근육 증가)
                targetCalories += 300;
                // 2. 비율: 탄5 : 단3 : 지2 (에너지 확보를 위해 탄수화물 충분히)
                ratioCarbs = 0.5;
                ratioProtein = 0.3;
                ratioFat = 0.2;
                break;

            case "MAINTAIN":
            default:
                // [유지]
                // 1. 칼로리: TDEE 그대로
                // 2. 비율: 탄5 : 단2 : 지3 (일반적인 건강 유지 비율)
                ratioCarbs = 0.5;
                ratioProtein = 0.2;
                ratioFat = 0.3;
                break;
        }

        // ※ 하루 섭취 칼로리가 너무 낮아지지 않도록 안전장치 (예: 최소 1200kcal)
        if (targetCalories < 1200) {
            targetCalories = 1200;
        }

        // 5. 그램(g) 수 계산
        // 탄수화물(4kcal), 단백질(4kcal), 지방(9kcal)
        double recCarbs = Math.round((targetCalories * ratioCarbs) / 4.0);
        double recProtein = Math.round((targetCalories * ratioProtein) / 4.0);
        double recFat = Math.round((targetCalories * ratioFat) / 9.0);

        // 6. 결과 DTO 생성 및 세팅
        MemberGoalDto result = new MemberGoalDto();
        result.setUserId(memberDto.getUserId());
        result.setActivityLevel(memberDto.getActivityLevel());
        result.setDietGoal(goal);

        result.setBmr(Math.round(bmr));
        result.setTdee(Math.round(tdee)); // 내 몸이 사용하는 에너지 총량

        // ★ 중요: 추천 섭취량은 목표에 따라 조정된 값(targetCalories)을 넣습니다.
        result.setRecCalories(Math.round(targetCalories));

        result.setRecCarbs(recCarbs);
        result.setRecProtein(recProtein);
        result.setRecFat(recFat);

        return result;
    }

    // BMR 계산 로직
    private double calculateBMR(MemberDto m, int age) {
        double base = (10 * m.getWeight()) + (6.25 * m.getHeight()) - (5 * age);
        return "M".equalsIgnoreCase(m.getGender()) ? base + 5 : base - 161;
    }

    // TDEE 계산 로직
    private double calculateTDEE(double bmr, String activityLevel) {
        double multiplier = 1.2;

        if (activityLevel != null) {
            switch (activityLevel) {
                case "LOW":
                    multiplier = 1.2;
                    break;
                case "NORMAL":
                    multiplier = 1.55;
                    break;
                case "HIGH":
                    multiplier = 1.9;
                    break;
                default:
                    multiplier = 1.2;
            }
        }
        return bmr * multiplier;
    }
}