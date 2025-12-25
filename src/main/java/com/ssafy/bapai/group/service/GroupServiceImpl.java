package com.ssafy.bapai.group.service;

import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.group.dao.GroupDao;
import com.ssafy.bapai.group.dto.GroupDto;
import com.ssafy.bapai.group.dto.GroupRankDto;
import com.ssafy.bapai.member.dto.MemberDto;
import java.util.Arrays;
import java.util.Collections;
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
    public PageResponse<GroupDto> getList(String keyword, int page, int size, Long userId) {
        Map<String, Object> params = new HashMap<>();

        int offset = (page - 1) * size;
        params.put("limit", size);
        params.put("offset", offset);

        List<String> keywordList = null;
        if (keyword != null && !keyword.trim().isEmpty()) {
            keywordList = Arrays.asList(keyword.split("[\\s,]+"));
            params.put("keywordList", keywordList);
            // ★ 추가: XML에서 발생하던 리플렉션 오류를 방지하기 위해 크기를 직접 전달
            params.put("keywordListSize", keywordList.size());
        }

        // 1. 목록 조회
        List<GroupDto> list = groupDao.selectGroupList(params);

        for (GroupDto g : list) {
            g.setTags(groupDao.selectGroupTags(g.getGroupId()));
            if (userId != null) {
                String role = groupDao.selectMyRole(g.getGroupId(), userId);
                g.setRole(role == null ? "NONE" : role);
            } else {
                g.setRole("NONE");
            }
        }

        // 2. 전체 개수 조회
        // ★ 수정: 파라미터 맵(params)을 통째로 넘겨서 키워드와 크기 정보를 모두 전달
        long totalElements = groupDao.countAllGroups(params);

        // 3. PageResponse 생성
        return new PageResponse<>(list, page, size, totalElements);
    }
//    @Override
//    public List<GroupDto> getList(String keyword, int page, int size, Long userId) {
//        Map<String, Object> params = new HashMap<>();
//
//        // 페이지네이션 계산
//        int offset = (page - 1) * size;
//        params.put("limit", size);
//        params.put("offset", offset);
//
//        if (keyword != null && !keyword.trim().isEmpty()) {
//            params.put("keywordList", Arrays.asList(keyword.split("[\\s,]+")));
//        }
//
//        List<GroupDto> list = groupDao.selectGroupList(params);
//
//        for (GroupDto g : list) {
//            g.setTags(groupDao.selectGroupTags(g.getGroupId()));
//            if (userId != null) {
//                // 가입 여부 체크
//                g.setJoined(groupDao.checkJoined(g.getGroupId(), userId) > 0);
//                // 방장 여부 체크: ownerId와 로그인한 userId 비교
//                g.setOwner(g.getOwnerId().equals(userId));
//            }
//        }
//        return list;
//    }


    @Override
    public GroupDto getDetail(Long groupId, Long userId) {
        GroupDto group = groupDao.selectGroupDetail(groupId);
//        if (group != null) {
//            group.setTags(groupDao.selectGroupTags(groupId));
//            if (userId != null) {
//                group.setJoined(groupDao.checkJoined(groupId, userId) > 0);
//                group.setOwner(group.getOwnerId().equals(userId));
//            }
//        }

        if (group != null) {
            group.setTags(groupDao.selectGroupTags(groupId));
            if (userId != null) {
                String role = groupDao.selectMyRole(groupId, userId);
                if (role == null) {
                    role = "NONE";
                }
                group.setRole(role);
            } else {
                group.setRole("NONE");
            }
        }
        return group;
    }

