package app.services.security;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordService {

    public String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    public boolean verify(String plainPassword, String hashedPassword) {
        return plainPassword != null && BCrypt.checkpw(plainPassword, hashedPassword);
    }
}
