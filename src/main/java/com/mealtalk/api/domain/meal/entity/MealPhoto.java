package com.mealtalk.api.domain.meal.entity;

import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The single current photo of a meal.
 *
 * <p>The owner is reached through {@link Meal#getUser()}, so no user id is
 * duplicated here. {@code objectKey} identifies the object inside the private
 * bucket and must never leave the server: clients read the bytes through an
 * authenticated endpoint instead.
 */
@Getter
@Entity
@Table(name = "meal_photos", uniqueConstraints = @UniqueConstraint(name = "uk_meal_photos_meal", columnNames = "meal_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPhoto extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id", nullable = false, unique = true)
    private Meal meal;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    /** Lowercase hex SHA-256 of the stored bytes, 64 characters. */
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    public static MealPhoto create(
        Meal meal,
        String objectKey,
        String contentType,
        long byteSize,
        int width,
        int height,
        String checksumSha256
    ) {
        MealPhoto photo = new MealPhoto();
        photo.meal = meal;
        photo.objectKey = objectKey;
        photo.contentType = contentType;
        photo.byteSize = byteSize;
        photo.width = width;
        photo.height = height;
        photo.checksumSha256 = checksumSha256;
        return photo;
    }

    /** Replaces the stored object in place, keeping the meal's single photo row. */
    public void replaceWith(
        String objectKey,
        String contentType,
        long byteSize,
        int width,
        int height,
        String checksumSha256
    ) {
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
        this.checksumSha256 = checksumSha256;
    }
}
