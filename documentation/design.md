# Order Management API - Design Document

Status: draft. Sections 1-3 are complete. The remaining sections are filled in as the implementation progresses.

## 1. Understanding of the requirements

A small REST API, built with Java 17 and Spring Boot, for creating and managing orders.

- Three roles: `CUSTOMER`, `SUPPORT`, `ADMIN`.
- An order has one or more items. The server calculates the total.
- Status lifecycle: `CREATED -> CONFIRMED -> SHIPPED -> DELIVERED`.
- Persistence in PostgreSQL, with Liquibase migrations.
- Authentication and authorization are separate concerns. Identity and role are never taken from the request body.
- Deliverables: source code, unit and integration tests, Dockerfile, Docker Compose, GitLab CI pipeline, README, this document.

Priorities (from section 9): working core API, correct business rules and persistence, meaningful security, tests for critical behavior, documentation, basic CI/CD.

## 2. Clarifying questions and assumptions

The brief leaves some things open and allows reasonable assumptions as long as they are documented. These are the ones I made.

### Authentication and identity
- Users log in with `POST /api/auth/login`. The credentials are checked against a `users` table that stores BCrypt password hashes, and the API returns a short-lived JWT.
- Every other request is validated by Spring Security from that token. This keeps authentication (who you are) apart from authorization (what you may do).
- The user ID and role always come from the token, never from the request body.
- The signing secret is read from an environment variable and is never committed.
- Sample users exist only in the `dev` and `test` setups, with obviously fake passwords. Production has none, so the dev setup can't be mistaken for real authentication.
- There is no separate customer entity. An order's customer ID is simply the logged-in user's ID.

