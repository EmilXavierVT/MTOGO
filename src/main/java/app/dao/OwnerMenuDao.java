package app.dao;

import app.entities.restaurant.Menu;
import app.entities.restaurant.Product;
import app.exceptions.ApiException;
import app.security.Ownership;
import app.security.SecurityController.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class OwnerMenuDao {
    public record ProductDetails(String nameDa, String descriptionDa, String nameEn,
                                 String descriptionEn, BigDecimal priceDkkInclVat, boolean available) {}

    private final EntityManagerFactory emf;

    public OwnerMenuDao(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public List<Menu> findMenus(UUID ownerId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery("""
                    select m from Menu m left join fetch m.products
                    where m.restaurant.owner.id = :ownerId
                    order by m.name, m.id
                    """, Menu.class)
                    .setParameter("ownerId", ownerId)
                    .getResultList();
        }
    }

    public Product createProduct(CurrentUser owner, UUID menuId, ProductDetails details) {
        return emf.callInTransaction(em -> {
            Menu menu = requireOwnedMenu(em, owner, menuId);
            Product product = new Product();
            product.setMenu(menu);
            applyDetails(product, details);
            em.persist(product);
            return product;
        });
    }

    public Product updateProduct(CurrentUser owner, UUID menuId, UUID productId, ProductDetails details) {
        return emf.callInTransaction(em -> {
            requireOwnedMenu(em, owner, menuId);
            Product product = em.createQuery("""
                    select p from Product p where p.id = :productId and p.menu.id = :menuId
                    """, Product.class)
                    .setParameter("productId", productId)
                    .setParameter("menuId", menuId)
                    .getSingleResultOrNull();
            if (product == null) {
                throw new ApiException(404, "Product not found in this menu");
            }
            applyDetails(product, details);
            return product;
        });
    }

    private static Menu requireOwnedMenu(EntityManager em, CurrentUser owner, UUID menuId) {
        Menu menu = em.find(Menu.class, menuId);
        if (menu == null) {
            throw new ApiException(404, "Menu not found");
        }
        Ownership.requireRestaurantOwner(owner, menu.getRestaurant().getOwner().getId());
        return menu;
    }

    private static void applyDetails(Product product, ProductDetails details) {
        product.setName(details.nameDa());
        product.setDescriptionDa(details.descriptionDa());
        product.setNameEn(details.nameEn());
        product.setDescriptionEn(details.descriptionEn());
        product.setPrice(details.priceDkkInclVat());
        product.setAvailable(details.available());
    }
}
