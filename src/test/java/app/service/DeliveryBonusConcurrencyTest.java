package app.service;

import app.config.CourierTestDatabase;
import app.dao.OrderDao;
import app.entities.enums.Status;
import app.entities.orderFlow.Delivery;
import app.entities.orderFlow.Order;
import app.exceptions.ApiException;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MTOGO_COURIER_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
class DeliveryBonusConcurrencyTest {
    private EntityManagerFactory emf;
    private DeliveryService service;
    private DeliveryTestData data;

    @BeforeEach
    void setup() {
        emf = CourierTestDatabase.create();
        service = new DeliveryService(emf, new OrderDao());
        data = DeliveryTestData.create(emf);
        service.acceptDelivery(data.courier(), data.orderId());
        service.pickupDelivery(data.courier(), data.orderId());
    }

    @AfterEach
    void close() {
        if (emf != null) emf.close();
    }

    @Test
    void competingCompletionCallsEarnBonusOnlyOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try (var blocker = emf.createEntityManager()) {
            blocker.getTransaction().begin();
            blocker.find(Order.class, data.orderId(), LockModeType.PESSIMISTIC_WRITE);
            try {
                var first = executor.submit(() -> service.deliveredDelivery(data.courier(), data.orderId()));
                var second = executor.submit(() -> service.deliveredDelivery(data.courier(), data.orderId()));
                assertTrue(awaitBothCompletionsBlocked(), "Both completion calls must be waiting on a PostgreSQL lock");
                blocker.getTransaction().commit();

                assertOneBonusAndOneConflict(List.of(first, second));
                ApiException retry = assertThrows(ApiException.class,
                        () -> service.deliveredDelivery(data.courier(), data.orderId()));
                assertEquals(409, retry.getCode());
                assertNull(service.activeDelivery(data.courier()));
            } finally {
                if (blocker.getTransaction().isActive()) blocker.getTransaction().rollback();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void failedCompletionRollsBackStatusAndBonusTogetherBeforeARetry() {
        emf.runInTransaction(em -> {
            em.createNativeQuery("""
                    create function fail_bonus_completion() returns trigger language plpgsql as $$
                    begin raise exception 'test forced completion failure'; end $$
                    """).executeUpdate();
            em.createNativeQuery("""
                    create trigger fail_bonus_completion before update on deliveries
                    for each row when (new.status = 'DELIVERED') execute function fail_bonus_completion()
                    """).executeUpdate();
        });
        try {
            assertThrows(PersistenceException.class, () -> service.deliveredDelivery(data.courier(), data.orderId()));
            Delivery stillActive = service.activeDelivery(data.courier());
            assertEquals(Status.UNDER_DELIVERY, stillActive.getStatus());
            assertEquals(Status.UNDER_DELIVERY, stillActive.getOrder().getStatus());
            assertNull(stillActive.getEarnedBonus());
            assertNull(stillActive.getDeliveryTime());
        } finally {
            emf.runInTransaction(em -> {
                em.createNativeQuery("drop trigger fail_bonus_completion on deliveries").executeUpdate();
                em.createNativeQuery("drop function fail_bonus_completion()").executeUpdate();
            });
        }

        Delivery delivered = service.deliveredDelivery(data.courier(), data.orderId());
        assertEquals(new BigDecimal("0.96"), delivered.getEarnedBonus());
        assertEquals(Status.DELIVERED, delivered.getStatus());
        assertNull(service.activeDelivery(data.courier()));
    }

    private boolean awaitBothCompletionsBlocked() throws InterruptedException {
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

    private void assertOneBonusAndOneConflict(List<Future<Delivery>> calls) throws Exception {
        int completions = 0;
        int conflicts = 0;
        for (Future<Delivery> call : calls) {
            try {
                Delivery delivered = call.get(10, TimeUnit.SECONDS);
                assertEquals(Status.DELIVERED, delivered.getStatus());
                assertEquals(data.courier().id(), delivered.getCourier().getId());
                assertEquals(new BigDecimal("0.96"), delivered.getEarnedBonus());
                completions++;
            } catch (ExecutionException e) {
                ApiException conflict = assertInstanceOf(ApiException.class, e.getCause());
                assertEquals(409, conflict.getCode());
                conflicts++;
            }
        }
        assertEquals(1, completions);
        assertEquals(1, conflicts);
    }
}
