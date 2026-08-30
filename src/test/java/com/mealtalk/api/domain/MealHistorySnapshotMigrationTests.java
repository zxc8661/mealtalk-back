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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealHistorySnapshotMigrationTests {
    @Test
    void freshDatabaseMigratesThroughFoodNameSnapshotWithNonNullContract() throws Exception {
        String url = databaseUrl();

        assertEquals(
            migrationResource("/db/migration/V3__add_meal_item_food_name_snapshot.sql"),
            migrationResource("/db/migration-h2/V3__add_meal_item_food_name_snapshot.sql")
        );

        MigrateResult result = flyway(url).migrate();

        assertEquals(3, result.migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             ResultSet column = connection.createStatement().executeQuery("""
                 SELECT is_nullable
                 FROM information_schema.columns
                 WHERE table_name = 'meal_items' AND column_name = 'food_name'
                 """)) {
            assertTrue(column.next());
            assertEquals("NO", column.getString("is_nullable"));
        }
    }

    @Test
    void upgradeBackfillsFoodNameWithoutChangingExistingUnitOrNutritionSnapshots() throws Exception {
        String url = databaseUrl();
        MigrateResult v2 = v2Flyway(url).migrate();
        assertEquals(2, v2.migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            seedV2MealItem(connection);
        }

        MigrateResult v3 = flyway(url).migrate();
        assertEquals(1, v3.migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             ResultSet item = connection.createStatement().executeQuery("""
                 SELECT food_name, unit, calories_kcal, carbohydrates_g, protein_g, fat_g
                 FROM meal_items
                 WHERE id = 300
                 """)) {
            assertTrue(item.next());
            assertEquals("Original oats", item.getString("food_name"));
            assertEquals("serving", item.getString("unit"));
            assertEquals("778.000", item.getBigDecimal("calories_kcal").toPlainString());
            assertEquals("132.600", item.getBigDecimal("carbohydrates_g").toPlainString());
            assertEquals("33.800", item.getBigDecimal("protein_g").toPlainString());
            assertEquals("13.800", item.getBigDecimal("fat_g").toPlainString());
        }
    }

    private static Flyway flyway(String url) {
        return Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration-h2")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load();
    }

    private static Flyway v2Flyway(String url) {
        return Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration-h2")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .target(MigrationVersion.fromVersion("2"))
            .load();
    }

    private static String databaseUrl() throws SQLException {
        String url = "jdbc:h2:mem:meal-history-migration-" + UUID.randomUUID() +
            ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            execute(connection, "CREATE DOMAIN TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        }
        return url;
    }

    private static String migrationResource(String path) throws IOException {
        try (InputStream input = MealHistorySnapshotMigrationTests.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing migration resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void seedV2MealItem(Connection connection) throws SQLException {
        execute(connection, """
            INSERT INTO users
                (id, provider, provider_user_id, email, name, profile_completed, timezone, created_at, updated_at)
            VALUES
                (1, 'GOOGLE', 'snapshot-owner', 'snapshot@example.com', 'Snapshot owner', FALSE, 'UTC',
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
        execute(connection, """
            INSERT INTO foods
                (id, user_id, archived, name, normalized_name, serving_amount, serving_unit, calories_kcal,
                 carbohydrates_g, protein_g, fat_g, created_at, updated_at)
            VALUES
                (100, 1, FALSE, 'Original oats', 'original oats', 100.000, 'g', 389.000,
                 66.300, 16.900, 6.900, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
        execute(connection, """
            INSERT INTO meals
                (id, user_id, meal_date, meal_type, eaten_at, created_at, updated_at)
            VALUES
                (200, 1, DATE '2026-08-29', 'BREAKFAST', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
        execute(connection, """
            INSERT INTO meal_items
                (id, meal_id, food_id, amount, unit, calories_kcal, carbohydrates_g, protein_g, fat_g,
                 created_at, updated_at)
            VALUES
                (300, 200, 100, 200.000, 'serving', 778.000, 132.600, 33.800, 13.800,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
