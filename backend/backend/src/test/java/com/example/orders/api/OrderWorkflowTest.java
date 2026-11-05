package com.example.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orders.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * One scenario through the public API only, with the real sample users and real logins (no database
 * shortcuts): the way a client would use the service.
 */
class OrderWorkflowTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + username + "\", \"password\": \"dev-password-1\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + JsonPath.<String>read(body, "$.accessToken");
    }

    @Test
    void customerOrdersSupportProcessesAdminDeletes() throws Exception {
        String customer = login("customer1");
        String otherCustomer = login("customer2");
        String support = login("support1");
        String admin = login("admin1");

        // The customer places an order and finds it again.
        String created = mvc.perform(post("/api/orders")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [
                                  {"productId": "PROD-101", "productName": "Keyboard", "quantity": 2, "unitPrice": 49.99},
                                  {"productId": "PROD-102", "productName": "Mouse", "quantity": 1, "unitPrice": 19.99}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.totalAmount").value(119.97))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));
        mvc.perform(get("/api/orders/my").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + orderId + "')]", hasSize(1)));

        // Another customer cannot see it, and the customer cannot process it.
        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", otherCustomer))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isForbidden());

        // Support sees it and moves it along. Skipping a step is a conflict.
        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", support)).andExpect(status().isOk());
        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", support)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SHIPPED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", support)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Support cannot delete, an admin can, and afterwards the order is gone for everyone.
        mvc.perform(delete("/api/orders/{id}", orderId).header("Authorization", support))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/orders/{id}", orderId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", customer))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id = ?::uuid", Integer.class, orderId))
                .isZero();
    }
}
