package com.ssafy.bapai.team.service;

import com.ssafy.bapai.team.dto.TeamDto;
import com.ssafy.bapai.team.dto.TeamRankDto;
import java.util.List;

public interface TeamService {
    void createTeam(TeamDto teamDto);

    List<TeamDto> getList(String keyword, Long userId);

    TeamDto getDetail(Long teamId, Long userId);

    void joinTeam(Long teamId, Long userId);

    void leaveTeam(Long teamId, Long userId);

    void kickMember(Long teamId, Long leaderId, Long targetUserId);

    void delegateLeader(Long teamId, Long currentLeaderId, Long newLeaderId);

    List<TeamRankDto> getTeamRanking(Long teamId);
}