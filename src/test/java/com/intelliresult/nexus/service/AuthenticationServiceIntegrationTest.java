package com.intelliresult.nexus.service;

import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.AuthenticationException;
import com.intelliresult.nexus.support.IntegrationTestSupport;
import com.intelliresult.nexus.util.PasswordUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 17. Covers the three outcomes Sec. 5's authentication system
 * requires (valid login, invalid login, disabled-account rejection) plus
 * this project's specific identifier resolution - a login ID can be an
 * email, a roll number, or an employee code, tried in that DAO lookup order.
 */
class AuthenticationServiceIntegrationTest extends IntegrationTestSupport {

    private final AuthenticationService authenticationService = new AuthenticationService();
    private static final String REAL_PASSWORD = "a-genuinely-strong-password";

    // Builds (without persisting) a user whose hash really matches REAL_PASSWORD, so login() can verify it.
    private User newUser(UserRole role, boolean active) {
        User user = new User(unique("user") + "@example.edu", PasswordUtil.hash(REAL_PASSWORD), "Test User", role);
        user.setActive(active);
        return user;
    }

    private User seedUser(UserRole role, boolean active) {
        return seed(() -> {
            User user = newUser(role, active);
            session().persist(user);
            return user;
        });
    }

    @Test
    void loginWithCorrectEmailAndPasswordSucceeds() {
        User user = seedUser(UserRole.ADMIN, true);

        User result = authenticationService.login(user.getEmail(), REAL_PASSWORD, "127.0.0.1");

        assertEquals(user.getId(), result.getId());
    }

    @Test
    void loginWithWrongPasswordIsRejectedWithoutRevealingWhichPartWasWrong() {
        User user = seedUser(UserRole.ADMIN, true);

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authenticationService.login(user.getEmail(), "the-wrong-password", "127.0.0.1"));

        // Sec. 6's spirit (never help an attacker enumerate valid accounts):
        // the same message must cover both "no such account" and "wrong
        // password" - this assertion would fail if a future change leaked
        // which one it actually was.
        assertEquals("Invalid login ID or password.", ex.getMessage());
    }

    @Test
    void loginToADisabledAccountIsRejectedEvenWithTheCorrectPassword() {
        User user = seedUser(UserRole.TEACHER, false);

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authenticationService.login(user.getEmail(), REAL_PASSWORD, "127.0.0.1"));

        assertEquals("This account has been disabled. Contact your administrator.", ex.getMessage());
    }

    @Test
    void aStudentCanLogInWithTheirRollNumberInsteadOfTheirEmail() {
        String rollNo = unique("r").toLowerCase(); // deliberately lowercase - resolveUserByIdentifier must upper-case it
        User user = seed(() -> {
            Department dept = new Department("Physics", unique("PHY"));
            session().persist(dept);
            Course course = new Course(dept, "B.Sc Physics", unique("BSCPHY"), 6);
            session().persist(course);
            User studentUser = newUser(UserRole.STUDENT, true);
            session().persist(studentUser);
            session().persist(new Student(studentUser, rollNo.toUpperCase(), course));
            return studentUser;
        });

        User result = authenticationService.login(rollNo, REAL_PASSWORD, "127.0.0.1");

        assertEquals(user.getId(), result.getId());
    }

    @Test
    void loginForAnIdentifierThatMatchesNoAccountAtAllIsRejectedWithTheSameMessage() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authenticationService.login(unique("nobody") + "@example.edu", "anything", "127.0.0.1"));

        assertEquals("Invalid login ID or password.", ex.getMessage());
    }
}
