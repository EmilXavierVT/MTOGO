package app.entities.orderFlow;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "feedback")
@Check(name = "ck_feedback_ratings", constraints = "foodRating between 1 and 5 "
        + "and overAllExperienceRating between 1 and 5 and courierRating between 1 and 5")
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

    @OneToOne(optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, unique = true)
    private Delivery delivery;
}
