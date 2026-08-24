package com.mealtalk.api.domain.user.repository;

import com.mealtalk.api.domain.user.entity.AuthProvider;
import com.mealtalk.api.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
