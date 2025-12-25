package com.ssafy.bapai.group.service;

import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import com.ssafy.bapai.member.dto.MemberDto;
import java.util.List;

public interface GroupService {
    // 팀(Team) -> 모임(Group) 용어 변경
    void createGroup(GroupDto groupDto);

    PageResponse<GroupDto> getList(String keyword, int page, int size, Long userId);

    PageResponse<GroupDto> getMyGroups(Long userId, int page, int size);

    GroupDto getDetail(Long groupId, Long userId);

    void joinGroup(Long groupId, Long userId);

    void leaveGroup(Long groupId, Long userId);

    void kickMember(Long groupId, Long ownerId, Long targetUserId);

    // leaderId -> ownerId, teamId -> groupId
    void delegateOwner(Long groupId, Long currentOwnerId, Long newOwnerId);

    List<GroupRankDto> getGroupRanking(Long groupId);

    List<String> getHashtagList(String keyword);

//    List<GroupDto> getMyGroups(Long userId);

//    List<GroupDto> getList(String keyword, int page, int size, Long userId); // 페이징 추가

    void inviteMember(Long groupId, Long ownerId, Long targetUserId); // 초대하기 추가

    List<MemberDto> getGroupMembers(Long groupId);

    void updateGroup(GroupDto groupDto);

    // 초대할 유저 검색
    List<MemberDto> searchUsers(String nickname, Long groupId);


    /**
     * 비공개 그룹 - 가입 대기자 목록 조회 (그룹장만 접근)
     */
    List<MemberDto> getPendingMembers(Long groupId, Long ownerId);

    /**
     * 비공개 그룹 - 가입 승인 (그룹장만)
     */
    void approveJoin(Long groupId, Long userId, Long ownerId);

    /**
     * 비공개 그룹 - 가입 거절 (그룹장만)
     */
    void rejectJoin(Long groupId, Long userId, Long ownerId);

    void removeGroup(Long groupId, Long userId);

}