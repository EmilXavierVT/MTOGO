package app.entities.orderFlow;

import app.entities.enums.Status;
import app.entities.users.Courier;
import jakarta.persistence.*;
import lombok.*;

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

    @OneToOne(optional = false)
    private Order order;

    @ManyToOne(optional = false)
    private Courier courier;


}
