package com.ssafy.bapai.team.service;

import com.ssafy.bapai.team.dao.TeamDao;
import com.ssafy.bapai.team.dto.TeamDto;
import com.ssafy.bapai.team.dto.TeamRankDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {
    private final TeamDao teamDao;

    @Override
    @Transactional
    public void createTeam(TeamDto teamDto) {
        teamDao.insertTeam(teamDto);
        teamDao.insertTeamMember(teamDto.getTeamId(), teamDto.getLeaderId(), "LEADER");
    }

    @Override
    public List<TeamDto> getList(String keyword, Long userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        List<TeamDto> list = teamDao.selectTeamList(params);
        if (userId != null) {
            for (TeamDto t : list) {
                String myRole = teamDao.selectMyRole(t.getTeamId(), userId);
                if (myRole != null) {
                    t.setJoined(true);
                    t.setMyRole(myRole);
                }
            }
        }
        return list;
    }

    @Override
    public TeamDto getDetail(Long teamId, Long userId) {
        TeamDto team = teamDao.selectTeamDetail(teamId);
        if (userId != null) {
            String myRole = teamDao.selectMyRole(teamId, userId);
            if (myRole != null) {
                team.setJoined(true);
                team.setMyRole(myRole);
            }
        }
        return team;
    }

    @Override
    @Transactional
    public void joinTeam(Long teamId, Long userId) {
        if (teamDao.checkJoined(teamId, userId) > 0) {
            throw new IllegalStateException("이미 가입했습니다.");
        }
        TeamDto team = teamDao.selectTeamDetail(teamId);
        if (teamDao.countMembers(teamId) >= team.getMaxMembers()) {
            throw new IllegalStateException("정원 초과입니다.");
        }
        teamDao.insertTeamMember(teamId, userId, "MEMBER");
    }

    @Override
    public void leaveTeam(Long teamId, Long userId) {
        String myRole = teamDao.selectMyRole(teamId, userId);
        if ("LEADER".equals(myRole) && teamDao.countMembers(teamId) > 1) {
            throw new IllegalStateException("방장은 탈퇴 불가. 위임하거나 팀을 삭제하세요.");
        }
        teamDao.deleteTeamMember(teamId, userId);
    }

    @Override
    @Transactional
    public void kickMember(Long teamId, Long leaderId, Long targetUserId) {
        if (!"LEADER".equals(teamDao.selectMyRole(teamId, leaderId))) {
            throw new IllegalStateException("권한 없음");
        }
        teamDao.deleteTeamMember(teamId, targetUserId);
    }

    @Override
    @Transactional
    public void delegateLeader(Long teamId, Long currentLeaderId, Long newLeaderId) {
        if (!"LEADER".equals(teamDao.selectMyRole(teamId, currentLeaderId))) {
            throw new IllegalStateException("권한 없음");
        }
        teamDao.updateMemberRole(teamId, currentLeaderId, "MEMBER");
        teamDao.updateMemberRole(teamId, newLeaderId, "LEADER");
        teamDao.updateTeamLeader(teamId, newLeaderId);
    }

    @Override
    public List<TeamRankDto> getTeamRanking(Long teamId) {
        List<TeamRankDto> list = teamDao.selectTeamRanking(teamId, 5); // 상위 5명
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        return list;
    }
}