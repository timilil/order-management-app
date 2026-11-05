package com.example.orders.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orders.AbstractIntegrationTest;
import com.example.orders.domain.Role;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Login, logout, session validation, role rules ({@code @PreAuthorize}) and the 401 and 403 responses, all
 * through the real security filter chain.
 */
class SecurityTest extends AbstractIntegrationTest {

    private static final String SEED_PASSWORD = "dev-password-1";
    private static final String SOME_ORDER_ID = "5b0a3c1e-8f0e-4a43-9d58-0c8e6d2f7a11";

    /** Valid for creating an order and for changing a status, so that only the role decides the outcome. */
    private static final String ANY_VALID_BODY = """
            {"status": "CONFIRMED",
             "items": [{"productId": "P", "productName": "T", "quantity": 1, "unitPrice": 1.00}]}""";

    @Autowired
    MockMvc mvc;

    // --- helpers ---

    private static String loginBody(String username, String password) {
        return "{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(username, password)));
    }

    private String loginToken(String username) throws Exception {
        return JsonPath.read(
                login(username, SEED_PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.accessToken");
    }

    private ResultActions getMyOrdersWith(String authorizationHeader) throws Exception {
        return mvc.perform(get("/api/orders/my").header("Authorization", authorizationHeader));
    }

    private ResultActions getMyOrders(String token) throws Exception {
        return getMyOrdersWith("Bearer " + token);
    }

    private ResultActions logout(String token) throws Exception {
        return mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token));
    }

    private void insertSession(String token, UUID userId, Instant expiresAt) {
        jdbc.update(
                "INSERT INTO sessions (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                SessionStore.sessionId(token),
                userId,
                Timestamp.from(Instant.now()),
                Timestamp.from(expiresAt));
    }

