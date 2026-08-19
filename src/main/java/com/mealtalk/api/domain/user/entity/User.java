package com.mealtalk.api.domain.user.entity;

import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_provider_subject", columnNames = {"provider", "provider_user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private AuthProvider provider;
    @Column(name = "provider_user_id", nullable = false, length = 255) private String providerUserId;
    @Column(nullable = false, length = 320) private String email;
    @Column(length = 100) private String name;
    @Column(name = "profile_completed", nullable = false) private boolean profileCompleted;
    @Column(nullable = false, length = 50) private String timezone = "Asia/Seoul";
}