//    @Override
//    @Transactional
//    public void joinGroup(Long groupId, Long userId) {
//        GroupDto group = groupDao.selectGroupDetail(groupId);
//        if ("PRIVATE".equals(group.getType())) {
//            // 비공개일 경우 가입 신청 테이블(별도 필요)에 넣거나 로직 처리
//            // 여기서는 비공개 시 즉시 가입을 막는 예시만 작성
//            throw new IllegalStateException("비공개 모임은 관리자(그룹장)의 승인이 필요합니다.");
//        }
//        if (groupDao.checkJoined(groupId, userId) > 0) {
//            throw new IllegalStateException("이미 가입했습니다.");
//        }
//
//        if (groupDao.countMembers(groupId) >= group.getMaxMember()) {
//            throw new IllegalStateException("정원 초과입니다.");
//        }
//        groupDao.insertGroupMember(groupId, userId, "MEMBER");
//    }

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
        // 1. 현재방장 권한 확인
        if (!"LEADER".equals(groupDao.selectMyRole(groupId, currentOwnerId))) {
            throw new IllegalStateException("권한 없음");
        }
        // 2. newOwner의 그룹내 역할 가져오기
        String toRole = groupDao.selectMyRole(groupId, newOwnerId);

        // 3. 반드시 member만 가능하게 제한
        if (!"MEMBER".equals(toRole)) {
            throw new IllegalStateException("위임 대상은 MEMBER로 이미 가입된 사용자여야 합니다.");
        }
        // 4. 기존 owner는 MEMBER로, newOwner는 LEADER로
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

    //    @Override
//    public List<GroupDto> getMyGroups(Long userId) {
//        List<GroupDto> list = groupDao.selectMyGroups(userId);
//        for (GroupDto g : list) {
//            g.setTags(groupDao.selectGroupTags(g.getGroupId()));
//            // 내 그룹 목록이므로 가입은 무조건 true, 방장 여부만 계산
//            g.setJoined(true);
//            g.setOwner(g.getOwnerId().equals(userId));
//        }
//        return list;
//    }
    @Override
    public PageResponse<GroupDto> getMyGroups(Long userId, int page, int size) {
        // 1. 페이지네이션 계산
        int offset = (page - 1) * size;

        // 2. 목록 조회 (limit/offset 적용)
        List<GroupDto> list = groupDao.selectMyGroupsPaged(userId, size, offset);
//
//        for (GroupDto g : list) {
//            g.setTags(groupDao.selectGroupTags(g.getGroupId()));
//            g.setJoined(true);
//            g.setOwner(g.getOwnerId().equals(userId));
//        }
        for (GroupDto g : list) {
            g.setTags(groupDao.selectGroupTags(g.getGroupId()));

            String role = groupDao.selectMyRole(g.getGroupId(), userId);
            if (role == null) {
                role = "NONE";
            }
            g.setRole(role);
        }

        // 3. 전체 개수 조회 (카운트 쿼리)
        long totalElements = groupDao.countMyGroups(userId);

        // 4. PageResponse 생성(자동 계산)
        return new PageResponse<>(list, page, size, totalElements);
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

    @Override
    public List<MemberDto> getGroupMembers(Long groupId) {
        // GroupDao에서도 List<MemberDto>를 반환해야 함
        return groupDao.selectGroupMembers(groupId);
    }

    @Override
    public List<MemberDto> searchUsers(String nickname, Long groupId) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return Collections.emptyList();
        }
        // GroupDao에서도 List<MemberDto>를 반환해야 함
        return groupDao.searchUsersByNickname(nickname, groupId);
    }

