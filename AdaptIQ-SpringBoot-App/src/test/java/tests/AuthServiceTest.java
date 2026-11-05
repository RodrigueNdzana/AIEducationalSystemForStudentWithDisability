package tests;

import com.aiadaptive.dto.Dto.LoginResult;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.services.AuthService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AuthServiceTest {
    private final Fixture f = new Fixture();
    private final AuthService auth = f.auth;

    @Test void registersStudentAndLogsIn() {
        auth.register("Test Student", "t@x.ac.za", "Secret123");
        LoginResult r = auth.login("t@x.ac.za", "Secret123");
        assertNotNull(r.token());
        assertTrue(auth.resolve(r.token()).isPresent());
        auth.logout(r.token());
        assertFalse(auth.resolve(r.token()).isPresent());
    }

    @Test void rejectsWeakPasswordsAndBadEmail() {
        assertThrows(ApiException.class, () -> auth.register("A", "a@x.ac.za", "short1"));
        assertThrows(ApiException.class, () -> auth.register("A", "a@x.ac.za", "onlyletters"));
        assertThrows(ApiException.class, () -> auth.register("A", "not-an-email", "Secret123"));
    }

    @Test void rejectsDuplicateEmail() {
        auth.register("A", "dup@x.ac.za", "Secret123");
        ApiException e = assertThrows(ApiException.class, () -> auth.register("B", "DUP@x.ac.za", "Secret123"));
        assertEquals(409, e.getStatus());
    }

    @Test void locksAccountAfterFiveFailedAttempts() {
        String id = auth.register("L", "lock@x.ac.za", "Secret123").id();
        for (int i = 1; i <= 4; i++) {
            ApiException e = assertThrows(ApiException.class, () -> auth.login("lock@x.ac.za", "wrong-pass1"));
            assertEquals(401, e.getStatus());
        }
        ApiException fifth = assertThrows(ApiException.class, () -> auth.login("lock@x.ac.za", "wrong-pass1"));
        assertEquals(423, fifth.getStatus());
        // even the right password is refused once locked
        ApiException locked = assertThrows(ApiException.class, () -> auth.login("lock@x.ac.za", "Secret123"));
        assertEquals(423, locked.getStatus());
        auth.unlock(id);
        assertNotNull(auth.login("lock@x.ac.za", "Secret123").token());
    }

    @Test void successfulLoginResetsFailureCounter() {
        auth.register("R", "reset@x.ac.za", "Secret123");
        for (int i = 0; i < 3; i++) {
            assertThrows(ApiException.class, () -> auth.login("reset@x.ac.za", "bad-pass12"));
        }
        auth.login("reset@x.ac.za", "Secret123");
        for (int i = 0; i < 4; i++) {
            assertThrows(ApiException.class, () -> auth.login("reset@x.ac.za", "bad-pass12"));
        }
        assertNotNull(auth.login("reset@x.ac.za", "Secret123").token());   // still not locked
    }

    @Test void unknownEmailGivesSameErrorAsWrongPassword() {
        ApiException e = assertThrows(ApiException.class, () -> auth.login("nobody@x.ac.za", "Secret123"));
        assertEquals(401, e.getStatus());
    }
}