    private int sessionRows(String token) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM sessions WHERE token_hash = ?", Integer.class, SessionStore.sessionId(token));
    }

    private static void assertUnauthorizedBody(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.path").value("/api/orders/my"));
    }

    // --- login ---

    @ParameterizedTest
    @CsvSource({
        "customer1, 11111111-1111-1111-1111-111111111111",
        "customer2, 22222222-2222-2222-2222-222222222222",
        "support1,  33333333-3333-3333-3333-333333333333",
        "admin1,    44444444-4444-4444-4444-444444444444"
    })
    void loginStartsASessionForTheUser(String username, String userId) throws Exception {
        MvcResult result = login(username, SEED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andReturn();

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        UUID sessionUser = jdbc.queryForObject(
                "SELECT user_id FROM sessions WHERE token_hash = ?", UUID.class, SessionStore.sessionId(token));
        assertThat(sessionUser).isEqualTo(UUID.fromString(userId));
    }

    @Test
    void tokenIsRandomOpaqueAndOnlyItsHashIsStored() throws Exception {
        String first = loginToken("customer1");
        String second = loginToken("customer1");

        assertThat(first).isNotEqualTo(second).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first).doesNotContain(".");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions WHERE token_hash = ?", Integer.class, first))
                .isZero();
        assertThat(sessionRows(first)).isEqualTo(1);
    }

    @Test
    void tokenFromLoginWorksOnProtectedEndpoints() throws Exception {
        getMyOrders(loginToken("customer1")).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSame401() throws Exception {
        String wrongPassword = login("customer1", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String unknownUser = login("nobody", SEED_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.error")).isEqualTo("UNAUTHORIZED");
        assertThat(JsonPath.<String>read(unknownUser, "$.error")).isEqualTo("UNAUTHORIZED");
        assertThat(JsonPath.<String>read(wrongPassword, "$.message"))
                .isEqualTo(JsonPath.<String>read(unknownUser, "$.message"));
        assertThat(wrongPassword).doesNotContain("customer1").doesNotContain("$2");
    }

    @Test
    void failedLoginDoesNotCreateASession() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class);

        login("customer1", "wrong-password").andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class)).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource(value = {"'', x", "x, ''", "'   ', x"})
    void loginRejectsBlankCredentials(String username, String password) throws Exception {
        login(username, password).andExpect(status().isBadRequest());
    }

    @Test
    void loginRejectsMissingBodyAndOverlongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        login("customer1", "x".repeat(73)).andExpect(status().isBadRequest());
    }

    @Test
    void expiredSessionsAreRemovedOnLaterLogins() throws Exception {
        String stale = "stale-token-for-cleanup-test";
        insertSession(stale, UUID.fromString("11111111-1111-1111-1111-111111111111"), Instant.now().minusSeconds(60));
        assertThat(sessionRows(stale)).isEqualTo(1);

        loginToken("customer1");

        assertThat(sessionRows(stale)).isZero();
    }

    // --- 401 ---

    @Test
    void requestWithoutTokenGets401WithTheStandardErrorBody() throws Exception {
        assertUnauthorizedBody(mvc.perform(get("/api/orders/my")));
    }

    @ParameterizedTest
    @CsvSource({
        "GET,    /api/orders",
        "GET,    /api/orders/my",
        "GET,    /api/orders/" + SOME_ORDER_ID,
        "POST,   /api/orders",
        "PATCH,  /api/orders/" + SOME_ORDER_ID + "/status",
        "DELETE, /api/orders/" + SOME_ORDER_ID,
        "POST,   /api/auth/logout"
    })
    void everyProtectedEndpointRequiresAToken(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void garbageTokenGets401() throws Exception {
        assertUnauthorizedBody(getMyOrdersWith("Bearer not-a-session"));
    }

    @Test
    void nonBearerSchemeGets401() throws Exception {
        assertUnauthorizedBody(getMyOrdersWith("Basic " + Base64.getEncoder().encodeToString("customer1:x".getBytes())));
    }

    @Test
    void wellFormedTokenThatIsNotASessionGets401() throws Exception {
        assertUnauthorizedBody(getMyOrders(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32])));
    }

    @Test
    void jwtShapedTokenGets401() throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"sub\":\"44444444-4444-4444-4444-444444444444\",\"role\":\"ADMIN\"}".getBytes());

        assertUnauthorizedBody(getMyOrders(header + "." + payload + "."));
    }

    @Test
    void storedHashIsNotAcceptedAsAToken() throws Exception {
        String token = sessionToken(createUser(Role.CUSTOMER));

        assertUnauthorizedBody(getMyOrders(SessionStore.sessionId(token)));
    }

    @Test
    void expiredSessionGets401() throws Exception {
        String token = "expired-session-token";
        insertSession(token, createUser(Role.CUSTOMER), Instant.now().minusSeconds(1));

        assertUnauthorizedBody(getMyOrders(token));
    }

    @Test
    void sessionIsGoneWhenItsUserIsDeleted() throws Exception {
        UUID userId = createUser(Role.CUSTOMER);
        String token = sessionToken(userId);
        getMyOrders(token).andExpect(status().isOk());

        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        assertUnauthorizedBody(getMyOrders(token));
    }

    // --- the user is read from the database on every request ---

    @Test
    void roleChangeAppliesToExistingSessionsAtOnce() throws Exception {
        UUID userId = createUser(Role.CUSTOMER);
        String token = sessionToken(userId);
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        jdbc.update("UPDATE users SET role = 'SUPPORT' WHERE id = ?", userId);

        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // --- 403 and @PreAuthorize ---

    @Test
    void forbiddenResponseUsesTheStandardErrorBodyWithoutInternals() throws Exception {
        String token = sessionToken(createUser(Role.CUSTOMER));

        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("springframework"))));
    }

    @ParameterizedTest
    @CsvSource({
        "CUSTOMER, GET,    /api/orders",
        "SUPPORT,  POST,   /api/orders",
        "CUSTOMER, PATCH,  /api/orders/" + SOME_ORDER_ID + "/status",
        "CUSTOMER, DELETE, /api/orders/" + SOME_ORDER_ID,
        "SUPPORT,  DELETE, /api/orders/" + SOME_ORDER_ID
    })
    void deniedRolesGet403(String role, String method, String path) throws Exception {
        String token = sessionToken(createUser(Role.valueOf(role)));

        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANY_VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @CsvSource({
        "CUSTOMER, POST,   /api/orders,                                  201",
        "ADMIN,    POST,   /api/orders,                                  201",
        "SUPPORT,  GET,    /api/orders,                                  200",
        "ADMIN,    GET,    /api/orders,                                  200",
        "CUSTOMER, GET,    /api/orders/my,                               200",
        "SUPPORT,  GET,    /api/orders/my,                               200",
        "ADMIN,    GET,    /api/orders/my,                               200"
    })
    void permittedRolesPassSecurity(String role, String method, String path, int expectedStatus) throws Exception {
        String token = sessionToken(createUser(Role.valueOf(role)));

        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANY_VALID_BODY))
                .andExpect(status().is(expectedStatus));
    }

    private String createOrderAsNewCustomer() throws Exception {
        String token = sessionToken(createUser(Role.CUSTOMER));
        String body = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANY_VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @ParameterizedTest
    @CsvSource({"SUPPORT", "ADMIN"})
    void supportAndAdminMayChangeStatus(String role) throws Exception {
        String orderId = createOrderAsNewCustomer();
        String token = sessionToken(createUser(Role.valueOf(role)));

        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void onlyAdminMayDelete() throws Exception {
        String orderId = createOrderAsNewCustomer();
        String support = sessionToken(createUser(Role.SUPPORT));
        String admin = sessionToken(createUser(Role.ADMIN));

        mvc.perform(delete("/api/orders/{id}", orderId).header("Authorization", "Bearer " + support))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/orders/{id}", orderId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    // --- public endpoints and stateless behaviour ---

    @Test
    void healthIsPublicAndShowsNoDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").header("Authorization", "Bearer " + sessionToken(createUser(Role.ADMIN))))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiDocsArePublicInTheTestSetupAndDeclareBearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout']").exists())
                .andExpect(jsonPath("$.paths['/api/orders']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void authenticatedRequestsDoNotCreateHttpSessionsOrCookies() throws Exception {
        String token = sessionToken(createUser(Role.CUSTOMER));

        MvcResult result = getMyOrders(token)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void postWithoutCsrfTokenIsAcceptedBecauseTokensAreNotCookies() throws Exception {
        String token = sessionToken(createUser(Role.CUSTOMER));

        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANY_VALID_BODY))
                .andExpect(status().isCreated());
    }

    // --- logout ---

    @Test
    void loggedOutTokenIsRejectedAfterwards() throws Exception {
        String token = sessionToken(createUser(Role.CUSTOMER));
        getMyOrders(token).andExpect(status().isOk());

        logout(token).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertUnauthorizedBody(getMyOrders(token));
        assertThat(sessionRows(token)).isZero();
    }

    @Test
    void loggingOutTwiceGivesUnauthorizedTheSecondTime() throws Exception {
        String token = sessionToken(createUser(Role.CUSTOMER));
        logout(token).andExpect(status().isNoContent());

        logout(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void loggingOutOneSessionDoesNotAffectAnotherSessionOfTheSameUser() throws Exception {
        UUID userId = createUser(Role.CUSTOMER);
        String first = sessionToken(userId);
        String second = sessionToken(userId);

        logout(first).andExpect(status().isNoContent());

        getMyOrders(second).andExpect(status().isOk());
    }

    @Test
    void loginAfterLogoutGivesAFreshWorkingToken() throws Exception {
        String first = loginToken("customer1");
        logout(first).andExpect(status().isNoContent());

        String second = loginToken("customer1");

        assertThat(second).isNotEqualTo(first);
        getMyOrders(second).andExpect(status().isOk());
    }
}
