package app.service;

import app.config.H2TestDatabase;
import app.controller.OrderController.CheckoutRequest;
import app.controller.OrderController.OrderItemRequest;
import app.entities.enums.Role;
import app.entities.restaurant.Menu;
import app.entities.restaurant.Product;
import app.entities.restaurant.Restaurant;
import app.entities.users.User;
import app.exceptions.ApiException;
import app.security.SecurityController.CurrentUser;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    private EntityManagerFactory emf;
    private OrderService service;
    private CurrentUser customer;
    private UUID restaurantId;
    private UUID productId;

    @BeforeEach
    void setup() {
        emf = H2TestDatabase.create();
        service = new OrderService(emf);
        emf.runInTransaction(em -> {
            User owner = new User("Owner", "test-password", "owner@test.local");
            owner.setRole(Role.RESTAURANT_OWNER);
            em.persist(owner);
            User user = new User("Customer", "test-password", "customer@test.local");
            em.persist(user);
            customer = new CurrentUser(user.getId(), user.getName(), user.getEmail(), Role.CUSTOMER);
            Restaurant restaurant = new Restaurant();
            restaurant.setName("Test restaurant");
            restaurant.setAddress("Test address");
            restaurant.setOwner(owner);
            em.persist(restaurant);
            restaurantId = restaurant.getId();
            Menu menu = new Menu();
            menu.setName("Menu");
            menu.setRestaurant(restaurant);
            em.persist(menu);
            Product product = new Product();
            product.setName("Dish");
            product.setPrice(new BigDecimal("50.00"));
            product.setMenu(menu);
            em.persist(product);
            productId = product.getId();
        });
    }

    @AfterEach
    void close() {
        if (emf != null) emf.close();
    }

    private CheckoutRequest request(UUID restaurant, List<OrderItemRequest> items) {
        return new CheckoutRequest(restaurant, items, "Customer", "12345678", "Test address", new BigDecimal("100.00"), PaymentSimulator.PaymentResult.APPROVED);
    }

    @Test
    void acceptsValidCheckout() {
        assertDoesNotThrow(() -> service.checkout(customer,
                request(restaurantId, List.of(new OrderItemRequest(productId, 2)))));
    }

    @Test
    void approvedPaymentBuildsPendingOrder() {
        var order = service.checkout(customer,
                request(restaurantId, List.of(new OrderItemRequest(productId, 2))));
        assertEquals(app.entities.enums.Status.PENDING, order.getStatus());
        assertEquals(1, order.getLines().size());
        assertEquals(2, order.getLines().get(0).getQuantity());
        try (var em = emf.createEntityManager()) {
            var saved = em.find(app.entities.orderFlow.Order.class, order.getId());
            assertNotNull(saved);
            assertEquals("Test address", saved.getDeliveryAddress());
            assertEquals(1, saved.getLines().size());
            assertEquals(0, new BigDecimal("100.00").compareTo(saved.getLines().get(0).getLineTotal()));
        }
    }

    @Test
    void declinedPaymentStopsOrderCreation() {
        CheckoutRequest declined = new CheckoutRequest(restaurantId,
                List.of(new OrderItemRequest(productId, 2)), "Customer", "12345678",
                "Test address", new BigDecimal("100.00"), PaymentSimulator.PaymentResult.DECLINED);
        assertCode(402, declined);
        assertNoOrders();
    }

    @Test
    void persistenceFailureRollsBackOrderAndLines() {
        var failingDao = new app.dao.OrderDao() {
            @Override
            public void save(jakarta.persistence.EntityManager em, app.entities.orderFlow.Order order) {
                super.save(em, order);
                em.flush();
                throw new IllegalStateException("Simulated persistence failure");
            }
        };
        var failingService = new OrderService(emf, failingDao);
        assertThrows(IllegalStateException.class, () -> failingService.checkout(customer,
                request(restaurantId, List.of(new OrderItemRequest(productId, 2)))));
        assertNoOrders();
    }

    @Test
    void changedTotalDoesNotSaveAnOrder() {
        assertCode(409, request(restaurantId, List.of(new OrderItemRequest(productId, 1))));
        assertNoOrders();
    }

    private void assertNoOrders() {
        try (var em = emf.createEntityManager()) {
            assertEquals(0L, em.createQuery("select count(o) from Order o", Long.class).getSingleResult());
            assertEquals(0L, em.createQuery("select count(l) from OrderLine l", Long.class).getSingleResult());
        }
    }

    @Test
    void rejectsInvalidCartBeforeDatabaseLookup() {
        assertCode(400, request(restaurantId, List.of()));
        assertCode(400, request(restaurantId, List.of(new OrderItemRequest(productId, 0))));
        assertCode(400, request(restaurantId, List.of(new OrderItemRequest(productId, -1))));
        assertCode(400, request(restaurantId, List.of(
                new OrderItemRequest(productId, 1), new OrderItemRequest(productId, 2))));
    }

    @Test
    void rejectsMissingRestaurantAndProduct() {
        assertCode(404, request(UUID.randomUUID(), List.of(new OrderItemRequest(productId, 1))));
        assertCode(404, request(restaurantId, List.of(new OrderItemRequest(UUID.randomUUID(), 1))));
    }

    @Test
    void rejectsUnavailableProduct() {
        emf.runInTransaction(em -> em.find(Product.class, productId).setAvailable(false));
        assertCode(409, request(restaurantId, List.of(new OrderItemRequest(productId, 1))));
    }

    @Test
    void rejectsProductFromAnotherRestaurant() {
        emf.runInTransaction(em -> {
            Restaurant other = new Restaurant();
            other.setName("Other");
            other.setAddress("Other address");
            other.setOwner(em.find(Restaurant.class, restaurantId).getOwner());
            em.persist(other);
            em.find(Product.class, productId).getMenu().setRestaurant(other);
        });
        assertCode(400, request(restaurantId, List.of(new OrderItemRequest(productId, 1))));
    }

    private void assertCode(int expected, CheckoutRequest request) {
        ApiException error = assertThrows(ApiException.class, () -> service.checkout(customer, request));
        assertEquals(expected, error.getCode());
    }
}
