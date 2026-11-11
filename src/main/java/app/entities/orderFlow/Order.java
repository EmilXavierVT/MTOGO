package app.entities.orderFlow;

import app.entities.enums.Status;
import app.entities.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
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

    @Column(nullable = false)
    private Double totalPrice;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private User customer;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private User restaurant;
}
