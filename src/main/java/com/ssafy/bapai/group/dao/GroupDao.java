package com.ssafy.bapai.group.dao;

import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupDao {
    // 1. 모임 CRUD
    void insertGroup(GroupDto groupDto);

    List<GroupDto> selectGroupList(Map<String, Object> params);

    GroupDto selectGroupDetail(Long groupId);

    void updateGroupOwner(@Param("groupId") Long groupId, @Param("newOwnerId") Long newOwnerId);

    // 2. 멤버 관리
    void insertGroupMember(@Param("groupId") Long groupId, @Param("userId") Long userId,
                           @Param("role") String role);

    void deleteGroupMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    // 멤버 유틸
    int checkJoined(@Param("groupId") Long groupId, @Param("userId") Long userId);

    int countMembers(Long groupId);

    String selectMyRole(@Param("groupId") Long groupId, @Param("userId") Long userId);

    void updateMemberRole(@Param("groupId") Long groupId, @Param("userId") Long userId,
                          @Param("role") String role);

    // 3.  해시태그 관리
    void insertGroupHashtag(@Param("groupId") Long groupId, @Param("tagId") Long tagId);

    // 특정 모임의 태그 이름 목록 조회
    List<String> selectGroupTags(Long groupId);

    // 태그 이름으로 tag_id 찾기 (hashtags 테이블 조회)
    Long selectTagIdByName(String tagName);

    // 4. 랭킹
    List<GroupRankDto> selectGroupRanking(@Param("groupId") Long groupId,
                                          @Param("limit") int limit);
}