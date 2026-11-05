# Order Management API

A small REST API for creating and managing orders, built with Java 17 and Spring Boot 3.5. Customers create and view their own orders, support staff view all orders and move them through their lifecycle, and admins can additionally delete orders. Data is stored in PostgreSQL, with schema changes managed by Liquibase.

> **Status: work in progress.** The database layer, project setup, the domain model and the order endpoints (create, read, list, update status, delete) are done. Login and the security configuration, consistent error responses, the container image and the CI pipeline are not implemented yet, so the endpoints cannot be called with real tokens or curl until the security step is finished. Sections marked *(coming)* describe what will be added.

The design, assumptions and trade-offs are in [documentation/design.md](documentation/design.md).

## Prerequisites

- Java 17
- Docker (for the local database and for the Testcontainers-based tests)
- Nothing else to install: Maven is provided by the wrapper (`./mvnw`)

## Project layout

| Path | Contents |
|------|----------|
| `backend/` | Spring Boot application (Maven project). Code is in `com.example.orders`, packaged by layer: `api`, `service`, `domain`, `repository`, `security`, `config`, `error`. `security` and `error` are only partly filled in so far. |
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
| `JWT_SECRET` | HS256 signing key for tokens, at least 32 bytes |

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
- **API tests (MockMvc and Testcontainers):** the order endpoints over HTTP with simulated tokens, including the `201` and `Location` header, ignored client totals, input validation (`400`) and role denials (`403`).

All Testcontainers-based tests share one PostgreSQL container, so Docker is started once per test run.

## Inspect the database

```bash
docker exec orders-postgres psql -U orders_dev -d orders -c '\dt'
```

## API usage *(coming)*

Example requests, login and token usage, and the OpenAPI / Swagger UI address will be added once the endpoints exist.

## CI pipeline *(coming)*

A GitLab CI pipeline (build, test, package) and the notes on extending it will be added in the packaging step.

## Assumptions and limitations

The full list is in [documentation/design.md](documentation/design.md), sections 2 and 10. Key points so far:

- Authentication is a local login issuing JWTs. It is a simplified setup, not a replacement for a real identity provider.
- Unit prices are supplied by the client because there is no product catalog. The server always calculates totals.
- Orders cannot be cancelled, and order creation is not idempotent.
