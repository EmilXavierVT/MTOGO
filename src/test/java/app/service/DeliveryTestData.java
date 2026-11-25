package app.service;

import app.entities.enums.Role;
import app.entities.enums.Status;
import app.entities.orderFlow.Order;
import app.entities.restaurant.Restaurant;
import app.entities.users.Courier;
import app.entities.users.User;
import app.security.SecurityController.CurrentUser;
import jakarta.persistence.EntityManagerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

record DeliveryTestData(CurrentUser courier, CurrentUser otherCourier, CurrentUser customer,
                        UUID orderId, UUID otherOrderId) {
    static DeliveryTestData create(EntityManagerFactory emf) {
        return emf.callInTransaction(em -> {
            User customer = new User("Customer", "test-password", "customer@test.local");
            User owner = new User("Owner", "test-password", "owner@test.local");
            owner.setRole(Role.RESTAURANT_OWNER);
            Courier courier = courier("First courier", "first-courier@test.local", customer.getPasswordHash());
            Courier otherCourier = courier("Second courier", "second-courier@test.local", customer.getPasswordHash());
            em.persist(customer);
            em.persist(owner);
            em.persist(courier);
            em.persist(otherCourier);

            Restaurant restaurant = new Restaurant();
            restaurant.setName("Test restaurant");
            restaurant.setAddress("Restaurant address");
            restaurant.setOwner(owner);
            em.persist(restaurant);

            Order order = readyOrder(customer, restaurant);
            Order otherOrder = readyOrder(customer, restaurant);
            em.persist(order);
            em.persist(otherOrder);
            return new DeliveryTestData(currentUser(courier), currentUser(otherCourier), currentUser(customer),
                    order.getId(), otherOrder.getId());
        });
    }

    private static Courier courier(String name, String email, String passwordHash) {
        Courier courier = new Courier();
        courier.setName(name);
        courier.setEmail(email);
        courier.setPasswordHash(passwordHash);
        courier.setRole(Role.COURIER);
        return courier;
    }

    private static Order readyOrder(User customer, Restaurant restaurant) {
        Order order = new Order();
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setCustomerName(customer.getName());
        order.setCustomerPhone("12345678");
        order.setDeliveryAddress("Delivery address");
        order.setOrderTime(LocalDateTime.now());
        order.setStatus(Status.READY_FOR_DELIVERY);
        order.setTotalPrice(new BigDecimal("50.00"));
        order.setNetAmount(new BigDecimal("40.00"));
        order.setCommission(new BigDecimal("4.80"));
        return order;
    }

    private static CurrentUser currentUser(User user) {
        return new CurrentUser(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
