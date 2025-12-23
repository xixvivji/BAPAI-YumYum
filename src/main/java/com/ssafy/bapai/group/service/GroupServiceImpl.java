package com.ssafy.bapai.group.service;

import com.ssafy.bapai.group.dao.GroupDao;
import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import java.util.Arrays;
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
    public List<GroupDto> getList(String keyword, int page, int size, Long userId) {
        Map<String, Object> params = new HashMap<>();

        // 페이지네이션 계산
        int offset = (page - 1) * size;
        params.put("limit", size);
        params.put("offset", offset);

        if (keyword != null && !keyword.trim().isEmpty()) {
            params.put("keywordList", Arrays.asList(keyword.split("[\\s,]+")));
        }

        List<GroupDto> list = groupDao.selectGroupList(params);

        for (GroupDto g : list) {
            g.setTags(groupDao.selectGroupTags(g.getGroupId()));
            if (userId != null) {
                // 가입 여부 체크
                g.setJoined(groupDao.checkJoined(g.getGroupId(), userId) > 0);
                // 방장 여부 체크: ownerId와 로그인한 userId 비교
                g.setOwner(g.getOwnerId().equals(userId));
            }
        }
        return list;
    }


    @Override
    public GroupDto getDetail(Long groupId, Long userId) {
        GroupDto group = groupDao.selectGroupDetail(groupId);
        if (group != null) {
            group.setTags(groupDao.selectGroupTags(groupId));
            if (userId != null) {
                group.setJoined(groupDao.checkJoined(groupId, userId) > 0);
                group.setOwner(group.getOwnerId().equals(userId));
            }
        }
        return group;
    }

    @Override
    @Transactional
    public void joinGroup(Long groupId, Long userId) {
        GroupDto group = groupDao.selectGroupDetail(groupId);
        if ("PRIVATE".equals(group.getType())) {
            // 비공개일 경우 가입 신청 테이블(별도 필요)에 넣거나 로직 처리
            // 여기서는 비공개 시 즉시 가입을 막는 예시만 작성
            throw new IllegalStateException("비공개 모임은 관리자(그룹장)의 승인이 필요합니다.");
        }
        if (groupDao.checkJoined(groupId, userId) > 0) {
            throw new IllegalStateException("이미 가입했습니다.");
        }

        if (groupDao.countMembers(groupId) >= group.getMaxMember()) {
            throw new IllegalStateException("정원 초과입니다.");
        }
        groupDao.insertGroupMember(groupId, userId, "MEMBER");
    }

    @Override
    public void leaveGroup(Long groupId, Long userId) {
        // 내부 로직에서는 여전히 DB의 role(LEADER)을 체크하여 그룹장 탈퇴를 막음
        String role = groupDao.selectMyRole(groupId, userId);
        if ("LEADER".equals(role)) {
            throw new IllegalStateException("그룹장은 탈퇴할 수 없습니다. 권한을 위임하세요.");
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
        List<GroupDto> list = groupDao.selectMyGroups(userId);
        for (GroupDto g : list) {
            g.setTags(groupDao.selectGroupTags(g.getGroupId()));
            // 내 그룹 목록이므로 가입은 무조건 true, 방장 여부만 계산
            g.setJoined(true);
            g.setOwner(g.getOwnerId().equals(userId));
        }
        return list;
    }

    @Override
    @Transactional
    public void inviteMember(Long groupId, Long ownerId, Long targetUserId) {
        // 방장만 초대 가능 여부 확인
        GroupDto group = groupDao.selectGroupDetail(groupId);
        if (!group.getOwnerId().equals(ownerId)) {
            throw new IllegalStateException("초대 권한이 없습니다.");
        }
        // 이미 가입했는지 확인
        if (groupDao.checkJoined(groupId, targetUserId) > 0) {
            throw new IllegalStateException("이미 가입된 사용자입니다.");
        }
        groupDao.insertGroupMember(groupId, targetUserId, "MEMBER");
    }
}