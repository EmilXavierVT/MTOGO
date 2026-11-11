package app.entities.users;

import app.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer extends User {

    @Column(nullable = false)
    private String address;

    @Column
    private Double wallet;

    Customer(UUID id, String name, String passwordHash, String email, Role role, Instant createdAt) {
        super(id, name, passwordHash, email, role, createdAt);
    }
}
