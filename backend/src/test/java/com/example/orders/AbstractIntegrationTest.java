package com.example.orders;

import com.example.orders.domain.Role;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need the whole application and a real PostgreSQL. One container is shared by
 * all test classes, so the Spring context and the database are started once per test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Inserts a new user so each test can work with its own customers and not depend on other tests.
     * The password hash is a placeholder: these users never log in.
     */
    protected UUID createUser(Role role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, username, password_hash, role, created_at) VALUES (?, ?, 'unused', ?, now())",
                id,
                "test-" + id,
                role.name());
        return id;
    }
}
