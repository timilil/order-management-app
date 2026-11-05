package com.example.orders.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orders.AbstractIntegrationTest;
import com.example.orders.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Every error response has the {@link ErrorResponse} shape and none of them leaks internals. */
class ErrorHandlingTest extends AbstractIntegrationTest {

    private static final String VALID_ORDER =
            """
            {"items": [{"productId": "P", "productName": "Thing", "quantity": 1, "unitPrice": 10.00}]}""";

    @Autowired
    MockMvc mvc;

    private RequestPostProcessor tokenFor(UUID userId) {
        String token = sessionToken(userId);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private String createOrder(UUID customerId) throws Exception {
        MvcResult result = mvc.perform(post("/api/orders")
                        .with(tokenFor(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void validationErrorListsTheFieldsAndOnlyThose() throws Exception {
        String body = """
                {"items": [{"productId": "", "productName": "Thing", "quantity": 0, "unitPrice": 10.00}]}""";

        mvc.perform(post("/api/orders")
                        .with(tokenFor(createUser(Role.CUSTOMER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(containsString("quantity"))))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(containsString("productId"))));
    }

    @Test
    void malformedJsonDoesNotShowParserDetails() throws Exception {
        mvc.perform(post("/api/orders")
                        .with(tokenFor(createUser(Role.CUSTOMER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [ "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message", not(containsString("Jackson"))))
                .andExpect(jsonPath("$.message", not(containsString("com.example"))))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void unknownStatusValueIsABadRequest() throws Exception {
        String orderId = createOrder(createUser(Role.CUSTOMER));

        mvc.perform(patch("/api/orders/" + orderId + "/status")
                        .with(tokenFor(createUser(Role.SUPPORT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"TELEPORTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void malformedOrderIdIsABadRequestNamingTheParameter() throws Exception {
        mvc.perform(get("/api/orders/not-a-uuid").with(tokenFor(createUser(Role.SUPPORT))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value."));
    }

    @Test
    void unknownSortFieldIsABadRequest() throws Exception {
        mvc.perform(get("/api/orders/my?sort=passwordHash,asc").with(tokenFor(createUser(Role.CUSTOMER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", startsWith("Cannot sort by 'passwordHash'")));
    }

    @Test
    void missingOrderIs404() throws Exception {
        String id = UUID.randomUUID().toString();

        mvc.perform(get("/api/orders/" + id).with(tokenFor(createUser(Role.SUPPORT))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found."))
                .andExpect(jsonPath("$.path").value("/api/orders/" + id));
    }

    @Test
    void anotherCustomersOrderLooksExactlyLikeAMissingOne() throws Exception {
        String orderId = createOrder(createUser(Role.CUSTOMER));
        UUID stranger = createUser(Role.CUSTOMER);

        String foreign = mvc.perform(get("/api/orders/" + orderId).with(tokenFor(stranger)))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mvc.perform(get("/api/orders/" + UUID.randomUUID()).with(tokenFor(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found."));
        org.assertj.core.api.Assertions.assertThat(foreign).contains("ORDER_NOT_FOUND").contains("Order not found.");
    }

    @Test
    void invalidStatusTransitionIs409() throws Exception {
        String orderId = createOrder(createUser(Role.CUSTOMER));

        mvc.perform(patch("/api/orders/" + orderId + "/status")
                        .with(tokenFor(createUser(Role.SUPPORT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"DELIVERED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Cannot change order status from CREATED to DELIVERED"));
    }

    @Test
    void roleDeniedByPreAuthorizeUsesTheStandardShape() throws Exception {
        mvc.perform(get("/api/orders").with(tokenFor(createUser(Role.CUSTOMER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/orders"));
    }

    @Test
    void unsupportedMethodIs405WithAllowHeader() throws Exception {
        mvc.perform(put("/api/orders/" + UUID.randomUUID()).with(tokenFor(createUser(Role.SUPPORT))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        mvc.perform(post("/api/orders")
                        .with(tokenFor(createUser(Role.CUSTOMER)))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unknownPathIs404InTheSameShape() throws Exception {
        mvc.perform(get("/api/nothing-here").with(tokenFor(createUser(Role.CUSTOMER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/nothing-here"));
    }

    @Test
    void missingTokenIs401InTheSameShape() throws Exception {
        mvc.perform(get("/api/orders/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}
