package com.ssafy.bapai.group.service;

import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import java.util.List;

public interface GroupService {
    // 팀(Team) -> 모임(Group) 용어 변경
    void createGroup(GroupDto groupDto);

    List<GroupDto> getList(String keyword, Long userId);

    GroupDto getDetail(Long groupId, Long userId);

    void joinGroup(Long groupId, Long userId);

    void leaveGroup(Long groupId, Long userId);

    void kickMember(Long groupId, Long ownerId, Long targetUserId);

    // leaderId -> ownerId, teamId -> groupId
    void delegateOwner(Long groupId, Long currentOwnerId, Long newOwnerId);

    List<GroupRankDto> getGroupRanking(Long groupId);
}