//    @Override
//    public void updateGroup(GroupDto groupDto) {
//
//        try {
//            groupDao.updateGroup(groupDto);
//        } catch (BadSqlGrammarException e) {
//            // 값이 전부 null이라면 SET절 없는 오류를 무시하고
//        }
//
//    }

    @Override
    public void updateGroup(GroupDto groupDto) {
        boolean hasMainFields =
                groupDto.getName() != null ||
                        groupDto.getDescription() != null ||
                        groupDto.getImgUrl() != null ||
                        groupDto.getMaxMember() != null ||
                        (groupDto.getType() != null && !groupDto.getType().isEmpty());

        if (hasMainFields) {
            groupDao.updateGroup(groupDto);
        }
        // [태그 처리]
        if (groupDto.getTags() != null) {
            // tags가 비어있으면 "태그 전체 삭제"
            groupDao.deleteAllGroupHashtags(groupDto.getGroupId());

            // tags가 1개라도 있으면 다시 insert
            if (!groupDto.getTags().isEmpty()) {
                for (String tag : groupDto.getTags()) {
                    Long tagId = groupDao.selectTagIdByName(tag);
                    if (tagId == null) {
                        Map<String, Object> params = new HashMap<>();
                        params.put("name", tag);
                        groupDao.insertHashtag(params);
                        Object idObj = params.get("tagId");
                        if (idObj != null) {
                            tagId = Long.valueOf(String.valueOf(idObj));
                        }
                    }
                    if (tagId != null) {
                        groupDao.insertGroupHashtag(groupDto.getGroupId(), tagId);
                    }
                }
            }
            // tags == [] 일 때는 insert가 스킵되고, 전체 삭제됨 ⇒ 즉 "모든 태그 삭제"
        }

        if (!hasMainFields && groupDto.getTags() == null) {
            throw new IllegalArgumentException("수정할 값이 1개 이상 필요합니다.");
        }
    }

    @Override
    @Transactional
    public void joinGroup(Long groupId, Long userId) {
        GroupDto group = groupDao.selectGroupDetail(groupId);
        if (groupDao.checkJoined(groupId, userId) > 0) {
            throw new IllegalStateException("이미 신청/가입된 사용자입니다.");
        }
        if (groupDao.countMembers(groupId) >= group.getMaxMember()) {
            throw new IllegalStateException("정원 초과입니다.");
        }
        if ("PRIVATE".equals(group.getType())) {
            // WAIT 상태로 넣기
            groupDao.insertGroupMember(groupId, userId, "WAIT");
        } else {
            groupDao.insertGroupMember(groupId, userId, "MEMBER");
        }
    }


    /**
     * 비공개 그룹 - 가입 대기자 목록 조회 (그룹장만 접근)
     */
    @Override
    public List<MemberDto> getPendingMembers(Long groupId, Long ownerId) {
        GroupDto group = groupDao.selectGroupDetail(groupId);
        if (!group.getOwnerId().equals(ownerId)) {
            throw new IllegalStateException("권한 없음");
        }
        // group_member.role = 'WAIT'인 사용자만 조회 (MyBatis에 selectPendingMembers 구현 필요)
        return groupDao.selectPendingMembers(groupId);
    }

    /**
     * 비공개 그룹 - 가입 승인 (그룹장만)
     */
    @Override
    @Transactional
    public void approveJoin(Long groupId, Long userId, Long ownerId) {
        GroupDto group = groupDao.selectGroupDetail(groupId);
        if (!group.getOwnerId().equals(ownerId)) {
            throw new IllegalStateException("권한 없음");
        }
        // role = 'WAIT' -> 'MEMBER'
        groupDao.updateMemberRole(groupId, userId, "MEMBER");
    }

    /**
     * 비공개 그룹 - 가입 거절 (그룹장만)
     */
    @Override
    @Transactional
    public void rejectJoin(Long groupId, Long userId, Long ownerId) {
        GroupDto group = groupDao.selectGroupDetail(groupId);
        if (!group.getOwnerId().equals(ownerId)) {
            throw new IllegalStateException("권한 없음");
        }
        // 대기자 삭제
        groupDao.deleteGroupMember(groupId, userId);
    }

    @Override
    @Transactional
    public void removeGroup(Long groupId, Long userId) {

        String role = groupDao.selectMyRole(groupId, userId);

        // 수정: "OWNER" -> "LEADER"
        if (!"LEADER".equals(role)) {
            throw new RuntimeException("그룹장만 그룹을 삭제할 수 있습니다.");
        }

        // 2. 연관 데이터 삭제
        groupDao.deleteAllGroupHashtags(groupId);
        groupDao.deleteAllGroupMembers(groupId);
        groupDao.deleteGroup(groupId);
    }

    @Override
    public List<GroupRankDto> getDietRanking(Long groupId, String period) {
        Map<String, Object> params = new HashMap<>();
        params.put("groupId", groupId);
        params.put("period", period); // daily, weekly, monthly
        params.put("limit", 10); // 상위 10명

        if ("monthly".equals(period)) {
            // 현재 연도-월 추출 (예: 2025-12)
            params.put("currentMonth", java.time.LocalDate.now().toString().substring(0, 7));
        }

        List<GroupRankDto> list = groupDao.selectGroupRankingByDiet(params);

        // 순위(Rank) 자동 부여
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        return list;
    }
}