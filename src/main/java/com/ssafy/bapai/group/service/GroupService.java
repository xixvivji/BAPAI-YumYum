package com.ssafy.bapai.group.service;

import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import com.ssafy.bapai.member.dto.MemberDto;
import java.util.List;

public interface GroupService {
    // 팀(Team) -> 모임(Group) 용어 변경
    void createGroup(GroupDto groupDto);


    GroupDto getDetail(Long groupId, Long userId);

    void joinGroup(Long groupId, Long userId);

    void leaveGroup(Long groupId, Long userId);

    void kickMember(Long groupId, Long ownerId, Long targetUserId);

    // leaderId -> ownerId, teamId -> groupId
    void delegateOwner(Long groupId, Long currentOwnerId, Long newOwnerId);

    List<GroupRankDto> getGroupRanking(Long groupId);

    List<String> getHashtagList(String keyword);

    List<GroupDto> getMyGroups(Long userId);

    List<GroupDto> getList(String keyword, int page, int size, Long userId); // 페이징 추가

    void inviteMember(Long groupId, Long ownerId, Long targetUserId); // 초대하기 추가

    List<MemberDto> getGroupMembers(Long groupId);

    // 초대할 유저 검색
    List<MemberDto> searchUsers(String nickname, Long groupId);
}