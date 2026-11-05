# Order Management API

A small REST API for creating and managing orders, built with Java 17 and Spring Boot 3.5. Customers create and view their own orders, support staff view all orders and move them through their lifecycle, and admins can additionally delete orders. Data is stored in PostgreSQL, with schema changes managed by Liquibase.

> **Status: complete.** All order endpoints, login with sessions and roles, error handling, the integration tests, the container image, Docker Compose and the GitLab CI pipeline are implemented. Parts that are deliberately left out of scope are listed under "Assumptions and limitations".

The design, assumptions and trade-offs are in [documentation/design.md](documentation/design.md).

## Prerequisites

- Java 17
- Docker (for the local database and for the Testcontainers-based tests)
- Nothing else to install: Maven is provided by the wrapper (`./mvnw`)

## Project layout

| Path | Contents |
|------|----------|
| `backend/` | Spring Boot application (Maven project). Code is in `com.example.orders`, packaged by layer: `api`, `service`, `domain`, `repository`, `security`, `config`, `error`. |
| `backend/Dockerfile` | Multi-stage image build (JDK build stage, JRE runtime stage, non-root user) |
| `docker/` | Docker Compose file (PostgreSQL and the application) and environment template |
| `.gitlab-ci.yml` | CI pipeline: compile, test, package |
| `documentation/` | Design document |

## Quick start: everything in Docker

The credentials are local-only fake values. Copy the template once, then build and start PostgreSQL and the application:

```bash
cp docker/.env.example docker/.env
docker compose -f docker/docker-compose.yml --env-file docker/.env up -d --build
```

The API is then at `http://localhost:8080`, and `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}` once it has started (a few seconds). The Compose file starts the application with the `dev` profile so that the sample users and Swagger UI exist. This is for the local demo only. Stop everything with `docker compose -f docker/docker-compose.yml --env-file docker/.env down`.

## Start only the database

To run the application from your IDE or with Maven instead, start just PostgreSQL:

```bash
docker compose -f docker/docker-compose.yml --env-file docker/.env up -d postgres
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

## API usage

Start the application (see above), then log in with a sample user to get a session token. The examples use these shell variables:

```bash
BASE=http://localhost:8080
login() {
  curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
    -d "{\"username\": \"$1\", \"password\": \"dev-password-1\"}" \
    | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'
}
CUSTOMER=$(login customer1)
SUPPORT=$(login support1)
ADMIN=$(login admin1)
```

The login response is `{"accessToken": "...", "tokenType": "Bearer", "expiresIn": 1800}`. The token is a login session, valid for 30 minutes, and is sent as `Authorization: Bearer <token>`.

**Create an order** (customer). The server sets the customer, status and total. Response `201` with a `Location` header:

```bash
curl -s -i -X POST $BASE/api/orders -H "Authorization: Bearer $CUSTOMER" \
  -H 'Content-Type: application/json' \
  -d '{"items": [
        {"productId": "PROD-101", "productName": "Keyboard", "quantity": 2, "unitPrice": 49.99},
        {"productId": "PROD-102", "productName": "Mouse", "quantity": 1, "unitPrice": 19.99}]}'
```

**Get one order** (a customer can only read their own, another customer's order is `404`). Set `ORDER_ID` to the `id` from the response above:

```bash
ORDER_ID=<id from the create response>
curl -s $BASE/api/orders/$ORDER_ID -H "Authorization: Bearer $CUSTOMER"
```

**List my orders** (any role, paged):

```bash
curl -s "$BASE/api/orders/my?page=0&size=10" -H "Authorization: Bearer $CUSTOMER"
```

**List all orders** (support and admin, paged and sorted). `sort` is `field,direction` with `createdAt`, `updatedAt`, `status` or `totalAmount`. `size` is at most 100:

```bash
curl -s "$BASE/api/orders?page=0&size=5&sort=totalAmount,asc" -H "Authorization: Bearer $SUPPORT"
```

**Change the status** (support and admin). Only the next step is allowed: `CREATED` to `CONFIRMED` to `SHIPPED` to `DELIVERED`:

```bash
curl -s -X PATCH $BASE/api/orders/$ORDER_ID/status -H "Authorization: Bearer $SUPPORT" \
  -H 'Content-Type: application/json' -d '{"status": "CONFIRMED"}'
