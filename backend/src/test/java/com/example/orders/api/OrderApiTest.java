package com.example.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orders.AbstractIntegrationTest;
import com.example.orders.domain.Role;
import com.example.orders.security.CurrentUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HTTP-level tests of the order endpoints. Tokens are simulated with the {@code jwt()} test helper, so
 * these tests check what the controllers do with a valid identity. Login and the real security filter
 * chain (401 handling) come with the security step.
 */
class OrderApiTest extends AbstractIntegrationTest {

    private static final String VALID_ORDER = """
            {"items": [
              {"productId": "PROD-101", "productName": "Keyboard", "quantity": 2, "unitPrice": 49.99},
              {"productId": "PROD-102", "productName": "Mouse", "quantity": 1, "unitPrice": 19.99}
            ]}""";

    @Autowired
    MockMvc mvc;

    private static RequestPostProcessor tokenFor(UUID userId, Role role) {
        return jwt().jwt(j -> j.subject(userId.toString()).claim(CurrentUser.ROLE_CLAIM, role.name()));
    }

    private static String orderWithItem(String quantity, String unitPrice) {
        return """
                {"items": [{"productId": "P", "productName": "Thing", "quantity": %s, "unitPrice": %s}]}"""
                .formatted(quantity, unitPrice);
    }

