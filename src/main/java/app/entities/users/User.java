package app.entities.users;

import app.entities.enums.Role;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import app.services.security.PasswordService;

import java.time.Instant;

import java.util.Set;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor

@Entity
@Table(name = "users")

public class User {
    private static final PasswordService PASSWORDS = new PasswordService();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;

    @Column(unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    public User(String name, String password, String email){
        String hashedPassword = PASSWORDS.hash(password);
        this.name = name;
        this.passwordHash = hashedPassword;
        this.email = email;
        this.role = Role.CUSTOMER;
    }

    public User(UUID id, String name, String passwordHash, String email, Role role, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.passwordHash = passwordHash;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Set<String> getRolesAsStrings() {
        return Set.of(role.name());
    }

    public boolean verifyPassword(String pw) {
        return PASSWORDS.verify(pw, passwordHash);
    }
}


