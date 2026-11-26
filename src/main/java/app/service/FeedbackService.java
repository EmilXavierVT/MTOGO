package app.service;

import app.dao.OrderDao;
import app.entities.enums.Role;
import app.entities.enums.Status;
import app.entities.orderFlow.Delivery;
import app.entities.orderFlow.Feedback;
import app.entities.orderFlow.Order;
import app.exceptions.ApiException;
import app.features.FeatureCatalog;
import app.features.FeatureFlags;
import app.security.Ownership;
import app.security.SecurityController.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;

public final class FeedbackService {
    public record Ratings(int foodRating, int overallExperienceRating, int courierRating) {}
    public record SavedFeedback(UUID id, int foodRating, int overallExperienceRating, int courierRating) {}
    public record FeedbackView(UUID orderId, UUID deliveryId, UUID courierId, String courierName,
                               SavedFeedback feedback) {}

    private final EntityManagerFactory emf;
    private final FeatureFlags flags;
    private final OrderDao orders = new OrderDao();

    public FeedbackService(EntityManagerFactory emf, FeatureFlags flags) {
        this.emf = emf;
        this.flags = flags;
    }

    public FeedbackView find(CurrentUser customer, UUID orderId) {
        requireCustomer(customer);
        return emf.callInTransaction(em -> {
            Order order = orders.find(em, orderId);
            requireAccess(customer, order);
            Delivery delivery = deliveredOrder(em, order);
            return view(delivery, feedback(em, delivery));
        });
    }

    public FeedbackView submit(CurrentUser customer, UUID orderId, Ratings ratings) {
        requireCustomer(customer);
        return emf.callInTransaction(em -> {
            // All submissions for an order share this lock, including competing browser tabs.
            Order order = orders.findForUpdate(em, orderId);
            requireAccess(customer, order);
            Delivery delivery = deliveredOrder(em, order);
            if (feedback(em, delivery) != null) {
                throw new ApiException(409, "Feedback already submitted");
            }
            validate(ratings);
            Feedback feedback = new Feedback();
            feedback.setDelivery(delivery);
            feedback.setFoodRating(ratings.foodRating());
            feedback.setOverAllExperienceRating(ratings.overallExperienceRating());
            feedback.setCourierRating(ratings.courierRating());
            em.persist(feedback);
            return view(delivery, feedback);
        });
    }

    private void requireAccess(CurrentUser customer, Order order) {
        if (order == null) throw new ApiException(404, "Order not found");
        Ownership.requireCustomer(customer, order.getCustomer().getId());
        flags.requireEnabled(FeatureCatalog.CUSTOMER_FEEDBACK);
    }

    private static Delivery deliveredOrder(EntityManager em, Order order) {
        if (order.getStatus() != Status.DELIVERED) {
            throw new ApiException(409, "Feedback requires a delivered order");
        }
        Delivery delivery = em.createQuery("""
                select d from Delivery d join fetch d.courier where d.order.id = :orderId
                """, Delivery.class).setParameter("orderId", order.getId()).getSingleResultOrNull();
        if (delivery == null || delivery.getStatus() != Status.DELIVERED || delivery.getDeliveryTime() == null) {
            throw new ApiException(409, "Feedback requires a completed delivery");
        }
        return delivery;
    }

    private static Feedback feedback(EntityManager em, Delivery delivery) {
        return em.createQuery("select f from Feedback f where f.delivery.id = :deliveryId", Feedback.class)
                .setParameter("deliveryId", delivery.getId()).getSingleResultOrNull();
    }

    private static FeedbackView view(Delivery delivery, Feedback feedback) {
        SavedFeedback saved = feedback == null ? null : new SavedFeedback(feedback.getId(), feedback.getFoodRating(),
                feedback.getOverAllExperienceRating(), feedback.getCourierRating());
        return new FeedbackView(delivery.getOrder().getId(), delivery.getId(), delivery.getCourier().getId(),
                delivery.getCourier().getName(), saved);
    }

    private static void validate(Ratings ratings) {
        if (ratings == null || !inRange(ratings.foodRating()) || !inRange(ratings.overallExperienceRating())
                || !inRange(ratings.courierRating())) {
            throw new ApiException(400, "All three ratings must be integers from 1 to 5");
        }
    }

    private static boolean inRange(int rating) {
        return rating >= 1 && rating <= 5;
    }

    private static void requireCustomer(CurrentUser customer) {
        if (customer == null || customer.id() == null) throw new ApiException(401, "Authentication required");
        if (customer.role() != Role.CUSTOMER) throw new ApiException(403, "Customer account required");
    }
}
