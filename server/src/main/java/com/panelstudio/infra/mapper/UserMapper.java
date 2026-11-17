package com.panelstudio.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panelstudio.domain.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM users WHERE device_id = #{deviceId}")
    UserEntity findByDeviceId(@Param("deviceId") String deviceId);
}