    private String createOrder(UUID customerId) throws Exception {
        MvcResult result = mvc.perform(post("/api/orders")
                        .with(csrf())
                        .with(tokenFor(customerId, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    // --- create ---

    @Test
    void createReturns201WithLocationAndCalculatedTotal() throws Exception {
        UUID customerId = createUser(Role.CUSTOMER);

        mvc.perform(post("/api/orders")
                        .with(csrf())
                        .with(tokenFor(customerId, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/api/orders/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(119.97))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].lineTotal").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void createIgnoresClientSuppliedTotalCustomerIdAndStatus() throws Exception {
        UUID customerId = createUser(Role.CUSTOMER);
        String body = """
                {"customerId": "%s", "status": "DELIVERED", "totalAmount": 0.01,
                 "items": [{"productId": "P", "productName": "Thing", "quantity": 3, "unitPrice": 0.10}]}"""
                .formatted(UUID.randomUUID());

        mvc.perform(post("/api/orders")
                        .with(csrf())
                        .with(tokenFor(customerId, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(0.30));
    }

    @Test
    void supportCannotCreateOrders() throws Exception {
        mvc.perform(post("/api/orders")
                        .with(csrf())
                        .with(tokenFor(createUser(Role.SUPPORT), Role.SUPPORT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAcceptsTheLargestAllowedValues() throws Exception {
        mvc.perform(post("/api/orders")
                        .with(csrf())
                        .with(tokenFor(createUser(Role.CUSTOMER), Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderWithItem("1000", "99999999.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(99999999990.00));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"items\": []}",
        "{\"items\": [null]}",
        "{\"items\": [{\"productName\": \"Thing\", \"quantity\": 1, \"unitPrice\": 1.00}]}",
        "{\"items\": [{\"productId\": \" \", \"productName\": \"Thing\", \"quantity\": 1, \"unitPrice\": 1.00}]}",
        "{\"items\": [{\"productId\": \"P\", \"quantity\": 1, \"unitPrice\": 1.00}]}",
        "{\"items\": [{\"productId\": \"P\", \"productName\": \"Thing\", \"unitPrice\": 1.00}]}",
        "{\"items\": [{\"productId\": \"P\", \"productName\": \"Thing\", \"quantity\": 1}]}"
    })
    void createRejectsMissingFields(String body) throws Exception {
        assertCreateRejected(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1001"})
    void createRejectsQuantityOutsideLimits(String quantity) throws Exception {
        assertCreateRejected(orderWithItem(quantity, "1.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5.00", "100000000.00", "1.999", "1.005"})
    void createRejectsPriceOutsideLimitsOrWithTooManyDecimals(String price) throws Exception {
        assertCreateRejected(orderWithItem("1", price));
    }

    @Test
    void createRejectsMoreThan100Items() throws Exception {
        String item = "{\"productId\": \"P\", \"productName\": \"Thing\", \"quantity\": 1, \"unitPrice\": 1.00}";
        String body = "{\"items\": [" + String.join(",", java.util.Collections.nCopies(101, item)) + "]}";

        assertCreateRejected(body);
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        assertCreateRejected("{\"items\": [");
    }

    private void assertCreateRejected(String body) throws Exception {
        mvc.perform(post("/api/orders")
                        .with(csrf())
                        .with(tokenFor(createUser(Role.CUSTOMER), Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // --- read ---

    @Test
    void customerCanReadOwnOrderThroughLocation() throws Exception {
        UUID customerId = createUser(Role.CUSTOMER);
        String orderId = createOrder(customerId);

        mvc.perform(get("/api/orders/{id}", orderId).with(tokenFor(customerId, Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.totalAmount").value(119.97))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void supportCanReadAnyOrder() throws Exception {
        String orderId = createOrder(createUser(Role.CUSTOMER));

        mvc.perform(get("/api/orders/{id}", orderId).with(tokenFor(createUser(Role.SUPPORT), Role.SUPPORT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
    }

    @Test
    void myOrdersListsOnlyTheCallersOrdersInAPageEnvelope() throws Exception {
        UUID customerId = createUser(Role.CUSTOMER);
        createOrder(customerId);
        createOrder(customerId);
        createOrder(createUser(Role.CUSTOMER));

        mvc.perform(get("/api/orders/my").with(tokenFor(customerId, Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].customerId", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(customerId.toString()))))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void myOrdersSupportsPagingAndDefaultsToNewestFirst() throws Exception {
        UUID customerId = createUser(Role.CUSTOMER);
        createOrder(customerId);
        createOrder(customerId);
        String newest = createOrder(customerId);

        mvc.perform(get("/api/orders/my").param("size", "2").with(tokenFor(customerId, Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(newest))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void pageSizeIsCappedAt100() throws Exception {
        mvc.perform(get("/api/orders/my")
                        .param("size", "5000")
                        .with(tokenFor(createUser(Role.CUSTOMER), Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void customerCannotListAllOrders() throws Exception {
        mvc.perform(get("/api/orders").with(tokenFor(createUser(Role.CUSTOMER), Role.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportAndAdminCanListAllOrders() throws Exception {
        createOrder(createUser(Role.CUSTOMER));

        for (Role role : new Role[] {Role.SUPPORT, Role.ADMIN}) {
            mvc.perform(get("/api/orders").with(tokenFor(createUser(role), role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }
    }

    // --- status ---

    @Test
    void supportCanConfirmAnOrder() throws Exception {
        String orderId = createOrder(createUser(Role.CUSTOMER));

        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .with(csrf())
                        .with(tokenFor(createUser(Role.SUPPORT), Role.SUPPORT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void customerCannotChangeStatus() throws Exception {
        UUID customerId = createUser(Role.CUSTOMER);
        String orderId = createOrder(customerId);

        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .with(csrf())
                        .with(tokenFor(customerId, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\": null}", "{\"status\": \"CANCELLED\"}", "{\"status\": \"confirmed\"}"})
    void statusUpdateRejectsMissingOrUnknownStatus(String body) throws Exception {
        String orderId = createOrder(createUser(Role.CUSTOMER));

        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .with(csrf())
                        .with(tokenFor(createUser(Role.SUPPORT), Role.SUPPORT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // --- delete ---

    @Test
    void adminCanDeleteAndTheOrderIsGone() throws Exception {
        UUID customerId = createUser(Role.CUSTOMER);
        String orderId = createOrder(customerId);

        mvc.perform(delete("/api/orders/{id}", orderId)
                        .with(csrf())
                        .with(tokenFor(createUser(Role.ADMIN), Role.ADMIN)))
                .andExpect(status().isNoContent());

        Integer remaining = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE id = ?::uuid", Integer.class, orderId);
        assertThat(remaining).isZero();
    }

    @Test
    void supportCannotDelete() throws Exception {
        String orderId = createOrder(createUser(Role.CUSTOMER));

        mvc.perform(delete("/api/orders/{id}", orderId)
                        .with(csrf())
                        .with(tokenFor(createUser(Role.SUPPORT), Role.SUPPORT)))
                .andExpect(status().isForbidden());
    }
}
