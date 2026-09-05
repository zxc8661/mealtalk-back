package com.mealtalk.api.domain;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V4 turns {@code meals.source_text} into the user-authored memo and adds the
 * one-photo-per-meal table. Historic rows were written by the nutrition-item
 * flow with a NULL {@code source_text}, so the migration must give each of them
 * a stable nonblank legacy memo without touching {@code foods}/{@code meal_items}.
 */
class MealPhotoJournalMigrationTests {
    @Test
    void productionAndH2MigrationBodiesStayInSync() throws Exception {
        assertEquals(
            migrationResource("/db/migration/V4__add_meal_photo_and_memo.sql"),
            migrationResource("/db/migration-h2/V4__add_meal_photo_and_memo.sql")
        );
    }

    @Test
    void freshDatabaseMigratesThroughV4AndCreatesTheOnePhotoPerMealTable() throws Exception {
        String url = databaseUrl();

        MigrateResult result = flyway(url).migrate();

        assertEquals(4, result.migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            for (String column : List.of(
                "id", "meal_id", "object_key", "content_type", "byte_size",
                "width", "height", "checksum_sha256", "created_at", "updated_at"
            )) {
                assertEquals(1, count(connection, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_name = 'meal_photos' AND column_name = '%s'
                    """.formatted(column)), "meal_photos is missing column " + column);
            }
            assertEquals(0, count(connection, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'meal_photos' AND column_name = 'user_id'
                """), "meal_photos must derive its owner through meals, not duplicate user_id");

            long userId = insertUser(connection, "fresh-photo-owner");
            execute(connection, mealInsert(500, userId, "'첫 기록'"));
            execute(connection, photoInsert(600, 500, "meals/1/first.jpg"));
            assertThrows(SQLException.class, () -> execute(connection, photoInsert(601, 500, "meals/1/second.jpg")),
                "meal_id must be unique so a meal has at most one current photo");

            execute(connection, "DELETE FROM meals WHERE id = 500");
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM meal_photos WHERE id = 600"),
                "deleting a meal must cascade to its photo row");
        }
    }

    @Test
    void upgradeBackfillsLegacyItemOnlyMealsWithoutTouchingFoodOrItemRows() throws Exception {
        String url = databaseUrl();
        assertEquals(3, v3Flyway(url).migrate().migrationsExecuted);

        List<String> itemsBefore;
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            seedLegacyRows(connection);
            itemsBefore = snapshotMealItems(connection);
            assertEquals(4, itemsBefore.size());
        }

        MigrateResult v4 = flyway(url).migrate();
        assertEquals(1, v4.migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(itemsBefore, snapshotMealItems(connection),
                "V4 must not delete or alter any meal_items row");
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM foods"),
                "V4 must not delete any foods row");

            assertEquals("현미밥, 된장국", memoOf(connection, 700));
            assertEquals("사용자가 직접 쓴 메모", memoOf(connection, 701));
            assertEquals("기록 내용 없음", memoOf(connection, 702));
            assertEquals("계란", memoOf(connection, 703));

            try (ResultSet blank = connection.createStatement().executeQuery(
                "SELECT id FROM meals WHERE source_text IS NULL OR TRIM(source_text) = ''"
            )) {
                assertFalse(blank.next(), "every pre-existing meal must have a nonblank memo after V4");
            }

