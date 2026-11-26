package app.support;

import app.dao.OrderDao;
import app.entities.enums.Role;
import app.entities.enums.Status;
import app.entities.orderFlow.Order;
import app.entities.restaurant.Restaurant;
import app.entities.users.Courier;
import app.entities.users.User;
import app.security.SecurityController.CurrentUser;
import app.service.DeliveryService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FeedbackTestData(SecurityTestData.Fixtures accounts, CurrentUser customer, CurrentUser courier,
                               UUID orderId, UUID deliveryId) {
    public static FeedbackTestData create(EntityManagerFactory emf) {
        var accounts = SecurityTestData.populate(emf);
        Courier courier = emf.callInTransaction(em -> {
            Courier actor = new Courier();
            actor.setName("Assigned courier");
            actor.setEmail("feedback-courier@example.test");
            actor.setRole(Role.COURIER);
            actor.setPasswordHash(em.find(User.class, accounts.accounts().get(Role.CUSTOMER)).getPasswordHash());
            em.persist(actor);
            return actor;
        });
        UUID orderId = emf.callInTransaction(em -> {
            Order order = new Order();
            order.setCustomer(em.find(User.class, accounts.accounts().get(Role.CUSTOMER)));
            order.setRestaurant(em.find(Restaurant.class, accounts.restaurant()));
            order.setCustomerName("Saved customer");
            order.setCustomerPhone("12345678");
            order.setDeliveryAddress("Saved address");
            order.setOrderTime(LocalDateTime.now());
            order.setStatus(Status.READY_FOR_DELIVERY);
            order.setTotalPrice(new BigDecimal("150.00"));
            order.setNetAmount(new BigDecimal("120.000"));
            order.setCommission(new BigDecimal("13.75"));
            em.persist(order);
            return order.getId();
        });
        CurrentUser actor = new CurrentUser(courier.getId(), courier.getName(), courier.getEmail(), Role.COURIER);
        DeliveryService deliveries = new DeliveryService(emf, new OrderDao());
        UUID deliveryId = deliveries.acceptDelivery(actor, orderId).getId();
        deliveries.pickupDelivery(actor, orderId);
        deliveries.deliveredDelivery(actor, orderId);
        return new FeedbackTestData(accounts,
                new CurrentUser(accounts.accounts().get(Role.CUSTOMER), "Customer", "customer@example.test", Role.CUSTOMER),
                actor, orderId, deliveryId);
    }
}
