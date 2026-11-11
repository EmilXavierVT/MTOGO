package app.config;

import app.entities.Complaint;
import app.entities.orderFlow.Delivery;
import app.entities.orderFlow.Feedback;
import app.entities.orderFlow.Order;
import app.entities.restaurant.Menu;
import app.entities.restaurant.Product;
import app.entities.restaurant.Restaurant;
import app.entities.users.Admin;
import app.entities.users.Courier;
import app.entities.users.Customer;
import app.entities.users.User;
import org.hibernate.cfg.Configuration;

final class EntityRegistry {

    private EntityRegistry() {}

    static void registerEntities(Configuration configuration) {

        configuration.addAnnotatedClass(Order.class);
        configuration.addAnnotatedClass(Delivery.class);
        configuration.addAnnotatedClass(Feedback.class);

        configuration.addAnnotatedClass(Product.class);
        configuration.addAnnotatedClass(Restaurant.class);
        configuration.addAnnotatedClass(Menu.class);

        configuration.addAnnotatedClass(User.class);
        configuration.addAnnotatedClass(Courier.class);
        configuration.addAnnotatedClass(Customer.class);
        configuration.addAnnotatedClass(Admin.class);

        configuration.addAnnotatedClass(Complaint.class);


    }
}