```

**Delete an order** (admin only, `204`):

```bash
curl -s -i -X DELETE $BASE/api/orders/$ORDER_ID -H "Authorization: Bearer $ADMIN"
```

**Log out** (the token stops working immediately, `204`):

```bash
curl -s -i -X POST $BASE/api/auth/logout -H "Authorization: Bearer $CUSTOMER"
```

**Examples of errors**:

```bash
# 401: no token
curl -s $BASE/api/orders/my
# 403: support cannot delete orders
curl -s -X DELETE $BASE/api/orders/$ORDER_ID -H "Authorization: Bearer $SUPPORT"
# 404: another customer's order looks the same as a missing one
curl -s $BASE/api/orders/$ORDER_ID -H "Authorization: Bearer $(login customer2)"
# 409: skipping a status (CONFIRMED to DELIVERED)
curl -s -X PATCH $BASE/api/orders/$ORDER_ID/status -H "Authorization: Bearer $SUPPORT" \
  -H 'Content-Type: application/json' -d '{"status": "DELIVERED"}'
# 400: no items
curl -s -X POST $BASE/api/orders -H "Authorization: Bearer $CUSTOMER" \
  -H 'Content-Type: application/json' -d '{"items": []}'
```

The API documentation is at `http://localhost:8080/swagger-ui.html` in the `dev` profile: log in through `POST /api/auth/login`, then use the "Authorize" button to paste the token. Request and response samples for every endpoint are also in section 5 of the design document.

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

## CI pipeline

[.gitlab-ci.yml](.gitlab-ci.yml) has three stages, all running `./mvnw` in `backend/`:

| Stage | Job | What it does |
|-------|-----|--------------|
| `build` | `compile` | Compiles the code |
| `test` | `test` | Runs all tests (`./mvnw verify`) and publishes the JUnit reports in the GitLab UI |
| `package` | `package` | Builds the executable jar and keeps it as an artifact |

The Maven repository is cached between runs (the cache key is `backend/pom.xml`). The integration tests start PostgreSQL with Testcontainers, so the `test` job runs with a Docker-in-Docker service. The GitLab runner must allow privileged containers for that. Building and publishing the container image, dependency scanning, deployment to a test environment, health checks and rollback are not part of the pipeline. How they would be added is described in section 11 of [documentation/design.md](documentation/design.md).

Checking the pipeline locally:

- The same commands the jobs run can be run by hand: `cd backend && ./mvnw -B verify && ./mvnw -B -DskipTests package`.
- To validate the file syntax, paste it in the "CI lint" page of a GitLab project, or run `glab ci lint` (GitLab CLI).
- To run the jobs on your machine, use [gitlab-ci-local](https://github.com/firecow/gitlab-ci-local), for example `gitlab-ci-local compile` and `gitlab-ci-local package`. The `test` job needs a Docker-in-Docker service, which is easiest to try in a real GitLab project.

The container image is built with `docker build -t order-management-api backend`. The Dockerfile uses two stages, so the final image contains only a JRE and the jar, and it runs as a non-root user.

## Assumptions and limitations

The full list is in [documentation/design.md](documentation/design.md), sections 2 and 10. Key points:

- Authentication is a local login that starts a database-backed session (random token, only its hash stored). Roles are checked with `@PreAuthorize` and again in the service. It is a simplified setup, not a replacement for a real identity provider. There is no rate limiting or account lockout, and no user registration or password change.
- Unit prices are supplied by the client because there is no product catalog. The server always calculates totals.
- Orders cannot be cancelled, and order creation is not idempotent.
- Every request does one database lookup to validate the session token. That is fine at this scale, and a cache or signed tokens would be the next step.
- The Compose file runs the application with the `dev` profile (sample users, Swagger UI) for the local demo. A real deployment uses no profile, gets its database settings from environment variables and has no sample users.
- The pipeline builds and tests but does not publish or deploy. See section 11 of the design document.
