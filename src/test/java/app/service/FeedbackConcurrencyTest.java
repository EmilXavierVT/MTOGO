package app.service;

import app.config.CourierTestDatabase;
import app.entities.orderFlow.Delivery;
import app.entities.orderFlow.Feedback;
import app.entities.orderFlow.Order;
import app.exceptions.ApiException;
import app.support.FeedbackTestData;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MTOGO_COURIER_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
class FeedbackConcurrencyTest {
    private EntityManagerFactory emf;
    private FeedbackTestData data;
    private FeedbackService service;

    @BeforeEach
    void setup() {
        emf = CourierTestDatabase.create();
        data = FeedbackTestData.create(emf);
        service = new FeedbackService(emf, key -> true);
    }

    @AfterEach
    void close() {
        if (emf != null) emf.close();
    }

    @Test
    void competingSubmissionsSaveExactlyOneCompleteSetOfRatings() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try (var blocker = emf.createEntityManager()) {
            blocker.getTransaction().begin();
            blocker.find(Order.class, data.orderId(), LockModeType.PESSIMISTIC_WRITE);
            try {
                var ratings = new FeedbackService.Ratings(5, 4, 3);
                var first = executor.submit(() -> service.submit(data.customer(), data.orderId(), ratings));
                var second = executor.submit(() -> service.submit(data.customer(), data.orderId(), ratings));
                assertTrue(awaitBothBlocked(), "Both submissions must wait for the same PostgreSQL order lock");
                blocker.getTransaction().commit();
                int saved = 0;
                int conflicts = 0;
                for (var result : List.of(first, second)) {
                    try {
                        var view = result.get(10, TimeUnit.SECONDS);
                        assertEquals(data.courier().id(), view.courierId());
                        assertEquals(5, view.feedback().foodRating());
                        assertEquals(4, view.feedback().overallExperienceRating());
                        assertEquals(3, view.feedback().courierRating());
                        saved++;
                    } catch (ExecutionException e) {
                        assertEquals(409, assertInstanceOf(ApiException.class, e.getCause()).getCode());
                        conflicts++;
                    }
                }
                assertEquals(1, saved);
                assertEquals(1, conflicts);
                emf.runInTransaction(em -> {
                    assertEquals(1L, em.createQuery("select count(f) from Feedback f", Long.class).getSingleResult());
                    assertEquals(new BigDecimal("2.75"), em.find(Delivery.class, data.deliveryId()).getEarnedBonus());
                });
            } finally {
                if (blocker.getTransaction().isActive()) blocker.getTransaction().rollback();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void databaseAlsoRejectsInvalidRatingsAndDuplicateDeliveryRows() {
        assertThrows(PersistenceException.class, () -> emf.runInTransaction(em -> {
            Feedback invalid = feedback(em.find(Delivery.class, data.deliveryId()), 0);
            em.persist(invalid);
            em.flush();
        }));
        service.submit(data.customer(), data.orderId(), new FeedbackService.Ratings(1, 5, 3));
        assertThrows(PersistenceException.class, () -> emf.runInTransaction(em -> {
            em.persist(feedback(em.find(Delivery.class, data.deliveryId()), 5));
            em.flush();
        }));
        assertEquals(1, service.find(data.customer(), data.orderId()).feedback().foodRating());
    }

    @Test
    void failedInsertRollsBackAndAllowsAnExplicitRetry() {
        emf.runInTransaction(em -> {
            em.createNativeQuery("""
                    create function fail_feedback() returns trigger language plpgsql as $$
                    begin raise exception 'test forced feedback failure'; end $$
                    """).executeUpdate();
            em.createNativeQuery("""
                    create trigger fail_feedback before insert on feedback
                    for each row execute function fail_feedback()
                    """).executeUpdate();
        });
        var ratings = new FeedbackService.Ratings(2, 3, 4);
        try {
            assertThrows(PersistenceException.class, () -> service.submit(data.customer(), data.orderId(), ratings));
            assertNull(service.find(data.customer(), data.orderId()).feedback());
        } finally {
            emf.runInTransaction(em -> {
                em.createNativeQuery("drop trigger fail_feedback on feedback").executeUpdate();
                em.createNativeQuery("drop function fail_feedback()").executeUpdate();
            });
        }
        assertEquals(2, service.submit(data.customer(), data.orderId(), ratings).feedback().foodRating());
    }

    private static Feedback feedback(Delivery delivery, int foodRating) {
        Feedback feedback = new Feedback();
        feedback.setDelivery(delivery);
        feedback.setFoodRating(foodRating);
        feedback.setOverAllExperienceRating(5);
        feedback.setCourierRating(3);
        return feedback;
    }

    private boolean awaitBothBlocked() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try (var observer = emf.createEntityManager()) {
            var waiting = observer.createNativeQuery("""
                    select count(*) from pg_stat_activity where datname = current_database()
                    and wait_event_type = 'Lock' and query ilike '%orders%'
                    """);
            while (System.nanoTime() - deadline < 0) {
                if (((Number) waiting.getSingleResult()).intValue() >= 2) return true;
                Thread.sleep(25);
            }
            return false;
        }
    }
}
