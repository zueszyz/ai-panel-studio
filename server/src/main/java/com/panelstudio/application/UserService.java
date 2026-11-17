package com.panelstudio.application;

import com.panelstudio.domain.entity.UserEntity;
import com.panelstudio.domain.support.IdPrefixes;
import com.panelstudio.domain.support.UlidGenerator;
import com.panelstudio.infra.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 匿名身份：前端生成 deviceId，服务端首次见到就建 users 行。
 * 并发下同一 deviceId 可能同时插入，靠 UNIQUE(device_id) 兜底。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final UlidGenerator idGenerator;

    public UserService(UserMapper userMapper, UlidGenerator idGenerator) {
        this.userMapper = userMapper;
        this.idGenerator = idGenerator;
    }

    /** 返回 userId；deviceId 为空则返回 null（纯匿名浏览）。 */
    public String resolveUserId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        UserEntity existing = userMapper.findByDeviceId(deviceId);
        if (existing != null) {
            return existing.getId();
        }
        UserEntity entity = new UserEntity();
        entity.setId(idGenerator.nextWithPrefix(IdPrefixes.USER));
        entity.setDeviceId(deviceId);
        entity.setDisplayName("旁听者");
        try {
            userMapper.insert(entity);
            return entity.getId();
        } catch (DuplicateKeyException ex) {
            UserEntity concurrent = userMapper.findByDeviceId(deviceId);
            return concurrent == null ? null : concurrent.getId();
        }
    }
}
