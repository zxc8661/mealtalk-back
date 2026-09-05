package com.mealtalk.api.domain.meal.entity;

import com.mealtalk.api.domain.user.entity.User;
import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The record a client-supplied request id already created.
 *
 * <p>A save button pressed twice, or a request retried after a dropped
 * connection, must not leave the user with two identical journal entries. The
 * client sends one UUID per draft; this row remembers which meal that UUID
 * produced so the retry can replay it.
 *
 * <p>{@code fingerprint} guards against the id being reused for a different
 * draft: the same id with different content is a client bug, and answering it
 * with the earlier unrelated record would be worse than refusing.
 */
@Getter
@Entity
@Table(
    name = "meal_write_requests",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_meal_write_requests_user_request",
        columnNames = {"user_id", "client_request_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealWriteRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "client_request_id", nullable = false, length = 36)
    private String clientRequestId;

    /** Hex SHA-256 over the normalized payload this id was first used with. */
    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    public static MealWriteRequest create(User user, String clientRequestId, String fingerprint, Meal meal) {
        MealWriteRequest request = new MealWriteRequest();
        request.user = user;
        request.clientRequestId = clientRequestId;
        request.fingerprint = fingerprint;
        request.meal = meal;
        return request;
    }
}
