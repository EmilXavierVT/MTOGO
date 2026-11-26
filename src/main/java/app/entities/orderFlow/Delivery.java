package app.entities.orderFlow;

import app.entities.enums.Status;
import app.entities.users.Courier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    private LocalDateTime deliveryTime;

    private LocalDateTime pickupTime;

    private LocalDateTime claimedTime;

    @Column(name = "earned_bonus", precision = 12, scale = 2)
    private BigDecimal earnedBonus;

    @OneToOne(optional = false)
    private Order order;

    @ManyToOne(optional = false)
    private Courier courier;


}
