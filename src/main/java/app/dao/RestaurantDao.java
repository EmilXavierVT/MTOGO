package app.dao;

import app.entities.restaurant.Menu;
import app.entities.restaurant.Restaurant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.List;
import java.util.UUID;

public class RestaurantDao {

    private final EntityManagerFactory emf;

    public  RestaurantDao(EntityManagerFactory emf){ this.emf = emf; }

    public List<Restaurant> getAllRestaurants() {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery("""
            select r from Restaurant r 
            order by r.id """, Restaurant.class)
            .getResultList();
        }
    }

    public List<Menu> getAllRestaurantMenus(UUID restaurantId){

        try(EntityManager em = emf.createEntityManager()){
            return  em.createQuery("""
            select m from Menu m left join fetch m.products where m.restaurant.id = :restaurantId
            order by m.name
            """, Menu.class)
            .setParameter("restaurantId", restaurantId)
            .getResultList();
        }
    }
}
