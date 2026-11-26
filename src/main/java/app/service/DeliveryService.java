package app.service;

import app.dao.OrderDao;
import app.entities.enums.Role;
import app.entities.enums.Status;
import app.entities.orderFlow.Delivery;
import app.entities.orderFlow.Order;
import app.entities.users.Courier;
import app.exceptions.ApiException;
import app.security.SecurityController;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class DeliveryService {
    private static final BigDecimal BONUS_RATE = new BigDecimal("0.20");
    private final EntityManagerFactory emf;
    private final OrderDao orderDao;
    public DeliveryService(EntityManagerFactory emf, OrderDao orderDao) {
        this.emf = emf;
        this.orderDao = orderDao;
    }

    public List<Order> availableOrders(SecurityController.CurrentUser user) {
        requireCourier(user);
        return emf.callInTransaction(em -> em.createQuery("""
                select o from Order o join fetch o.restaurant
                where o.status = :ready
                and not exists (select d.id from Delivery d where d.order = o)
                order by o.orderTime, o.id
                """, Order.class)
                .setParameter("ready", Status.READY_FOR_DELIVERY)
                .getResultList());
    }

    public Delivery activeDelivery(SecurityController.CurrentUser user) {
        requireCourier(user);
        return emf.callInTransaction(em -> em.createQuery("""
                select d from Delivery d join fetch d.order o join fetch o.restaurant
                where d.courier.id = :courierId and d.status in (:claimed, :underDelivery)
                """, Delivery.class)
                .setParameter("courierId", user.id())
                .setParameter("claimed", Status.CLAIMED)
                .setParameter("underDelivery", Status.UNDER_DELIVERY)
                .getSingleResultOrNull());
    }

    public Delivery acceptDelivery(SecurityController.CurrentUser user, UUID orderId) {
        requireCourier(user);

        return emf.callInTransaction(em -> {
            Courier courier = em.find(Courier.class, user.id(), LockModeType.PESSIMISTIC_WRITE);
            if (courier == null) {
                throw new ApiException(403, "Courier account required");
            }
            Order order = orderDao.findForUpdate(em, orderId);
            if (order == null) {
                throw new ApiException(404, "Order not found");
            }
            if (order.getStatus() != Status.READY_FOR_DELIVERY) {
                throw new ApiException(409, "Not ready to be delivered");
            }

            boolean alreadyAssigned = !em.createQuery("""
                    select d.id from Delivery d where d.order.id = :orderId
                    """, UUID.class)
                    .setParameter("orderId", orderId)
                    .setMaxResults(1)
                    .getResultList().isEmpty();
            if (alreadyAssigned) {
                throw new ApiException(409, "Order already assigned");
            }

            boolean hasActiveDelivery = !em.createQuery("""
                    select d.id from Delivery d
                    where d.courier.id = :courierId and d.status in (:claimed, :underDelivery)
                    """, UUID.class)
                    .setParameter("courierId", courier.getId())
                    .setParameter("claimed", Status.CLAIMED)
                    .setParameter("underDelivery", Status.UNDER_DELIVERY)
                    .setMaxResults(1)
                    .getResultList().isEmpty();
            if (hasActiveDelivery) {
                throw new ApiException(409, "Courier already has an active delivery");
            }

            Delivery delivery = new Delivery();
            delivery.setCourier(courier);
            delivery.setOrder(order);
            delivery.setStatus(Status.CLAIMED);
            delivery.setClaimedTime(LocalDateTime.now());
            em.persist(delivery);

            return delivery;
        });
    }

    public Delivery pickupDelivery(SecurityController.CurrentUser user, UUID orderId) {
        requireCourier(user);
        return emf.callInTransaction(em -> {
            Courier courier = em.find(Courier.class, user.id());
            if (courier == null) {
                throw new ApiException(403, "Courier account required");
            }
            Order order = orderDao.findForUpdate(em, orderId);
            if (order == null) {
                throw new ApiException(404, "Order not found");
            }

            Delivery delivery = deliveryForOrder(em, orderId);
            if (!user.id().equals(delivery.getCourier().getId())) {
                throw new ApiException(403, "Only the assigned courier can pick up this delivery");
            }
            if (order.getStatus() != Status.READY_FOR_DELIVERY || delivery.getStatus() != Status.CLAIMED) {
                throw new ApiException(409, "Delivery is not awaiting pickup");
            }

            LocalDateTime claimedTime = delivery.getClaimedTime();
            if (claimedTime == null) {
                throw new ApiException(409, "Delivery has no claim time");
            }
            LocalDateTime pickupTime = LocalDateTime.now();
            if (pickupTime.isBefore(claimedTime)) {
                throw new ApiException(409, "Pickup time cannot be before claim time");
            }
            order.setStatus(Status.UNDER_DELIVERY);
            delivery.setStatus(Status.UNDER_DELIVERY);
            delivery.setPickupTime(pickupTime);

            return delivery;
        });
    }

    public Delivery deliveredDelivery(SecurityController.CurrentUser user, UUID orderId) {
        requireCourier(user);
        return emf.callInTransaction(em -> {
            Courier courier = em.find(Courier.class, user.id());
            if (courier == null) {
                throw new ApiException(403, "Courier account required");
            }
            Order order = orderDao.findForUpdate(em, orderId);
            if (order == null) {
                throw new ApiException(404, "Order not found");
            }

            Delivery delivery = deliveryForOrder(em, orderId);
            if (!user.id().equals(delivery.getCourier().getId())) {
                throw new ApiException(403, "Only the assigned courier can mark this delivery as delivered");
            }
            if (order.getStatus() != Status.UNDER_DELIVERY
                    || delivery.getStatus() != Status.UNDER_DELIVERY || delivery.getPickupTime() == null) {
                throw new ApiException(409, "Delivery must be picked up before it can be marked as delivered");
            }

            LocalDateTime deliveryTime = LocalDateTime.now();
            if (deliveryTime.isBefore(delivery.getPickupTime())) {
                throw new ApiException(409, "Delivery time cannot be before pickup time");
            }
            delivery.setEarnedBonus(order.getCommission().multiply(BONUS_RATE).setScale(2, RoundingMode.HALF_UP));
            order.setStatus(Status.DELIVERED);
            delivery.setStatus(Status.DELIVERED);
            delivery.setDeliveryTime(deliveryTime);

            return delivery;
        });
    }

    private static Delivery deliveryForOrder(EntityManager em, UUID orderId) {
        Delivery delivery = em.createQuery("""
                select d from Delivery d where d.order.id = :orderId
                """, Delivery.class)
                .setParameter("orderId", orderId)
                .getSingleResultOrNull();
        if (delivery == null) {
            throw new ApiException(409, "Order has no delivery assignment");
        }
        return delivery;
    }

    private static void requireCourier(SecurityController.CurrentUser user) {
        if (user == null || user.id() == null) {
            throw new ApiException(401, "Authentication required");
        }
        if (user.role() != Role.COURIER) {
            throw new ApiException(403, "Only couriers can access customer orders");
        }
    }
}
