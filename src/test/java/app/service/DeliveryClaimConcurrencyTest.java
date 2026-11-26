package app.service;

import app.config.H2TestDatabase;
import app.dao.OrderDao;
import app.entities.enums.Status;
import app.entities.orderFlow.Delivery;
import app.entities.orderFlow.Order;
import app.exceptions.ApiException;
import app.security.SecurityController.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryClaimConcurrencyTest {
    private EntityManagerFactory emf;
    private DeliveryTestData data;

    @BeforeEach
    void setup() {
        emf = H2TestDatabase.create();
        data = DeliveryTestData.create(emf);
    }

    @AfterEach
    void close() {
        if (emf != null) emf.close();
    }

    // -----------------------------------
    @Test
    void onlyOneCourierCanClaimTheSameOrderConcurrently() throws Exception {
        assertCompetingClaimRejected(data.otherCourier(), data.orderId(), "Order already assigned");
    }

    // -----------------------------------
    @Test
    void sameCourierCannotClaimTwoDifferentOrdersConcurrently() throws Exception {
        assertCompetingClaimRejected(data.courier(), data.otherOrderId(), "Courier already has an active delivery");
    }
    // -----------------------------------

    private void assertCompetingClaimRejected(CurrentUser courier, UUID orderId, String expectedMessage) throws Exception {
        CompletableFuture<Integer> firstClaimSession = new CompletableFuture<>();
        CountDownLatch releaseFirstClaim = new CountDownLatch(1);
        OrderDao pausingDao = new OrderDao() {
            @Override
            public Order findForUpdate(EntityManager em, UUID id) {
                Order order = super.findForUpdate(em, id);
                Number sessionId = (Number) em.createNativeQuery("select session_id()").getSingleResult();
                firstClaimSession.complete(sessionId.intValue());
                awaitRelease(releaseFirstClaim);
                return order;
            }
        };
        DeliveryService firstService = new DeliveryService(emf, pausingDao);
        DeliveryService competingService = new DeliveryService(emf, new OrderDao());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> firstService.acceptDelivery(data.courier(), data.orderId()));
            int blockingSession = firstClaimSession.get(10, TimeUnit.SECONDS);
            var second = executor.submit(() -> competingService.acceptDelivery(courier, orderId));
            assertTrue(awaitBlockedClaim(blockingSession), "H2 did not report the second claim waiting on the first claim's lock");
            releaseFirstClaim.countDown();

            Delivery winner = first.get(10, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
            ApiException conflict = assertInstanceOf(ApiException.class, failure.getCause());
            assertEquals(409, conflict.getCode());
            assertEquals(expectedMessage, conflict.getMessage());
            assertOnlyWinningAssignmentWasSaved(winner.getId());
        } finally {
            releaseFirstClaim.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private boolean awaitBlockedClaim(int blockingSession) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try (var em = emf.createEntityManager()) {
            var blockedClaim = em.createNativeQuery("""
                    select count(*) from information_schema.sessions where blocker_id = :blockingSession
                    """).setParameter("blockingSession", blockingSession);
            while (System.nanoTime() - deadline < 0) {
                Number blockedSessions = (Number) blockedClaim.getSingleResult();
                if (blockedSessions.longValue() > 0) return true;
                Thread.sleep(25);
            }
            return false;
        }
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            assertTrue(release.await(15, TimeUnit.SECONDS), "First claim was not released");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("First claim was interrupted", e);
        }
    }

    private void assertOnlyWinningAssignmentWasSaved(UUID winningId) {
        try (var em = emf.createEntityManager()) {
            var saved = em.createQuery("select d from Delivery d", Delivery.class).getResultList();
            assertEquals(1, saved.size());
            assertEquals(winningId, saved.get(0).getId());
            assertEquals(data.orderId(), saved.get(0).getOrder().getId());
            assertEquals(data.courier().id(), saved.get(0).getCourier().getId());
            assertEquals(Status.CLAIMED, saved.get(0).getStatus());
        }
    }
}
