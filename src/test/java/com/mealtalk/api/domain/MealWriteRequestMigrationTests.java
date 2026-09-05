package com.mealtalk.api.domain;

import org.flywaydb.core.Flyway;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V5 remembers which record a client request id already produced, so a retried
 * save replays that record instead of creating a second one. The uniqueness is
 * per user: two clients must be free to pick the same UUID.
 */
class MealWriteRequestMigrationTests {
    @Test
    void productionAndH2MigrationBodiesStayInSync() throws Exception {
        assertEquals(
            migrationResource("/db/migration/V5__add_meal_write_requests.sql"),
            migrationResource("/db/migration-h2/V5__add_meal_write_requests.sql")
        );
    }

    @Test
    void freshDatabaseMigratesThroughV5AndScopesRequestIdsPerUser() throws Exception {
        String url = databaseUrl();

        MigrateResult result = flyway(url).migrate();

        assertEquals(5, result.migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            for (String column : List.of(
                "id", "user_id", "client_request_id", "fingerprint", "meal_id", "created_at", "updated_at"
            )) {
                assertEquals(1, count(connection, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_name = 'meal_write_requests' AND column_name = '%s'
                    """.formatted(column)), "meal_write_requests is missing column " + column);
            }

            long firstUser = insertUser(connection, "write-request-owner");
            long secondUser = insertUser(connection, "write-request-other");
            execute(connection, mealInsert(900, firstUser));
            execute(connection, mealInsert(901, secondUser));
            String requestId = UUID.randomUUID().toString();

            execute(connection, writeRequestInsert(1, firstUser, requestId, 900));
            execute(connection, writeRequestInsert(2, secondUser, requestId, 901));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM meal_write_requests"),
                "the same request id must be usable by two different users");

            assertThrows(SQLException.class,
                () -> execute(connection, writeRequestInsert(3, firstUser, requestId, 900)),
                "one user may not register the same request id twice");

            execute(connection, "DELETE FROM meals WHERE id = 900");
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM meal_write_requests WHERE meal_id = 900"),
                "deleting a meal must cascade to its write-request row");
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

    private static String databaseUrl() throws SQLException {
        String url = "jdbc:h2:mem:meal-write-request-migration-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            execute(connection, "CREATE DOMAIN TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        }
        return url;
    }

    private static String migrationResource(String path) throws IOException {
        try (InputStream input = MealWriteRequestMigrationTests.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing migration resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long insertUser(Connection connection, String providerUserId) throws SQLException {
        execute(connection,
            "INSERT INTO users (provider, provider_user_id, email, name, profile_completed, timezone, "
                + "created_at, updated_at) VALUES ('GOOGLE', '" + providerUserId + "', '" + providerUserId
                + "@example.com', 'Owner', FALSE, 'UTC', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        try (ResultSet row = connection.createStatement().executeQuery(
            "SELECT id FROM users WHERE provider_user_id = '" + providerUserId + "'"
        )) {
            row.next();
            return row.getLong(1);
        }
    }

    private static String mealInsert(long id, long userId) {
        return "INSERT INTO meals (id, user_id, meal_date, meal_type, eaten_at, source_text, created_at, updated_at) "
            + "VALUES (" + id + ", " + userId + ", DATE '2026-08-29', 'LUNCH', NULL, '메모', "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String writeRequestInsert(long id, long userId, String requestId, long mealId) {
        return "INSERT INTO meal_write_requests (id, user_id, client_request_id, fingerprint, meal_id, "
            + "created_at, updated_at) VALUES (" + id + ", " + userId + ", '" + requestId + "', "
            + "'0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', " + mealId + ", "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
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
