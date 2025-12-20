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
        // 1. 모임 생성
        groupDao.insertGroup(groupDto);

        // 2. 방장 추가
        groupDao.insertGroupMember(groupDto.getGroupId(), groupDto.getOwnerId(), "LEADER");

        // 3. 해시태그 저장 (수정됨: 없으면 생성 로직 추가)
        if (groupDto.getTags() != null && !groupDto.getTags().isEmpty()) {
            for (String tagName : groupDto.getTags()) {
                // 3-1. 기존 태그 ID 조회
                Long tagId = groupDao.selectTagIdByName(tagName);

                // 3-2. 없으면(null) 새로 생성
                if (tagId == null) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("name", tagName); // 매퍼의 #{name}과 일치

                    groupDao.insertHashtag(params); // DB에 저장 (useGeneratedKeys로 ID 생성됨)

                    // 생성된 ID 꺼내기 (타입 캐스팅 안전하게 처리)
                    Object idObj = params.get("tagId");
                    if (idObj != null) {
                        tagId = Long.valueOf(String.valueOf(idObj));
                    }
                }

                // 3-3. 태그 연결 (이제 tagId가 있으므로 저장 가능)
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

        for (GroupDto g : list) {
            g.setTags(groupDao.selectGroupTags(g.getGroupId()));

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
        if (groupDao.countMembers(groupId) >= group.getMaxMember()) {
            throw new IllegalStateException("정원 초과입니다.");
        }
        groupDao.insertGroupMember(groupId, userId, "MEMBER");
    }

    @Override
    public void leaveGroup(Long groupId, Long userId) {
        String myRole = groupDao.selectMyRole(groupId, userId);
        if ("LEADER".equals(myRole) && groupDao.countMembers(groupId) > 1) {
            throw new IllegalStateException("방장은 탈퇴 불가. 위임하거나 모임을 삭제하세요.");
        }
        groupDao.deleteGroupMember(groupId, userId);
    }

    @Override
    @Transactional
    public void kickMember(Long groupId, Long ownerId, Long targetUserId) {
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
        groupDao.updateMemberRole(groupId, currentOwnerId, "MEMBER");
        groupDao.updateMemberRole(groupId, newOwnerId, "LEADER");
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

    @Override
    public List<String> getHashtagList(String keyword) {
        return groupDao.selectHashtagList(keyword);
    }

    @Override
    public List<GroupDto> getMyGroups(Long userId) {
        return groupDao.selectMyGroups(userId);
    }
}