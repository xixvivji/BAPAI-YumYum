package com.ssafy.bapai.group.service;

import com.ssafy.bapai.group.dao.GroupBoardDao;
import com.ssafy.bapai.group.dao.GroupDao;
import com.ssafy.bapai.group.dto.GroupBoardDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupBoardServiceImpl {
    private final GroupBoardDao groupBoardDao;
    private final GroupDao groupDao;

    @Transactional
    public void writeBoard(GroupBoardDto boardDto) {
        // 공지는 LEADER만 작성 가능
        if ("NOTICE".equals(boardDto.getType())) {
            if (!"LEADER".equals(
                    groupDao.selectMyRole(boardDto.getGroupId(), boardDto.getUserId()))) {
                throw new IllegalStateException("공지는 방장만 작성 가능");
            }
        }
        // 멤버만 작성 가능
        if (groupDao.checkJoined(boardDto.getGroupId(), boardDto.getUserId()) == 0) {
            throw new IllegalStateException("모임 멤버만 작성 가능");
        }
        groupBoardDao.insertBoard(boardDto);
    }

    public List<GroupBoardDto> getList(Long groupId) {
        return groupBoardDao.selectBoardList(groupId);
    }

    public GroupBoardDto getDetail(Long boardId, Long userId) {
        GroupBoardDto dto = groupBoardDao.selectBoardDetail(boardId);
        if (userId != null && dto.getUserId().equals(userId)) {
            dto.setWriter(true);
        }
        return dto;
    }

    @Transactional
    public void deleteBoard(Long boardId, Long userId) {
        GroupBoardDto dto = groupBoardDao.selectBoardDetail(boardId);
        String role = groupDao.selectMyRole(dto.getGroupId(), userId);

        // 작성자 본인이거나 방장이면 삭제 가능
        if (!dto.getUserId().equals(userId) && !"LEADER".equals(role)) {
            throw new IllegalStateException("삭제 권한 없음");
        }
        groupBoardDao.deleteBoard(boardId);
    }
}