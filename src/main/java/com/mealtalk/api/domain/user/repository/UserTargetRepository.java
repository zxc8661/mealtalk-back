package com.mealtalk.api.domain.user.repository;

import com.mealtalk.api.domain.user.entity.TargetType;
import com.mealtalk.api.domain.user.entity.UserTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserTargetRepository extends JpaRepository<UserTarget, Long> {
    List<UserTarget> findAllByUserId(Long userId);

    Optional<UserTarget> findByUserIdAndTargetType(Long userId, TargetType targetType);
}
