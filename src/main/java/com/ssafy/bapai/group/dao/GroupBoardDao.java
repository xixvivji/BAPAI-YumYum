package com.ssafy.bapai.group.dao;

import com.ssafy.bapai.group.dto.GroupBoardDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GroupBoardDao {
    void insertBoard(GroupBoardDto boardDto);

    List<GroupBoardDto> selectBoardList(Long groupId);

    GroupBoardDto selectBoardDetail(Long boardId); // boardId

    void deleteBoard(Long boardId); // boardId
}