package app.config;

import app.support.FeedbackTestData;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MTOGO_COURIER_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
class FeedbackMigrationTest {
    private EntityManagerFactory emf;
    private FeedbackTestData data;
    private Connection connection;
    private Statement sql;
    private String migration;

    @BeforeEach
    void setup() throws Exception {
        emf = CourierTestDatabase.create();
        data = FeedbackTestData.create(emf);
        connection = DriverManager.getConnection(System.getenv("MTOGO_COURIER_TEST_JDBC_URL"), "courier_test", "courier_test");
        sql = connection.createStatement();
        sql.execute("alter table feedback drop constraint ck_feedback_ratings");
        migration = Files.readString(Path.of("docs/migrations/issue-60-feedback-constraints.sql"));
    }

    @AfterEach
    void close() throws Exception {
        try {
            if (sql != null) {
                sql.execute("rollback");
                sql.close();
            }
            if (connection != null) connection.close();
        } finally {
            if (emf != null) emf.close();
        }
    }

    @Test
    void upgradesLegacyFeedbackWithoutRewritingItAndCanBeRepeated() throws Exception {
        sql.execute(insert(1));
        sql.execute(migration);
        sql.execute(migration);
        assertThrows(SQLException.class, () -> sql.execute(insert(0)));
        assertThrows(SQLException.class, () -> sql.execute(insert(5)));
        try (var rows = sql.executeQuery("select foodRating, overAllExperienceRating, courierRating from feedback")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
            assertEquals(4, rows.getInt(2));
            assertEquals(3, rows.getInt(3));
            assertFalse(rows.next());
        }
    }

    @Test
    void refusesInvalidLegacyDataWithoutChangingOrDeletingIt() throws Exception {
        sql.execute(insert(0));
        assertThrows(SQLException.class, () -> sql.execute(migration));
        sql.execute("rollback");
        try (var rows = sql.executeQuery("select foodRating from feedback")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
            assertFalse(rows.next());
        }
    }

    @Test
    void addsMissingUniquenessButRefusesDuplicateLegacyRows() throws Exception {
        sql.execute("""
                do $$ declare c record; begin
                  for c in select conname from pg_constraint where conrelid = 'feedback'::regclass and contype = 'u'
                  loop execute format('alter table feedback drop constraint %I', c.conname); end loop;
                end $$
                """);
        sql.execute(insert(1));
        sql.execute(insert(5));
        assertThrows(SQLException.class, () -> sql.execute(migration));
        sql.execute("rollback");
        // Removing this test's duplicate lets the migration supply the absent unique index.
        sql.execute("delete from feedback where foodRating = 5");
        sql.execute(migration);
        assertThrows(SQLException.class, () -> sql.execute(insert(5)));
    }

    private String insert(int foodRating) {
        return "insert into feedback (id, delivery_id, foodRating, overAllExperienceRating, courierRating) "
                + "values (gen_random_uuid(), '" + data.deliveryId() + "', " + foodRating + ", 4, 3)";
    }
}
