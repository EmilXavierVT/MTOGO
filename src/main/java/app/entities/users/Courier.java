package app.entities.users;

import app.entities.enums.Role;
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
public class Courier extends User {

    @Column
    private Double rating;

    @Column
    private Double bonus;

     Courier(UUID id, String name, String passwordHash, String email, Role role, Instant createdAt) {
        super(id, name, passwordHash, email, role, createdAt);
    }


}