### Money and order data
- Prices and totals use `BigDecimal` in Java and `NUMERIC(12,2)` in PostgreSQL, because `double` can't represent decimal amounts exactly. One currency is assumed, rounding is `HALF_UP`.
- The client sends `unitPrice` (as in the brief's example) because there is no product catalog. It is validated, and the total is always calculated on the server. A client-supplied total is ignored. A real system would look prices up in a catalog, so this is listed as a limitation.
- Input limits protect the service: at most 100 items per order, quantity 1 to 1000, unit price 0.01 to 99,999,999.99.
- The same product may appear on several lines. Each line is stored as sent.

### Roles and access
- **CUSTOMER** can create orders and read only their own.
- **SUPPORT** can read any order and update its status, but cannot create orders.
- **ADMIN** can do everything SUPPORT can, create orders (assigned to the admin's own ID), and delete orders. Delete is a hard delete.
- A customer who requests someone else's order gets `404`, not `403`, so order IDs can't be probed. Role-based denials, such as a customer calling the status endpoint, still return `403`.

### Status transitions
- Only forward and one step at a time: `CREATED -> CONFIRMED -> SHIPPED -> DELIVERED`. No skipping, no going back, no cancellation.
- SUPPORT and ADMIN may perform all three transitions. CUSTOMER may perform none. ADMIN does not bypass the transition rules.
- An invalid transition returns `409 Conflict`, because the request is valid but doesn't fit the order's current state.
- Concurrent updates are handled with optimistic locking (a `version` column). A losing update also returns `409`.

### API conventions
- Paths are lowercase: `/api/orders`.
- All errors use one application-defined JSON body (an `ErrorResponse` DTO, content type `application/json`), produced by a single `@RestControllerAdvice`. Every error case, including 401 and 403 from Spring Security, returns the same shape:
  - `status`: the HTTP status code.
  - `error`: a stable machine-readable code such as `VALIDATION_FAILED`, `ORDER_NOT_FOUND` or `INVALID_STATUS_TRANSITION`. Clients branch on this, not on the message.
  - `message`: a short human-readable explanation that never contains internal details.
  - `timestamp`: when the error occurred (UTC, ISO-8601).
  - `path`: the request path.
  - `fieldErrors`: optional list of `{field, message}` for validation failures. Omitted otherwise.
- Why a custom DTO instead of RFC 7807 Problem Details (`application/problem+json`)? It is a plain class that the team fully controls and can extend easily, and there is no dependency on a particular RFC or on Spring's `ProblemDetail` type. Both options work with a global exception handler, so the choice is about simplicity and maintainability. The trade-off is that the format is not a standard, so it must be documented for API consumers (see section 5), and framework exceptions such as malformed JSON or unsupported methods must be mapped explicitly in the handler.
- Paging uses `page` (starting at 0), `size` and `sort`. The default size is 20, the maximum is 100 (larger values are capped), and the default sort is `createdAt,desc`.
- Sorting is limited to `createdAt`, `updatedAt`, `status` and `totalAmount`. Any other field returns `400`.

### Testing and operations
- Integration tests use Testcontainers with the same PostgreSQL version as production, not H2, to avoid dialect differences. This means Docker must be running.
- The Actuator `health` endpoint is exposed for Docker Compose and deployment checks. Other actuator endpoints are disabled.

### API documentation
- The API is documented with OpenAPI 3, generated from the code by `springdoc-openapi`. Swagger UI is served at `/swagger-ui.html` and the raw spec at `/v3/api-docs`.
- Controllers and DTOs carry annotations (`@Operation`, `@ApiResponse`, `@Schema`) so that each endpoint lists its request and response bodies, the `ErrorResponse` shape and the possible status codes (400, 401, 403, 404, 409).
- JWT bearer authentication is declared as a security scheme, so a reviewer can log in through `POST /api/auth/login`, paste the token into the Swagger UI "Authorize" dialog, and try the endpoints.
- Swagger UI and `/v3/api-docs` are enabled in the `dev` and `test` setups only. They are disabled by default in production, because they reveal the full API surface.
- Generating the docs from code keeps them in sync with the implementation. The curated examples in section 5 and in the README remain the human-readable overview.

### Deliberately left out
- Idempotent order creation. Out of scope for a time-boxed assignment, noted as a future improvement.
  - **What it is:** an operation is idempotent if repeating the same request has the same effect as sending it once. `GET`, `PUT` and `DELETE` are idempotent by definition. `POST /api/orders` is not: sending it twice creates two orders.
  - **Why it matters:** a client that times out or loses its connection can't tell whether the order was created, so it retries. Without protection this produces duplicate orders, and a customer could be charged or shipped twice.
  - **How it would work:** the client sends a unique `Idempotency-Key` header (for example a UUID) with each create request. The server stores the key with the resulting order. If the same key arrives again, the server returns the original response instead of creating a new order. A key reused with a different body is rejected.
  - **What it would need:** a table or column with a unique constraint on (user ID, key), plus a retention period for old keys.

## 3. Role and permission matrix

| Operation | Description | Endpoint | Method | CUSTOMER | SUPPORT | ADMIN | Anonymous |
|-----------|-------------|----------|--------|----------|---------|-------|-----------|
| login | Exchange credentials for a JWT | `/api/auth/login` | POST | yes | yes | yes | yes |
| createOrder | Create an order for the logged-in user | `/api/orders` | POST | yes (own) | no (403) | yes (own) | 401 |
| getOrder | Retrieve one order by ID | `/api/orders/{id}` | GET | own only (else 404) | any | any | 401 |
| getMyOrders | Paged list of the caller's own orders | `/api/orders/my` | GET | yes | yes | yes | 401 |
| getAllOrders | Paged list of all orders | `/api/orders` | GET | no (403) | yes | yes | 401 |
| updateOrderStatus | Move an order to its next status | `/api/orders/{id}/status` | PATCH | no (403) | yes | yes | 401 |
| deleteOrder | Permanently delete an order | `/api/orders/{id}` | DELETE | no (403) | no (403) | yes | 401 |
| health | Liveness and readiness check | `/actuator/health` | GET | yes | yes | yes | yes |
| apiDocs | OpenAPI spec and Swagger UI (dev and test only) | `/v3/api-docs`, `/swagger-ui.html` | GET | yes | yes | yes | yes (dev/test), 404 in prod |

Orders are always assigned to the authenticated user. Creating orders on behalf of another customer is out of scope. SUPPORT cannot create orders.

### Status transitions

| From | To | Allowed roles |
|------|----|---------------|
| CREATED | CONFIRMED | SUPPORT, ADMIN |
| CONFIRMED | SHIPPED | SUPPORT, ADMIN |
| SHIPPED | DELIVERED | SUPPORT, ADMIN |
| any other combination | - | rejected with 409 |

## 4. Architecture
TODO.

## 5. API design
TODO. Include sample requests and responses for each endpoint, and point to the generated OpenAPI spec and Swagger UI (see "API documentation" in section 2).

## 6. Data model
TODO.

## 7. Authentication and authorization
TODO. Must state clearly that the dev token setup is not production-ready authentication.

## 8. Error handling and transaction boundaries
TODO. Describe the `ErrorResponse` DTO, the single `@RestControllerAdvice`, and the Spring Security entry point and access-denied handler that reuse the same DTO.

## 9. Design decisions and trade-offs
TODO.

## 10. Known limitations and future improvements
Known so far: client-supplied unit prices, no idempotency, no cancellation, shared-secret JWT instead of an external identity provider, no rate limiting.
