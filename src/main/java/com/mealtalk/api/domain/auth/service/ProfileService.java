package com.mealtalk.api.domain.auth.service;

import com.mealtalk.api.domain.auth.dto.CurrentUserResponse;
import com.mealtalk.api.domain.auth.dto.ProfileUpdateRequest;
import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.domain.user.entity.UserProfile;
import com.mealtalk.api.domain.user.entity.UserTarget;
import com.mealtalk.api.domain.user.repository.UserProfileRepository;
import com.mealtalk.api.domain.user.repository.UserRepository;
import com.mealtalk.api.domain.user.repository.UserTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserTargetRepository userTargetRepository;

    @Transactional
    public CurrentUserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        UserProfile profile = userProfileRepository.findByUserId(userId)
            .orElseGet(() -> UserProfile.create(
                user,
                request.heightCm(),
                request.weightKg(),
                request.activityLevel(),
                request.goalMode()
            ));
        profile.update(request.heightCm(), request.weightKg(), request.activityLevel(), request.goalMode());
        userProfileRepository.save(profile);

        for (ProfileUpdateRequest.TargetRequest targetRequest : request.targets()) {
            UserTarget target = userTargetRepository.findByUserIdAndTargetType(userId, targetRequest.targetType())
                .orElseGet(() -> UserTarget.create(
                    user,
                    targetRequest.targetType(),
                    targetRequest.targetValue(),
                    targetRequest.dueDate()
                ));
            target.update(targetRequest.targetValue(), targetRequest.dueDate());
            userTargetRepository.save(target);
        }

        user.completeProfile();
        return authService.currentUser(userId);
    }
}
