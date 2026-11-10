package com.intelliresult.nexus.support;

import com.intelliresult.nexus.config.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.function.Supplier;

/**
 * Base class for every Phase 17 integration test that exercises a real
 * Service against Hibernate. hibernate.cfg.xml uses
 * hibernate.current_session_context_class=managed, which means
 * HibernateUtil.getCurrentSession() (what every Service's own
 * inTransaction() helper calls) throws unless a Session has already been
 * bound to the current thread - normally HibernateSessionFilter's job on
 * every real HTTP request. This class does exactly what that filter does,
 * per test method instead of per request, so Services under test can be
 * called exactly as they are in production: no mocking of DAOs, no mocking
 * of Hibernate, no source changes to make any of this testable.
 *
 * Tests share one H2 in-memory database for the whole run (HibernateUtil's
 * SessionFactory is a static field, built once), so subclasses give every
 * row they create a unique identifying value (roll number, employee code,
 * email) - see the {@link #unique(String)} helper - rather than relying on
 * transaction rollback or a fresh schema per test.
 */
public abstract class IntegrationTestSupport {

    private Session session;

    @BeforeEach
    void bindSession() {
        session = HibernateUtil.getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @AfterEach
    void unbindSession() {
        ManagedSessionContext.unbind(HibernateUtil.getSessionFactory());
        if (session != null && session.isOpen()) {
            session.close();
        }
    }

    /** The Session bound for the current test method - for direct DAO/fixture setup that a Service call alone doesn't cover. */
    protected Session session() {
        return session;
    }

    /**
     * Runs fixture setup in its own committed transaction and returns what it built.
     * Outside a transaction persist() defers IDENTITY inserts (ids stay null) and flush()
     * throws TransactionRequiredException, while every Service opens its own transaction on
     * this same Session and Hibernate rejects nesting - so fixtures must commit before any
     * Service is called. Do not call seed() from inside another seed().
     */
    protected <T> T seed(Supplier<T> fixture) {
        Transaction tx = session.beginTransaction();
        try {
            T built = fixture.get();
            tx.commit();
            return built;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    /**
     * Appends a per-test-run unique suffix to a value that must not collide
     * with another test's row (roll numbers, employee codes, emails all have
     * unique constraints in schema.sql/the entity mappings). Using the
     * current nanoTime rather than a shared counter keeps this thread-safe
     * without any shared mutable state between test classes.
     */
    protected static String unique(String prefix) {
        return prefix + System.nanoTime();
    }
}
