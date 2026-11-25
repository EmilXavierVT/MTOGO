package app.entities.orderFlow;

import app.entities.enums.Status;
import app.entities.restaurant.Restaurant;
import app.entities.users.User;
import app.entities.orderFlow.OrderLine;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name ="orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private LocalDateTime orderTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    // DKK uden moms: priser i hele øre / 1,25 kan give tre decimaler.
    @Column(name = "net_amount", nullable = false, precision = 13, scale = 3)
    private BigDecimal netAmount;


    @Column(name = "commission", nullable = false, precision = 12, scale = 2)
    private BigDecimal commission;

    // A refund is recorded once when the restaurant rejects a paid order.
    @Column(name = "refunded_amount", precision = 12, scale = 2)
    private BigDecimal refundedAmount;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private User customer;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private Restaurant restaurant;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderLine> lines = new ArrayList<>();


}
