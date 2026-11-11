package app.entities.orderFlow;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private int foodRating;

    @Column(nullable = false)
    private int overAllExperienceRating;

    @Column(nullable = false)
    private int courierRating;

    @OneToOne
    @JoinColumn(nullable = false)
    private Delivery delivery;
}