            for (long mealId : List.of(700L, 701L, 702L, 703L)) {
                String memo = memoOf(connection, mealId);
                assertNotNull(memo);
                assertTrue(memo.length() <= 1000, "legacy memo must stay within the 1000 character cap");
            }
        }

        MigrateResult rerun = flyway(url).migrate();
        assertEquals(0, rerun.migrationsExecuted, "V4 must not re-run on an already migrated database");
    }

    @Test
    void longLegacyItemListIsCappedAtOneThousandCharacters() throws Exception {
        String url = databaseUrl();
        assertEquals(3, v3Flyway(url).migrate().migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            long userId = insertUser(connection, "long-legacy-owner");
            execute(connection, foodInsert(800, userId, "가".repeat(200)));
            execute(connection, mealInsert(900, userId, "NULL"));
            for (int index = 0; index < 12; index++) {
                execute(connection, mealItemInsert(1000 + index, 900, 800, "가".repeat(200)));
            }
        }

        assertEquals(1, flyway(url).migrate().migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            String memo = memoOf(connection, 900);
            assertNotNull(memo);
            assertFalse(memo.isBlank());
            assertTrue(memo.length() <= 1000, "legacy memo length was " + memo.length());
        }
    }

    private static void seedLegacyRows(Connection connection) throws SQLException {
        long userId = insertUser(connection, "legacy-journal-owner");
        execute(connection, foodInsert(400, userId, "현미밥"));
        execute(connection, foodInsert(401, userId, "된장국"));

        // 700: item-only historic record, the case the backfill exists for.
        execute(connection, mealInsert(700, userId, "NULL"));
        execute(connection, mealItemInsert(500, 700, 400, "현미밥"));
        execute(connection, mealItemInsert(501, 700, 401, "된장국"));
        // 701: already has user text and must be left exactly as it is.
        execute(connection, mealInsert(701, userId, "'사용자가 직접 쓴 메모'"));
        execute(connection, mealItemInsert(502, 701, 400, "현미밥"));
        // 702: no items at all, gets the fixed Korean placeholder.
        execute(connection, mealInsert(702, userId, "NULL"));
        // 703: blank-but-not-null text is treated as absent.
        execute(connection, mealInsert(703, userId, "'   '"));
        execute(connection, mealItemInsert(503, 703, 400, "계란"));
    }

    private static List<String> snapshotMealItems(Connection connection) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (ResultSet result = connection.createStatement().executeQuery("""
            SELECT id, meal_id, food_id, food_name, amount, unit, calories_kcal,
                   carbohydrates_g, protein_g, fat_g
            FROM meal_items ORDER BY id
            """)) {
            while (result.next()) {
                rows.add(String.join("|",
                    result.getString("id"), result.getString("meal_id"), result.getString("food_id"),
                    result.getString("food_name"), result.getBigDecimal("amount").toPlainString(),
                    result.getString("unit"), result.getBigDecimal("calories_kcal").toPlainString(),
                    result.getBigDecimal("carbohydrates_g").toPlainString(),
                    result.getBigDecimal("protein_g").toPlainString(),
                    result.getBigDecimal("fat_g").toPlainString()));
            }
        }
        return rows;
    }

    private static String memoOf(Connection connection, long mealId) throws SQLException {
        try (ResultSet row = connection.createStatement().executeQuery(
            "SELECT source_text FROM meals WHERE id = " + mealId
        )) {
            assertTrue(row.next(), "meal " + mealId + " disappeared");
            return row.getString("source_text");
        }
    }

    /**
     * Pinned to V4: this suite is about what V4 itself does, so a later migration
     * appearing in the folder must not change its migration count or its schema.
     */
    private static Flyway flyway(String url) {
        return Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration-h2")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .target(MigrationVersion.fromVersion("4"))
            .load();
    }

    private static Flyway v3Flyway(String url) {
        return Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration-h2")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .target(MigrationVersion.fromVersion("3"))
            .load();
    }

    private static String databaseUrl() throws SQLException {
        String url = "jdbc:h2:mem:meal-photo-migration-" + UUID.randomUUID() +
            ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            execute(connection, "CREATE DOMAIN TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        }
        return url;
    }

    private static String migrationResource(String path) throws IOException {
        try (InputStream input = MealPhotoJournalMigrationTests.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing migration resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long insertUser(Connection connection, String providerUserId) throws SQLException {
        execute(connection,
            "INSERT INTO users (provider, provider_user_id, email, name, profile_completed, timezone, " +
                "created_at, updated_at) VALUES ('GOOGLE', '" + providerUserId + "', '" + providerUserId +
                "@example.com', 'Owner', FALSE, 'UTC', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        try (ResultSet row = connection.createStatement().executeQuery(
            "SELECT id FROM users WHERE provider_user_id = '" + providerUserId + "'"
        )) {
            row.next();
            return row.getLong(1);
        }
    }

    private static String foodInsert(long id, long userId, String name) {
        return "INSERT INTO foods (id, user_id, archived, name, normalized_name, serving_amount, serving_unit, " +
            "calories_kcal, carbohydrates_g, protein_g, fat_g, created_at, updated_at) VALUES (" +
            id + ", " + userId + ", FALSE, '" + name + "', '" + name + "', 100.000, 'g', 100.000, " +
            "10.000, 10.000, 1.000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String mealInsert(long id, long userId, String sourceTextLiteral) {
        return "INSERT INTO meals (id, user_id, meal_date, meal_type, eaten_at, source_text, created_at, updated_at) " +
            "VALUES (" + id + ", " + userId + ", DATE '2026-08-29', 'LUNCH', NULL, " + sourceTextLiteral +
            ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String mealItemInsert(long id, long mealId, long foodId, String foodName) {
        return "INSERT INTO meal_items (id, meal_id, food_id, amount, food_name, unit, calories_kcal, " +
            "carbohydrates_g, protein_g, fat_g, created_at, updated_at) VALUES (" +
            id + ", " + mealId + ", " + foodId + ", 100.000, '" + foodName + "', 'g', 100.000, " +
            "10.000, 10.000, 1.000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String photoInsert(long id, long mealId, String objectKey) {
        return "INSERT INTO meal_photos (id, meal_id, object_key, content_type, byte_size, width, height, " +
            "checksum_sha256, created_at, updated_at) VALUES (" + id + ", " + mealId + ", '" + objectKey +
            "', 'image/jpeg', 12345, 800, 600, " +
            "'0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', " +
            "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (ResultSet row = connection.createStatement().executeQuery(sql)) {
            row.next();
            return row.getInt(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
