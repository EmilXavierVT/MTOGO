package app.service;

import app.config.H2TestDatabase;
import app.dao.OrderDao;
import app.entities.enums.Role;
import app.entities.enums.Status;
import app.entities.orderFlow.Delivery;
import app.entities.orderFlow.Order;
import app.exceptions.ApiException;
import app.security.SecurityController.CurrentUser;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryServiceTest {
    private EntityManagerFactory emf;
    private DeliveryService service;
    private DeliveryTestData data;

    @BeforeEach
    void setup() {
        emf = H2TestDatabase.create();
        service = new DeliveryService(emf, new OrderDao());
        data = DeliveryTestData.create(emf);
    }

    @AfterEach
    void close() {
        if (emf != null) emf.close();
    }

    // -----------------------------------
    @Test
    void claimsPicksUpAndDeliversTheSameOrderAndPersistsEachStep() {
        Delivery claimed = service.acceptDelivery(data.courier(), data.orderId());
        assertEquals(Status.CLAIMED, claimed.getStatus());
        Delivery savedClaim = assertSavedDelivery(claimed.getId(), Status.CLAIMED, Status.READY_FOR_DELIVERY);
        assertNotNull(savedClaim.getClaimedTime());
        assertNull(savedClaim.getPickupTime());
        assertNull(savedClaim.getDeliveryTime());

        Delivery pickedUp = service.pickupDelivery(data.courier(), data.orderId());
        assertEquals(claimed.getId(), pickedUp.getId());
        assertEquals(Status.UNDER_DELIVERY, pickedUp.getStatus());
        Delivery savedPickup = assertSavedDelivery(claimed.getId(), Status.UNDER_DELIVERY, Status.UNDER_DELIVERY);
        assertEquals(savedClaim.getClaimedTime(), savedPickup.getClaimedTime());
        assertNotNull(savedPickup.getPickupTime());
        assertFalse(savedPickup.getPickupTime().isBefore(savedPickup.getClaimedTime()));
        assertNull(savedPickup.getDeliveryTime());

        Delivery delivered = service.deliveredDelivery(data.courier(), data.orderId());
        assertEquals(claimed.getId(), delivered.getId());
        assertEquals(Status.DELIVERED, delivered.getStatus());
        Delivery saved = assertSavedDelivery(claimed.getId(), Status.DELIVERED, Status.DELIVERED);
        assertEquals(savedClaim.getClaimedTime(), saved.getClaimedTime());
        assertEquals(savedPickup.getPickupTime(), saved.getPickupTime());
        assertNotNull(saved.getDeliveryTime());
        assertFalse(saved.getDeliveryTime().isBefore(saved.getPickupTime()));
        assertEquals(1L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void rejectsASecondOrderWhileTheCourierHasAnActiveDelivery() {
        UUID firstDelivery = service.acceptDelivery(data.courier(), data.orderId()).getId();

        assertError(409, "Courier already has an active delivery",
                () -> service.acceptDelivery(data.courier(), data.otherOrderId()));
        assertSavedDelivery(firstDelivery, Status.CLAIMED, Status.READY_FOR_DELIVERY);

        service.pickupDelivery(data.courier(), data.orderId());
        assertError(409, "Courier already has an active delivery",
                () -> service.acceptDelivery(data.courier(), data.otherOrderId()));
        assertSavedDelivery(firstDelivery, Status.UNDER_DELIVERY, Status.UNDER_DELIVERY);
        assertEquals(1L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void canClaimAnotherOrderAfterCompletingTheFirstDelivery() {
        UUID firstDelivery = claimAndPickUp().getId();
        service.deliveredDelivery(data.courier(), data.orderId());

        Delivery secondDelivery = service.acceptDelivery(data.courier(), data.otherOrderId());

        assertNotEquals(firstDelivery, secondDelivery.getId());
        assertEquals(data.otherOrderId(), secondDelivery.getOrder().getId());
        assertEquals(data.courier().id(), secondDelivery.getCourier().getId());
        assertEquals(Status.CLAIMED, secondDelivery.getStatus());
        assertSavedDelivery(firstDelivery, Status.DELIVERED, Status.DELIVERED);
        assertEquals(2L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void rejectsACustomerFromAllCourierActions() {
        assertAll(
                () -> assertError(403, () -> service.acceptDelivery(data.customer(), data.orderId())),
                () -> assertError(403, () -> service.pickupDelivery(data.customer(), data.orderId())),
                () -> assertError(403, () -> service.deliveredDelivery(data.customer(), data.orderId())));
        assertEquals(0L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void rejectsRequestsWithoutAnAuthenticatedUserId() {
        CurrentUser missingId = new CurrentUser(null, "Courier", "courier@test.local", Role.COURIER);

        assertAll(
                () -> assertError(401, () -> service.acceptDelivery(null, data.orderId())),
                () -> assertError(401, () -> service.pickupDelivery(missingId, data.orderId())),
                () -> assertError(401, () -> service.deliveredDelivery(null, data.orderId())));
        assertEquals(0L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void rejectsRequestsWithNoPersistedCourierAccount() {
        CurrentUser missingCourier = new CurrentUser(UUID.randomUUID(), "Courier", "courier@test.local", Role.COURIER);

        assertAll(
                () -> assertError(403, "Courier account required", () -> service.acceptDelivery(missingCourier, data.orderId())),
                () -> assertError(403, "Courier account required", () -> service.pickupDelivery(missingCourier, data.orderId())),
                () -> assertError(403, "Courier account required", () -> service.deliveredDelivery(missingCourier, data.orderId())));
        assertEquals(0L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void rejectsAllActionsOnAnUnknownOrder() {
        UUID missingOrderId = UUID.randomUUID();

        assertAll(
                () -> assertError(404, () -> service.acceptDelivery(data.courier(), missingOrderId)),
                () -> assertError(404, () -> service.pickupDelivery(data.courier(), missingOrderId)),
                () -> assertError(404, () -> service.deliveredDelivery(data.courier(), missingOrderId)));
        assertEquals(0L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void rejectsClaimingAnOrderThatIsNotReady() {
        emf.runInTransaction(em -> em.find(Order.class, data.orderId()).setStatus(Status.PENDING));

        assertError(409, "Not ready to be delivered", () -> service.acceptDelivery(data.courier(), data.orderId()));
        assertEquals(0L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void rejectsClaimingAnOrderAlreadyAssignedToAnotherCourier() {
        UUID deliveryId = service.acceptDelivery(data.courier(), data.orderId()).getId();

        assertError(409, "Order already assigned", () -> service.acceptDelivery(data.otherCourier(), data.orderId()));
        assertSavedDelivery(deliveryId, Status.CLAIMED, Status.READY_FOR_DELIVERY);
        assertEquals(1L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void rejectsPickupAndDeliveryWhenTheOrderHasNoAssignment() {
        assertError(409, "Order has no delivery assignment", () -> service.pickupDelivery(data.courier(), data.orderId()));
        assertError(409, "Order has no delivery assignment", () -> service.deliveredDelivery(data.courier(), data.orderId()));
        assertEquals(0L, deliveryCount());
    }

    // -----------------------------------
    @Test
    void rejectsPickupAndDeliveryByACourierWhoDoesNotOwnTheAssignment() {
        UUID deliveryId = service.acceptDelivery(data.courier(), data.orderId()).getId();
        assertError(403, () -> service.pickupDelivery(data.otherCourier(), data.orderId()));
        assertSavedDelivery(deliveryId, Status.CLAIMED, Status.READY_FOR_DELIVERY);

        service.pickupDelivery(data.courier(), data.orderId());
        assertError(403, () -> service.deliveredDelivery(data.otherCourier(), data.orderId()));
        assertSavedDelivery(deliveryId, Status.UNDER_DELIVERY, Status.UNDER_DELIVERY);
    }

    // -----------------------------------
    @Test
    void rejectsDeliveryBeforePickup() {
        UUID deliveryId = service.acceptDelivery(data.courier(), data.orderId()).getId();

        assertError(409, "Delivery must be picked up before it can be marked as delivered",
                () -> service.deliveredDelivery(data.courier(), data.orderId()));
        assertNull(assertSavedDelivery(deliveryId, Status.CLAIMED, Status.READY_FOR_DELIVERY).getDeliveryTime());
    }

    // -----------------------------------
    @Test
    void rejectsRepeatedPickupWithoutChangingThePickupTime() {
        UUID deliveryId = claimAndPickUp().getId();
        LocalDateTime pickupTime = assertSavedDelivery(deliveryId, Status.UNDER_DELIVERY, Status.UNDER_DELIVERY).getPickupTime();

        assertError(409, "Delivery is not awaiting pickup", () -> service.pickupDelivery(data.courier(), data.orderId()));
        assertEquals(pickupTime, assertSavedDelivery(deliveryId, Status.UNDER_DELIVERY, Status.UNDER_DELIVERY).getPickupTime());
    }

    // -----------------------------------
    @Test
    void rejectsRepeatedDeliveryWithoutChangingTheDeliveryTime() {
        UUID deliveryId = claimAndPickUp().getId();
        service.deliveredDelivery(data.courier(), data.orderId());
        LocalDateTime deliveredTime = assertSavedDelivery(deliveryId, Status.DELIVERED, Status.DELIVERED).getDeliveryTime();

        assertError(409, () -> service.deliveredDelivery(data.courier(), data.orderId()));
        assertEquals(deliveredTime, assertSavedDelivery(deliveryId, Status.DELIVERED, Status.DELIVERED).getDeliveryTime());
    }

    // -----------------------------------
    @Test
    void rejectsPickupWhenClaimTimeIsMissing() {
        UUID deliveryId = service.acceptDelivery(data.courier(), data.orderId()).getId();
        emf.runInTransaction(em -> em.find(Delivery.class, deliveryId).setClaimedTime(null));

        assertError(409, "Delivery has no claim time", () -> service.pickupDelivery(data.courier(), data.orderId()));
        assertNull(assertSavedDelivery(deliveryId, Status.CLAIMED, Status.READY_FOR_DELIVERY).getPickupTime());
    }

    // -----------------------------------
    @Test
    void rejectsPickupWhenItsTimeWouldPrecedeTheClaimTime() {
        UUID deliveryId = service.acceptDelivery(data.courier(), data.orderId()).getId();
        emf.runInTransaction(em -> em.find(Delivery.class, deliveryId).setClaimedTime(LocalDateTime.now().plusDays(1)));

        assertError(409, "Pickup time cannot be before claim time", () -> service.pickupDelivery(data.courier(), data.orderId()));
        assertNull(assertSavedDelivery(deliveryId, Status.CLAIMED, Status.READY_FOR_DELIVERY).getPickupTime());
    }

    // -----------------------------------
    @Test
    void rejectsDeliveryWhenPickupTimeIsMissing() {
        UUID deliveryId = claimAndPickUp().getId();
        emf.runInTransaction(em -> em.find(Delivery.class, deliveryId).setPickupTime(null));

        assertError(409, "Delivery must be picked up before it can be marked as delivered",
                () -> service.deliveredDelivery(data.courier(), data.orderId()));
        assertNull(assertSavedDelivery(deliveryId, Status.UNDER_DELIVERY, Status.UNDER_DELIVERY).getDeliveryTime());
    }

    // -----------------------------------
    @Test
    void rejectsDeliveryWhenItsTimeWouldPrecedeThePickupTime() {
        UUID deliveryId = claimAndPickUp().getId();
        emf.runInTransaction(em -> em.find(Delivery.class, deliveryId).setPickupTime(LocalDateTime.now().plusDays(1)));

        assertError(409, "Delivery time cannot be before pickup time", () -> service.deliveredDelivery(data.courier(), data.orderId()));
        assertNull(assertSavedDelivery(deliveryId, Status.UNDER_DELIVERY, Status.UNDER_DELIVERY).getDeliveryTime());
    }
    // -----------------------------------

    private Delivery claimAndPickUp() {
        service.acceptDelivery(data.courier(), data.orderId());
        return service.pickupDelivery(data.courier(), data.orderId());
    }

    private Delivery assertSavedDelivery(UUID deliveryId, Status deliveryStatus, Status orderStatus) {
        try (var em = emf.createEntityManager()) {
            Delivery saved = em.find(Delivery.class, deliveryId);
            assertNotNull(saved);
            assertEquals(deliveryId, saved.getId());
            assertEquals(deliveryStatus, saved.getStatus());
            assertEquals(data.orderId(), saved.getOrder().getId());
            assertEquals(orderStatus, saved.getOrder().getStatus());
            assertEquals(data.customer().id(), saved.getOrder().getCustomer().getId());
            assertEquals(data.courier().id(), saved.getCourier().getId());
            assertEquals(data.courier().name(), saved.getCourier().getName());
            return saved;
        }
    }

    private long deliveryCount() {
        try (var em = emf.createEntityManager()) {
            return em.createQuery("select count(d) from Delivery d", Long.class).getSingleResult();
        }
    }

    private static void assertError(int expectedCode, String expectedMessage, Executable action) {
        ApiException error = assertError(expectedCode, action);
        assertEquals(expectedMessage, error.getMessage());
    }

    private static ApiException assertError(int expectedCode, Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(expectedCode, error.getCode());
        return error;
    }
}
