package com.ssafy.bapai.team.dao;

import com.ssafy.bapai.team.dto.TeamDto;
import com.ssafy.bapai.team.dto.TeamRankDto;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeamDao {
    // 기본 CRUD
    void insertTeam(TeamDto teamDto);

    List<TeamDto> selectTeamList(Map<String, Object> params);

    TeamDto selectTeamDetail(Long teamId);

    // 멤버 관리
    void insertTeamMember(@Param("teamId") Long teamId, @Param("userId") Long userId,
                          @Param("role") String role);

    void deleteTeamMember(@Param("teamId") Long teamId, @Param("userId") Long userId);

    // 유틸
    int checkJoined(@Param("teamId") Long teamId, @Param("userId") Long userId);

    int countMembers(Long teamId);

    String selectMyRole(@Param("teamId") Long teamId, @Param("userId") Long userId);

    // 방장 위임
    void updateTeamLeader(@Param("teamId") Long teamId, @Param("newLeaderId") Long newLeaderId);

    void updateMemberRole(@Param("teamId") Long teamId, @Param("userId") Long userId,
                          @Param("role") String role);

    // 랭킹 조회
    List<TeamRankDto> selectTeamRanking(@Param("teamId") Long teamId, @Param("limit") int limit);
}