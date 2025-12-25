package com.ssafy.bapai.member.service;

import com.ssafy.bapai.member.dao.HealthDao;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OptionService {
    private final HealthDao healthDao;

    public String diseaseNames(List<Integer> diseaseIds) {
        if (diseaseIds == null || diseaseIds.isEmpty()) {
            return "없음";
        }
        List<String> names = healthDao.selectDiseaseNamesByIds(diseaseIds);
        return (names == null || names.isEmpty()) ? "없음" : String.join(", ", names);
    }

    public String allergyNames(List<Integer> allergyIds) {
        if (allergyIds == null || allergyIds.isEmpty()) {
            return "없음";
        }
        List<String> names = healthDao.selectAllergyNamesByIds(allergyIds);
        return (names == null || names.isEmpty()) ? "없음" : String.join(", ", names);
    }
}