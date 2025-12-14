package com.ssafy.bapai.team.service;

import com.ssafy.bapai.team.dao.TeamBoardDao;
import com.ssafy.bapai.team.dao.TeamDao;
import com.ssafy.bapai.team.dto.TeamBoardDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamBoardServiceImpl { // (Interface 생략하고 바로 클래스로 쓰셔도 됩니다)
    private final TeamBoardDao teamBoardDao;
    private final TeamDao teamDao;

    @Transactional
    public void writeBoard(TeamBoardDto boardDto) {
        if ("NOTICE".equals(boardDto.getType())) {
            if (!"LEADER".equals(
                    teamDao.selectMyRole(boardDto.getTeamId(), boardDto.getUserId()))) {
                throw new IllegalStateException("공지는 방장만 작성 가능");
            }
        }
        if (teamDao.checkJoined(boardDto.getTeamId(), boardDto.getUserId()) == 0) {
            throw new IllegalStateException("팀원만 작성 가능");
        }
        teamBoardDao.insertBoard(boardDto);
    }

    public List<TeamBoardDto> getList(Long teamId) {
        return teamBoardDao.selectBoardList(teamId);
    }

    public TeamBoardDto getDetail(Long tbId, Long userId) {
        TeamBoardDto dto = teamBoardDao.selectBoardDetail(tbId);
        if (userId != null && dto.getUserId().equals(userId)) {
            dto.setWriter(true);
        }
        return dto;
    }

    @Transactional
    public void deleteBoard(Long tbId, Long userId) {
        TeamBoardDto dto = teamBoardDao.selectBoardDetail(tbId);
        String role = teamDao.selectMyRole(dto.getTeamId(), userId);
        if (!dto.getUserId().equals(userId) && !"LEADER".equals(role)) {
            throw new IllegalStateException("삭제 권한 없음");
        }
        teamBoardDao.deleteBoard(tbId);
    }
}