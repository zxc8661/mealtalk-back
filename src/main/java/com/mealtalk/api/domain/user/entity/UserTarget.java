package com.mealtalk.api.domain.user.entity;

import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Entity @Table(name = "user_targets", uniqueConstraints = @UniqueConstraint(name = "uk_user_targets_type", columnNames = {"user_id", "target_type"})) @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTarget extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 30) private TargetType targetType;
    @Column(name = "target_value", nullable = false, precision = 10, scale = 2) private BigDecimal targetValue;
    @Column(name = "due_date") private LocalDate dueDate;

    public static UserTarget create(User user, TargetType targetType, BigDecimal targetValue, LocalDate dueDate) {
        UserTarget target = new UserTarget();
        target.user = user;
        target.targetType = targetType;
        target.targetValue = targetValue;
        target.dueDate = dueDate;
        return target;
    }
}
