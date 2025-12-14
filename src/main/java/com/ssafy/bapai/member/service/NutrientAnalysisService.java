package com.ssafy.bapai.member.service;

import com.ssafy.bapai.member.dao.MemberDao;
import com.ssafy.bapai.member.dao.MemberGoalDao;
import com.ssafy.bapai.member.dto.MemberDto;
import com.ssafy.bapai.member.dto.MemberGoalDto;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NutrientAnalysisService {

    private final MemberDao memberDao;
    private final MemberGoalDao memberGoalDao;

    @Transactional
    public void analyzeAndSave(Long userId) {
        // 1. 회원 정보(키, 몸무게, 나이 등) 조회
        MemberDto member = memberDao.selectMemberDetail(userId); // 기존에 만들어둔 조회 메서드 사용

        // 2. 나이 계산 (만 나이 간략 계산)
        int currentYear = LocalDate.now().getYear();
        int age = currentYear - member.getBirthYear();

        // 3. 기초대사량(BMR) 계산 (Mifflin-St Jeor 공식)
        double bmr = calculateBMR(member.getHeight(), member.getWeight(), age, member.getGender());

        // 4. 활동대사량(TDEE) 계산
        double tdee = calculateTDEE(bmr, member.getActivityLevel());

        // 5. 목표 칼로리 설정 (다이어트/벌크업)
        double targetCal = adjustCaloriesForGoal(tdee, member.getDietGoal());

        // 6. 탄단지 비율 설정 (일반적인 5:3:2 비율 적용, 질병 고려 로직 추가 가능)
        // 탄수화물 50%, 단백질 30%, 지방 20% 가정
        double targetCarbs = (targetCal * 0.5) / 4.0;    // 1g = 4kcal
        double targetProtein = (targetCal * 0.3) / 4.0;  // 1g = 4kcal
        double targetFat = (targetCal * 0.2) / 9.0;      // 1g = 9kcal

        // 7. DTO 생성 및 저장
        MemberGoalDto goalDto = new MemberGoalDto();
        goalDto.setUserId(userId);
        goalDto.setActivityLevel(member.getActivityLevel());
        goalDto.setDietGoal(member.getDietGoal());
        goalDto.setBmr(bmr);
        goalDto.setTdee(tdee);
        goalDto.setRecCalories(targetCal);
        goalDto.setRecCarbs(targetCarbs);
        goalDto.setRecProtein(targetProtein);
        goalDto.setRecFat(targetFat);

        // 기존 데이터가 있으면 Update, 없으면 Insert
        if (memberGoalDao.checkExist(userId) > 0) {
            memberGoalDao.updateGoal(goalDto);
        } else {
            memberGoalDao.insertGoal(goalDto);
        }
    }

    // --- 계산 공식 메서드들 ---

    private double calculateBMR(double height, double weight, int age, String gender) {
        // Mifflin-St Jeor 공식
        double base = (10 * weight) + (6.25 * height) - (5 * age);
        return "M".equals(gender) ? base + 5 : base - 161;
    }

    private double calculateTDEE(double bmr, String activityLevel) {
        switch (activityLevel) {
            case "LOW":
                return bmr * 1.2;      // 좌식 생활
            case "NORMAL":
                return bmr * 1.55;  // 적당한 활동
            case "HIGH":
                return bmr * 1.9;     // 격렬한 활동
            default:
                return bmr * 1.2;
        }
    }

    private double adjustCaloriesForGoal(double tdee, String goal) {
        if ("LOSS".equals(goal)) {
            return tdee - 500.0;   // 감량
        }
        if ("GAIN".equals(goal)) {
            return tdee + 300.0;   // 증량
        }
        return tdee;                                    // 유지
    }
}