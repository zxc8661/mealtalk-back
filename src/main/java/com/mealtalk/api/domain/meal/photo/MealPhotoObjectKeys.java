package com.mealtalk.api.domain.meal.photo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Builds the opaque bucket keys under which meal photos are stored.
 *
 * <p>Shape: {@code meals/{userHash}/{mealId}/{uuid}.jpg}.
 *
 * <p>Two properties matter. The user segment is a keyed hash rather than the raw
 * id, so enumerating one user's prefix from a leaked key tells an attacker
 * nothing about anyone else. The filename is a fresh random UUID on every write,
 * so a key is never derivable from public data: knowing a meal id is not enough.
 * The meal id stays in clear text because it is already scoped behind the hash
 * and makes operational cleanup of a single meal possible.
 *
 * <p>The salt is generated once per process. Keys are persisted alongside the
 * row that owns them, so nothing depends on the salt being stable across
 * restarts - it only has to be unpredictable.
 */
public final class MealPhotoObjectKeys {
    private static final byte[] USER_SALT = newSalt();

    private MealPhotoObjectKeys() {
    }

    public static String generate(long userId, long mealId) {
        return "meals/" + userSegment(userId) + "/" + mealId + "/" + UUID.randomUUID() + ".jpg";
    }

    private static String userSegment(long userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(USER_SALT);
            digest.update(Long.toString(userId).getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            byte[] truncated = new byte[12];
            System.arraycopy(hash, 0, truncated, 0, truncated.length);
            return HexFormat.of().formatHex(truncated);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every JVM", exception);
        }
    }

    private static byte[] newSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
}
