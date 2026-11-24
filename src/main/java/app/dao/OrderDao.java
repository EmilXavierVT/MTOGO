package app.dao;

import app.entities.orderFlow.Order;
import jakarta.persistence.EntityManager;

public class OrderDao {
    //Bruger em fra service
    public void save(EntityManager em, Order order) {
        em.persist(order);
    }
}
