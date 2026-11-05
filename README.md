# Order Management API

A small REST API for creating and managing orders, built with Java 17 and Spring Boot 3.5. Customers create and view their own orders, support staff view all orders and move them through their lifecycle, and admins can additionally delete orders. Data is stored in PostgreSQL, with schema changes managed by Liquibase.

> **Status: work in progress.** The database layer, project setup, the domain model, the order endpoints and security (login, sessions, logout, role rules with `@PreAuthorize`, 401 and 403 responses) are done. Error responses use one JSON format everywhere. The container image and the CI pipeline are not implemented yet. Sections marked *(coming)* describe what will be added.

The design, assumptions and trade-offs are in [documentation/design.md](documentation/design.md).

## Prerequisites

- Java 17
- Docker (for the local database and for the Testcontainers-based tests)
- Nothing else to install: Maven is provided by the wrapper (`./mvnw`)

## Project layout

| Path | Contents |
|------|----------|
| `backend/` | Spring Boot application (Maven project). Code is in `com.example.orders`, packaged by layer: `api`, `service`, `domain`, `repository`, `security`, `config`, `error`. |
| `docker/` | Docker Compose file and environment template for the local database |
| `documentation/` | Design document |

## Start the database

The database credentials are local-only fake values. Copy the template once, then start PostgreSQL:

```bash
cp docker/.env.example docker/.env
docker compose -f docker/docker-compose.yml --env-file docker/.env up -d
```

PostgreSQL listens on `127.0.0.1:5432`. Data is kept in a Docker volume, so it survives restarts and `docker compose ... down`. To delete the data as well, run `docker compose -f docker/docker-compose.yml --env-file docker/.env down -v`.

## Run the application

With the database running:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile uses fake local defaults that match `docker/.env.example` and enables the API documentation. On startup, Liquibase creates the tables and the sample users.

### Configuration

Outside the `dev` profile there are no defaults, and the application refuses to start unless these environment variables are set:

| Variable | Purpose |
|----------|---------|
| `DB_URL` | JDBC URL, for example `jdbc:postgresql://localhost:5432/orders` |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |

Never commit real values. `docker/.env` is ignored by git.

### Sample users (dev and test only)

These fake users are created only when the Liquibase context is `dev` or `test`. Production runs use the `prod` context and have no users. All four share the password `dev-password-1`.

| Username | Role |
|----------|------|
| `customer1` | CUSTOMER |
| `customer2` | CUSTOMER |
| `support1` | SUPPORT |
| `admin1` | ADMIN |

## Run the tests

Docker must be running, because the tests start a real PostgreSQL 16 container with Testcontainers.

```bash
cd backend
./mvnw test
```

Current tests cover:

- **Unit tests (no Spring, no database):** order total calculation, item validation (quantity, price, decimals), and every valid and invalid status transition.
- **Database tests (Testcontainers):** the application starts, Hibernate accepts the entity mappings, and the Liquibase migrations create the expected schema and enforce its constraints (foreign keys, `CHECK` constraints, cascade delete).
- **Service tests (Testcontainers):** order creation and persistence, ownership (another customer's order is "not found"), role rules, paging and sorting, status changes and delete.
- **API tests (MockMvc and Testcontainers):** the order endpoints over HTTP with real sessions, including the `201` and `Location` header, ignored client totals, input validation (`400`), role denials (`403`), what is stored in `orders` and `order_items`, another customer's order (`404`, never listed, never changed), the whole status lifecycle and rejected transitions (`409`).
- **Workflow test (real logins, public API only):** a customer places and reads an order, another customer cannot see it, support processes it, support cannot delete it, an admin deletes it and its items.
- **Security tests (MockMvc and Testcontainers):** login with each sample user, only a hash of the token in the database, the same `401` for a wrong password and an unknown user, and `401` for missing, malformed, unknown, expired and logged-out tokens, JWT-shaped tokens, and sessions of deleted users. They also check that a role change applies at once, the `403` body, the permitted and denied roles for each endpoint (`@PreAuthorize`), logout (other sessions of the same user keep working), the public endpoints, and that no HTTP session or cookie is created.
- **Error handling tests (MockMvc and Testcontainers):** every error case returns the standard JSON body and leaks no internals: validation (`400` with field errors), malformed JSON, bad IDs and sort fields, a missing order or another customer's order (`404`), an invalid status change (`409`), role denial (`403`), missing token (`401`), and framework errors (`405`, `415`, unknown path). Unit tests cover the lost-update (`409`) and unexpected-error (`500`) handlers.
- **Unit tests:** session settings, the session ID (SHA-256 of the token), and reading the caller from the authenticated user.

All Testcontainers-based tests share one PostgreSQL container, so Docker is started once per test run.

## Inspect the database

```bash
docker exec orders-postgres psql -U orders_dev -d orders -c '\dt'
```

## API usage *(more examples coming)*

With the application running in the `dev` profile, log in with a sample user to get a session token, then send it as a bearer token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username": "customer1", "password": "dev-password-1"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s http://localhost:8080/api/orders/my -H "Authorization: Bearer $TOKEN"
```

The token is a login session and is valid for 30 minutes. To end it earlier, log out. The token then stops working immediately:

```bash
curl -s -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer $TOKEN"   # 204
curl -s -i http://localhost:8080/api/orders/my -H "Authorization: Bearer $TOKEN"            # 401
```

The API documentation is at `http://localhost:8080/swagger-ui.html` in the `dev` profile: log in through `POST /api/auth/login`, then use the "Authorize" button to paste the token.

> The login and token setup is meant for this assignment only and is **not production-ready authentication** (see section 7 of the design document).

### Error format

Every error response, including `401` and `403`, has the same JSON body:

```json
{
  "status": 409,
  "error": "INVALID_STATUS_TRANSITION",
  "message": "Cannot change order status from CREATED to DELIVERED",
  "timestamp": "2026-11-05T09:30:00.123456Z",
  "path": "/api/orders/5b0a3c1e-8f0e-4a43-9d58-0c8e6d2f7a11/status"
}
```

`error` is a stable code to branch on. Validation errors (`VALIDATION_FAILED`, `400`) add a `fieldErrors` list with the field name and message. Unexpected failures return `500` with a generic message. Details are only written to the server log. The full list of codes is in section 8 of the design document.

Full request examples for every endpoint will be added in the documentation step.

## CI pipeline *(coming)*

A GitLab CI pipeline (build, test, package) and the notes on extending it will be added in the packaging step.

## Assumptions and limitations

The full list is in [documentation/design.md](documentation/design.md), sections 2 and 10. Key points so far:

- Authentication is a local login that starts a database-backed session (random token, only its hash stored). Roles are checked with `@PreAuthorize`. It is a simplified setup, not a replacement for a real identity provider.
- Unit prices are supplied by the client because there is no product catalog. The server always calculates totals.
- Orders cannot be cancelled, and order creation is not idempotent.
