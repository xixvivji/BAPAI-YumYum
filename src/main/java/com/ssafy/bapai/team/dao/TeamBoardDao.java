package com.ssafy.bapai.team.dao;

import com.ssafy.bapai.team.dto.TeamBoardDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TeamBoardDao {
    void insertBoard(TeamBoardDto boardDto);

    List<TeamBoardDto> selectBoardList(Long teamId);

    TeamBoardDto selectBoardDetail(Long tbId);

    void deleteBoard(Long tbId);
}