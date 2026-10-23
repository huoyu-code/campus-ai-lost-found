package com.campus.lostfound.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class SysUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private String phone;

    private String email;

    private UserRole role;

    private UserStatus status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

