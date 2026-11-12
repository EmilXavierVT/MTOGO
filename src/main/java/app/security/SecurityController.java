package app.security;

import app.entities.enums.Role;
import app.entities.users.User;
import app.exceptions.ApiException;
import app.exceptions.ValidationException;
import dk.bugelhartmann.TokenSecurity;
import dk.bugelhartmann.UserDTO;
import io.javalin.http.Context;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public class SecurityController implements ISecurityController {
    public record LoginRequest(String email, String password) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record RegisterRequest(String name, String email, String password) {}
    public record CurrentUser(UUID id, String name, String email, Role role) {}
    private final SecurityDao dao;
    private final TokenSecurity tokens = new TokenSecurity();
    private final String secret;

    public SecurityController(EntityManagerFactory emf, String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("SECRET_KEY must contain at least 32 bytes");
        this.dao = new SecurityDao(emf);
        this.secret = secret;
    }

    public void login(Context ctx) {
        LoginRequest request = ctx.bodyAsClass(LoginRequest.class);
        User user;
        try { user = dao.getVerifiedUser(request.email(), request.password()); }
        catch (ValidationException e) { throw new ApiException(401, "Invalid email or password"); }
        try {
            String token = tokens.createToken(new UserDTO(user.getId().toString(), user.getRolesAsStrings()),
                    "mtogo", "3600000", secret);
            ctx.json(Map.of("token", token, "user", view(user)));
        } catch (Exception e) { throw new ApiException(500, "Could not create token"); }
    }

    public void register(Context ctx) {
        RegisterRequest request = ctx.bodyAsClass(RegisterRequest.class);
        validateRegistration(request);
        try {
            User user = dao.createUser(request.name().trim(), request.password(), request.email());
            ctx.status(201).json(view(user));
        } catch (org.hibernate.exception.ConstraintViolationException e) {
            throw new ApiException(409, "Account already exists");
        }
    }

    public void authenticate(Context ctx) {
        if (isPublic(ctx)) return;
        String header = ctx.header("Authorization");
        if (header == null || !header.matches("Bearer [^\\s]+")) throw new ApiException(401, "Bearer token required");
        ctx.attribute("user", authenticateToken(header.substring(7)));
    }

    private CurrentUser authenticateToken(String token) {
        try {
            if (!tokens.tokenIsValid(token, secret) || !tokens.tokenNotExpired(token))
                throw new IllegalArgumentException();
            User user = dao.findById(UUID.fromString(tokens.getUserWithRolesFromToken(token).getUsername()));
            if (user == null) throw new IllegalArgumentException();
            return view(user);
        } catch (Exception e) { throw new ApiException(401, "Invalid or expired token"); }
    }

    private boolean isPublic(Context ctx) {
        if (ctx.method().name().equals("OPTIONS")) return true;
        if (ctx.method().name().equals("GET")) return ctx.path().equals("/");
        return ctx.method().name().equals("POST") && java.util.Set.of("/api/auth/login", "/api/auth/register").contains(ctx.path());
    }

    private void validateRegistration(RegisterRequest request) {
        if (request.name() == null || request.name().isBlank())
            throw new ApiException(400, "Name required");
        if (request.email() == null || !request.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ApiException(400, "Valid email required");
        validatePassword(request.password());
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(400, "Password must have at least 8 characters and at most 72 bytes");
    }
    public void authorize(Context ctx) {
        CurrentUser user = ctx.attribute("user");
        if (user != null && !ctx.routeRoles().contains(user.role()))
            throw new ApiException(403, "Access denied");
    }

    public void me(Context ctx) { ctx.json((CurrentUser) ctx.attribute("user")); }
    private static CurrentUser view(User u) { return new CurrentUser(u.getId(), u.getName(), u.getEmail(), u.getRole()); }
}



