package app.dao;

import app.entities.orderFlow.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.util.UUID;

public class OrderDao {
    //Bruger em fra service
    public void save(EntityManager em, Order order) {
        em.persist(order);
    }

    public Order find(EntityManager em, UUID id) {
        return em.find(Order.class, id);
    }

    public Order findForUpdate(EntityManager em, UUID id) {
        return em.find(Order.class, id, LockModeType.PESSIMISTIC_WRITE);
    }
}
