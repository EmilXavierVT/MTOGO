package app.service;

import app.controller.OrderController;
import app.dao.OrderDao;
import app.entities.enums.Status;
import app.entities.orderFlow.Order;
import app.entities.orderFlow.OrderLine;
import app.security.SecurityController;
import app.security.Ownership;
import app.entities.enums.Role;
import app.entities.restaurant.Product;
import app.entities.restaurant.Restaurant;
import app.entities.users.User;
import app.exceptions.ApiException;
import app.exceptions.CheckoutException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class OrderService {
    private final EntityManagerFactory emf;
    private final OrderDao orderDao;
    private final PricingService pricingService = new PricingService();
    private final PaymentSimulator paymentSimulator = new PaymentSimulator();

    public OrderService(EntityManagerFactory emf) {
        this(emf, new OrderDao());
    }

    public OrderService(EntityManagerFactory emf, OrderDao orderDao) {
        this.emf = emf;
        this.orderDao = orderDao;
    }

    public Order checkout(SecurityController.CurrentUser user, OrderController.CheckoutRequest request){
        requireCustomer(user);
        validateRequest(request);

        // Any exception rolls back the order and every order line.
        return emf.callInTransaction(em -> {
            User customer = em.find(User.class, user.id());
            if (customer == null || customer.getRole() != Role.CUSTOMER) {
                throw new ApiException(403, "Customer account not found or no longer a customer");
            }
            Restaurant restaurant = em.find(Restaurant.class, request.restaurantId());
            if (restaurant == null) {
                throw new ApiException(404, "Restaurant not found");
            }
            Map<UUID, Product> validatedProducts = loadValidatedProducts(em, request);

            PricingService.PricingResult pricing =
                    pricingService.calculate(request.products(), validatedProducts);

            if (pricing.total().compareTo(request.acceptedTotal()) != 0) {
                throw CheckoutException.priceChanged(pricing.total(), request.products().stream()
                        .map(item -> validatedProducts.get(item.productId()))
                        .map(product -> new CheckoutException.ItemPrice(
                                product.getId(), product.getName(), product.getPrice()))
                        .toList());
            }

            if (paymentSimulator.simulate(request.simulatedPaymentResult())
                    == PaymentSimulator.PaymentResult.DECLINED) {
                throw new ApiException(402, "Simulated payment declined. No order was created.");
            }

            Order order = buildOrder(customer, restaurant, request, pricing);

            for (OrderController.OrderItemRequest item : request.products()) {
                Product product = validatedProducts.get(item.productId());
                addOrderLine(order, product, item.quantity(), product.getPrice());
            }


            orderDao.save(em, order);
            return order;
        });
    }

    public Order findCustomerOrder(SecurityController.CurrentUser user, UUID orderId) {
        requireCustomer(user);
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery("""
                    select o from Order o
                    join fetch o.restaurant
                    left join fetch o.lines
                    where o.id = :orderId and o.customer.id = :customerId
                    """, Order.class)
                    .setParameter("orderId", orderId)
                    .setParameter("customerId", user.id())
                    .getResultList().stream().findFirst()
                    .orElseThrow(() -> new ApiException(404, "Order not found"));
        }
    }

    public List<Order> findCustomerOrders(SecurityController.CurrentUser user) {
        requireCustomer(user);
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery("""
                    select distinct o from Order o
                    join fetch o.restaurant
                    left join fetch o.lines
                    where o.customer.id = :customerId
                    order by o.orderTime desc, o.id desc
                    """, Order.class)
                    .setParameter("customerId", user.id())
                    .getResultList();
        }
    }

    public Order transitionRestaurantOrder(SecurityController.CurrentUser user, UUID orderId, Status target) {
        requireRestaurantTransition(user, orderId, target);
        return emf.callInTransaction(em -> {
            Order order = orderDao.findForUpdate(em, orderId);
            if (order == null) {
                throw new ApiException(404, "Order not found");
            }
            Ownership.requireRestaurantOwner(user, order.getRestaurant().getOwner().getId());
            Status current = order.getStatus();
            if (!allowedRestaurantTransition(current, target)) {
                throw new ApiException(409, "Invalid restaurant order transition");
            }
            if (target == Status.REJECTED) {
                order.setRefundedAmount(order.getTotalPrice());
                order.setRefundedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
                // Commission is a checkout quote, not a charge; no courier bonus is accrued here.
            }
            order.setStatus(target);
            return order;
        });
    }

    private static void requireRestaurantTransition(SecurityController.CurrentUser user, UUID orderId, Status target) {
        requireRestaurantOwnerActor(user);
        if (orderId == null || target == null) {
            throw new ApiException(400, "Order ID and target status are required");
        }
        if (!restaurantTarget(target)) {
            throw new ApiException(400, "Unsupported restaurant order status");
        }
    }

    private static void requireRestaurantOwnerActor(SecurityController.CurrentUser user) {
        if (user == null || user.id() == null) {
            throw new ApiException(401, "Authentication required");
        }
        if (user.role() != Role.RESTAURANT_OWNER) {
            throw new ApiException(403, "Restaurant owner required");
        }
    }

    private static boolean restaurantTarget(Status target) {
        return target == Status.ACCEPTED || target == Status.REJECTED || target == Status.READY_FOR_DELIVERY;
    }

    private static boolean allowedRestaurantTransition(Status current, Status target) {
        return (current == Status.PENDING && (target == Status.ACCEPTED || target == Status.REJECTED))
                || (current == Status.ACCEPTED && target == Status.READY_FOR_DELIVERY);
    }

    private static void requireCustomer(SecurityController.CurrentUser user) {
        if (user == null || user.id() == null) {
            throw new ApiException(401, "Authentication required");
        }
        if (user.role() != Role.CUSTOMER) {
            throw new ApiException(403, "Only customers can access customer orders");
        }
    }

    private Map<UUID, Product> loadValidatedProducts(
            EntityManager em, OrderController.CheckoutRequest request) {
        Map<UUID, Product> products = new HashMap<>();
        // Lock in stable order and hold locks until checkout commits.
        var sortedItems = request.products().stream()
                .sorted(java.util.Comparator.comparing(OrderController.OrderItemRequest::productId))
                .toList();
        for (OrderController.OrderItemRequest item : sortedItems) {
            Product product = em.find(Product.class, item.productId(), LockModeType.PESSIMISTIC_WRITE);
            if (product == null) {
                throw CheckoutException.unavailableProduct(item.productId(), true);
            }
            if (!product.getMenu().getRestaurant().getId().equals(request.restaurantId())) {
                throw new ApiException(400, "All products must belong to the selected restaurant");
            }
            if (!product.isAvailable()) {
                throw CheckoutException.unavailableProduct(item.productId(), false);
            }
            products.put(product.getId(), product);
        }
        return products;
    }

    private static void validateRequest(OrderController.CheckoutRequest request) {
        if (request == null || request.restaurantId() == null) {
            throw new ApiException(400, "Restaurant ID is required");
        }
        if (request.products() == null || request.products().isEmpty()) {
            throw new ApiException(400, "Cart must contain at least one product");
        }
        if (request.simulatedPaymentResult() == null) {
            throw new ApiException(400, "simulatedPaymentResult must be APPROVED or DECLINED");
        }
        validateItems(request.products());
        validateContactDetails(request);
        validateAcceptedTotal(request.acceptedTotal());
    }

    private static void validateItems(java.util.List<OrderController.OrderItemRequest> items) {
        Set<UUID> productIds = new HashSet<>();
        for (OrderController.OrderItemRequest item : items) {
            if (item == null || item.productId() == null) {
                throw new ApiException(400, "Each item must have a product ID");
            }
            if (item.quantity() <= 0) {
                throw new ApiException(400, "Quantity must be positive");
            }
            if (!productIds.add(item.productId())) {
                throw new ApiException(400, "Duplicate product IDs are not allowed");
            }
        }
    }

    private static void validateContactDetails(OrderController.CheckoutRequest request) {
        if (request.name() == null || request.name().isBlank()
                || request.phone() == null || request.phone().isBlank()
                || request.address() == null || request.address().isBlank()) {
            throw new ApiException(400, "Name, phone and delivery address are required");
        }
    }

    private static void validateAcceptedTotal(BigDecimal acceptedTotal) {
        if (acceptedTotal == null || acceptedTotal.signum() < 0
                || acceptedTotal.stripTrailingZeros().scale() > 2) {
            throw new ApiException(400, "Accepted total must be a non-negative amount in whole øre");
        }
    }
    private Order buildOrder(
            User customer,
            Restaurant restaurant,
            OrderController.CheckoutRequest request,
            PricingService.PricingResult pricing
    ) {
        Order order = new Order();

        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setOrderTime(LocalDateTime.now());
        order.setStatus(Status.PENDING);

        // Kundeoplysninger ved bestillingen
        order.setCustomerName(request.name());
        order.setCustomerPhone(request.phone());
        order.setDeliveryAddress(request.address());

        // Priser ved bestillingen
        order.setTotalPrice(pricing.total());
        order.setNetAmount(pricing.netAmount());
        order.setCommission(pricing.commission());

        return order;
    }

    private void addOrderLine(
            Order order,
            Product product,
            int quantity,
            BigDecimal unitPrice
    ) {
        OrderLine line = new OrderLine();

        line.setOrder(order);
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setLineTotal(
                unitPrice.multiply(BigDecimal.valueOf(quantity))
        );

        order.getLines().add(line);
    }
}
