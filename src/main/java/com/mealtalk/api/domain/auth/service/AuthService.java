package com.mealtalk.api.domain.auth.service;

import com.mealtalk.api.domain.auth.dto.AuthTokenResponse;
import com.mealtalk.api.domain.auth.dto.CurrentUserResponse;
import com.mealtalk.api.domain.auth.google.GoogleTokenPayload;
import com.mealtalk.api.domain.auth.google.GoogleTokenVerifier;
import com.mealtalk.api.domain.auth.security.JwtTokenProvider;
import com.mealtalk.api.domain.user.entity.AuthProvider;
import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.domain.user.repository.UserProfileRepository;
import com.mealtalk.api.domain.user.repository.UserRepository;
import com.mealtalk.api.domain.user.repository.UserTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserTargetRepository userTargetRepository;

    @Transactional
    public AuthTokenResponse loginWithGoogle(String idToken) {
        GoogleTokenPayload payload = googleTokenVerifier.verify(idToken);
        User user = userRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, payload.subject())
            .orElseGet(() -> userRepository.save(User.create(
                AuthProvider.GOOGLE,
                payload.subject(),
                payload.email(),
                payload.name(),
                "Asia/Seoul"
            )));
        return AuthTokenResponse.bearer(jwtTokenProvider.issueAccessToken(user.getId()), user.isProfileCompleted());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        CurrentUserResponse.ProfileSummary profile = userProfileRepository.findByUserId(userId)
            .map(saved -> new CurrentUserResponse.ProfileSummary(
                saved.getHeightCm(),
                saved.getWeightKg(),
                saved.getActivityLevel(),
                saved.getGoalMode()
            ))
            .orElse(null);
        List<CurrentUserResponse.TargetSummary> targets = userTargetRepository.findAllByUserId(userId).stream()
            .map(saved -> new CurrentUserResponse.TargetSummary(
                saved.getTargetType(),
                saved.getTargetValue(),
                saved.getDueDate()
            ))
            .toList();
        return new CurrentUserResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.isProfileCompleted(),
            user.getTimezone(),
            profile,
            targets
        );
    }
}
