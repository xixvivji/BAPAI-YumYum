package com.ssafy.bapai.common.redis;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
    // save, findById, deleteById 등 기본 메서드 자동 제공됨
}