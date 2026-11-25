package app.service;

import app.config.H2TestDatabase;
import app.dao.OrderDao;
import app.entities.enums.Role;
import app.entities.enums.Status;
import app.entities.orderFlow.Order;
import app.entities.restaurant.Restaurant;
import app.entities.users.User;
import app.exceptions.ApiException;
import app.security.SecurityController.CurrentUser;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantOrderTransitionTest {
    private EntityManagerFactory emf;
    private OrderService service;
    private CurrentUser owner;
    private CurrentUser otherOwner;
    private UUID orderId;

    @BeforeEach
    void setup() {
        emf = H2TestDatabase.create();
        service = new OrderService(emf);
        emf.runInTransaction(em -> {
            User customer = user("customer", Role.CUSTOMER);
            User restaurantOwner = user("owner", Role.RESTAURANT_OWNER);
            User stranger = user("stranger", Role.RESTAURANT_OWNER);
            em.persist(customer);
            em.persist(restaurantOwner);
            em.persist(stranger);
            owner = current(restaurantOwner);
            otherOwner = current(stranger);
            Restaurant restaurant = new Restaurant();
            restaurant.setName("Restaurant");
            restaurant.setAddress("Address");
            restaurant.setOwner(restaurantOwner);
            em.persist(restaurant);
            Order order = new Order();
            order.setCustomer(customer);
            order.setRestaurant(restaurant);
            order.setOrderTime(LocalDateTime.now());
            order.setStatus(Status.PENDING);
            order.setTotalPrice(new BigDecimal("125.00"));
            order.setNetAmount(new BigDecimal("100.000"));
            order.setCommission(new BigDecimal("6.00"));
            order.setCustomerName("Customer");
            order.setCustomerPhone("12345678");
            order.setDeliveryAddress("Address");
            em.persist(order);
            orderId = order.getId();
        });
    }

    @AfterEach
    void close() {
        emf.close();
    }

    @Test
    void acceptsThenMarksReadyAndRejectsEveryOtherTransition() {
        assertEquals(Status.ACCEPTED, service.transitionRestaurantOrder(owner, orderId, Status.ACCEPTED).getStatus());
        conflict(Status.ACCEPTED);
        conflict(Status.REJECTED);
        assertEquals(Status.READY_FOR_DELIVERY,
                service.transitionRestaurantOrder(owner, orderId, Status.READY_FOR_DELIVERY).getStatus());
        conflict(Status.READY_FOR_DELIVERY);
        assertEquals(400, error(() -> service.transitionRestaurantOrder(owner, orderId, Status.DELIVERED)).getCode());
    }

    @Test
    void rejectionRecordsOneRefundWithoutChangingCommission() {
        Order rejected = service.transitionRestaurantOrder(owner, orderId, Status.REJECTED);
        assertEquals(new BigDecimal("125.00"), rejected.getRefundedAmount());
        assertNotNull(rejected.getRefundedAt());
        conflict(Status.REJECTED);
        conflict(Status.ACCEPTED);
        try (var em = emf.createEntityManager()) {
            Order saved = new OrderDao().find(em, orderId);
            assertEquals(Status.REJECTED, saved.getStatus());
            assertEquals(new BigDecimal("125.00"), saved.getRefundedAmount());
            assertEquals(rejected.getRefundedAt(), saved.getRefundedAt());
            assertEquals(new BigDecimal("6.00"), saved.getCommission());
        }
    }

    @Test
    void deniesOtherOwnersAndRolesBeforeChangingOrder() {
        assertEquals(401, error(() -> service.transitionRestaurantOrder(null, orderId, Status.REJECTED)).getCode());
        assertEquals(403, error(() -> service.transitionRestaurantOrder(otherOwner, orderId, Status.REJECTED)).getCode());
        CurrentUser customer = new CurrentUser(owner.id(), owner.name(), owner.email(), Role.CUSTOMER);
        assertEquals(403, error(() -> service.transitionRestaurantOrder(customer, orderId, Status.REJECTED)).getCode());
        assertEquals(404, error(() -> service.transitionRestaurantOrder(owner, UUID.randomUUID(), Status.REJECTED)).getCode());
        try (var em = emf.createEntityManager()) {
            assertEquals(Status.PENDING, em.find(Order.class, orderId).getStatus());
        }
    }

    @Test
    void simultaneousRejectionsRecordOnlyOneRefund() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> rejectAfter(start));
            var second = pool.submit(() -> rejectAfter(start));
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        } finally {
            pool.shutdownNow();
        }
        try (var em = emf.createEntityManager()) {
            Order saved = em.find(Order.class, orderId);
            assertEquals(Status.REJECTED, saved.getStatus());
            assertEquals(new BigDecimal("125.00"), saved.getRefundedAmount());
            assertNotNull(saved.getRefundedAt());
        }
    }

    private boolean rejectAfter(CountDownLatch start) {
        try {
            start.await();
            service.transitionRestaurantOrder(owner, orderId, Status.REJECTED);
            return true;
        } catch (ApiException e) {
            assertEquals(409, e.getCode());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void conflict(Status target) {
        assertEquals(409, error(() -> service.transitionRestaurantOrder(owner, orderId, target)).getCode());
    }

    private ApiException error(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(ApiException.class, call);
    }

    private static User user(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(name + "@test.local");
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }

    private static CurrentUser current(User user) {
        return new CurrentUser(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
