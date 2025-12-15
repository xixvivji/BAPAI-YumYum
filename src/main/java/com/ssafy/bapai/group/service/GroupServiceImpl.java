package com.ssafy.bapai.group.service;

import com.ssafy.bapai.group.dao.GroupDao;
import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {
    private final GroupDao groupDao;

    @Override
    @Transactional
    public void createGroup(GroupDto groupDto) {
        // 1. 모임 생성 (groups 테이블)
        groupDao.insertGroup(groupDto);

        // 2. 방장을 멤버로 추가 (group_member 테이블, role='LEADER')
        groupDao.insertGroupMember(groupDto.getGroupId(), groupDto.getOwnerId(), "LEADER");

        // 3. 해시태그 저장 (group_hashtags 테이블)
        if (groupDto.getTags() != null && !groupDto.getTags().isEmpty()) {
            for (String tagName : groupDto.getTags()) {
                // 태그 이름으로 ID 조회 (없으면 null 반환한다고 가정)
                Long tagId = groupDao.selectTagIdByName(tagName);
                if (tagId != null) {
                    groupDao.insertGroupHashtag(groupDto.getGroupId(), tagId);
                }
            }
        }
    }

    @Override
    public List<GroupDto> getList(String keyword, Long userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        List<GroupDto> list = groupDao.selectGroupList(params);

        // 태그 및 멤버 정보 채우기
        for (GroupDto g : list) {
            g.setTags(groupDao.selectGroupTags(g.getGroupId())); // 태그 목록 조회

            if (userId != null) {
                String myRole = groupDao.selectMyRole(g.getGroupId(), userId);
                if (myRole != null) {
                    g.setJoined(true);
                    g.setMyRole(myRole);
                }
            }
        }
        return list;
    }

    @Override
    public GroupDto getDetail(Long groupId, Long userId) {
        GroupDto group = groupDao.selectGroupDetail(groupId);
        // 태그 목록 조회
        group.setTags(groupDao.selectGroupTags(groupId));

        if (userId != null) {
            String myRole = groupDao.selectMyRole(groupId, userId);
            if (myRole != null) {
                group.setJoined(true);
                group.setMyRole(myRole);
            }
        }
        return group;
    }

    @Override
    @Transactional
    public void joinGroup(Long groupId, Long userId) {
        if (groupDao.checkJoined(groupId, userId) > 0) {
            throw new IllegalStateException("이미 가입했습니다.");
        }
        GroupDto group = groupDao.selectGroupDetail(groupId);
        if (groupDao.countMembers(groupId) >= group.getMaxCount()) {
            throw new IllegalStateException("정원 초과입니다.");
        }
        // role = 'MEMBER'
        groupDao.insertGroupMember(groupId, userId, "MEMBER");
    }

    @Override
    public void leaveGroup(Long groupId, Long userId) {
        String myRole = groupDao.selectMyRole(groupId, userId);
        // 방장(LEADER)은 탈퇴 불가
        if ("LEADER".equals(myRole) && groupDao.countMembers(groupId) > 1) {
            throw new IllegalStateException("방장은 탈퇴 불가. 위임하거나 모임을 삭제하세요.");
        }
        groupDao.deleteGroupMember(groupId, userId);
    }

    @Override
    @Transactional
    public void kickMember(Long groupId, Long ownerId, Long targetUserId) {
        // 권한 체크: 요청자가 LEADER인지 확인
        if (!"LEADER".equals(groupDao.selectMyRole(groupId, ownerId))) {
            throw new IllegalStateException("권한 없음");
        }
        groupDao.deleteGroupMember(groupId, targetUserId);
    }

    @Override
    @Transactional
    public void delegateOwner(Long groupId, Long currentOwnerId, Long newOwnerId) {
        if (!"LEADER".equals(groupDao.selectMyRole(groupId, currentOwnerId))) {
            throw new IllegalStateException("권한 없음");
        }
        // 역할 변경
        groupDao.updateMemberRole(groupId, currentOwnerId, "MEMBER");
        groupDao.updateMemberRole(groupId, newOwnerId, "LEADER");

        // groups 테이블 owner_id 업데이트
        groupDao.updateGroupOwner(groupId, newOwnerId);
    }

    @Override
    public List<GroupRankDto> getGroupRanking(Long groupId) {
        List<GroupRankDto> list = groupDao.selectGroupRanking(groupId, 5);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        return list;
    }
}