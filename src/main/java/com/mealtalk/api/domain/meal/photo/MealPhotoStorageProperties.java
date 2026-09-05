package com.mealtalk.api.domain.meal.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare R2 connection settings, supplied only through backend environment
 * variables ({@code R2_ENDPOINT}, {@code R2_ACCESS_KEY_ID},
 * {@code R2_SECRET_ACCESS_KEY}, {@code R2_BUCKET}, optional {@code R2_REGION}).
 *
 * <p>Every value is optional on purpose: the application must boot with the
 * bucket not yet created. {@link #isConfigured()} is what decides whether the
 * real client is wired, so pasting the values into the ignored {@code .env}
 * file is the only step needed to switch storage on.
 *
 * <p>None of these values may ever reach the client or an API response.
 */
@ConfigurationProperties(prefix = "mealtalk.meal-photo-storage")
public record MealPhotoStorageProperties(
    String endpoint,
    String accessKeyId,
    String secretAccessKey,
    String bucket,
    String region
) {
    /** R2 ignores the region but the S3 client requires one; "auto" is R2's own default. */
    private static final String DEFAULT_REGION = "auto";

    public MealPhotoStorageProperties {
        region = hasText(region) ? region.trim() : DEFAULT_REGION;
    }

    /** True only when the S3 client has everything it needs to talk to the bucket. */
    public boolean isConfigured() {
        return hasText(endpoint) && hasText(accessKeyId) && hasText(secretAccessKey) && hasText(bucket);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
