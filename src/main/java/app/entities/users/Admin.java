package app.entities.users;

import jakarta.persistence.Entity;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Getter
@Setter

public class Admin extends User {

}
