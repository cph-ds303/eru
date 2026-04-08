# ERU Backend

ERU is a small backend API for a learning-focused scrolling platform. The idea is to store short educational content such as facts, quotes, and theories, and expose it through a REST API.

The project is built as a 3rd semester backend exam project with a simple layered architecture:

- Routes define the HTTP endpoints
- Controllers handle request/response logic
- Services contain validation and business rules
- DAOs handle persistence with Hibernate/JPA
- PostgreSQL stores the data

## Scope

The implemented scope is intentionally small and exam-safe:

- User registration and login with JWT
- Current authenticated user endpoint
- Content CRUD
- Filtering content by type and active status
- One JPA relationship through `User` <-> `Role`
- Content interaction feature through `UserInteraction`
- Optional AI elaboration endpoint
- Unit tests for service logic
- Integration test for auth + content routes using Testcontainers

Features that are present in the codebase but should be treated as lower priority in the exam:

- `UserInteraction`
- advanced role administration
- AI integration details

## Tech Stack

- Java 17
- Maven
- Javalin
- Hibernate / JPA
- PostgreSQL
- JWT
- JUnit 5
- Testcontainers

## Architecture

Request flow:

Client -> Route -> Controller -> Service -> DAO -> Hibernate/JPA -> PostgreSQL

Why this structure is useful in the exam:

- it shows separation of concerns
- it is easy to trace
- it matches the semester material
- it avoids overengineering

## Entities

### Content

Main domain entity used for the educational feed.

Important fields:

- `id`
- `title`
- `body`
- `contentType`
- `category`
- `source`
- `author`
- `active`
- `createdAt`

### User

Simple security entity used for registration/login.

Important fields:

- `id`
- `username`
- `passwordHash`
- `roles`
- `createdAt`

### Role

Used together with `User` in a many-to-many relationship for authorization.

## Endpoints

### Open endpoints

- `GET /api/v1/`
- `GET /api/v1/health`
- `GET /api/v1/routes`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/content`
- `GET /api/v1/content/{id}`
- `GET /api/v1/content/{id}/interactions`

### Protected endpoints

- `GET /api/v1/auth/me`
- `POST /api/v1/content`
- `PUT /api/v1/content/{id}`
- `DELETE /api/v1/content/{id}`
- `POST /api/v1/content/{id}/interactions`

### Optional endpoint

- `POST /api/v1/ai/elaborate`

## Example Requests

Preferred examples live in [src/main/resources/http](/D:/TheFileSTATIONÆR/eru/src/main/resources/http), grouped by feature.

## Local Setup

### 1. Start PostgreSQL

The easiest local setup is Docker:

```bash
docker compose up -d
```

This starts PostgreSQL on:

- database: `eru`
- username: `postgres`
- password: `postgres`

### 2. Optional configuration

The app can run locally with sensible defaults for the database and JWT secret.

If you want explicit configuration, use environment variables or create `src/main/resources/config.properties` based on `src/main/resources/config.properties.example`.

Supported properties:

- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_URL`
- `JWT_SECRET`
- `OPENAI_API_KEY` (optional)
- `OPENAI_MODEL` (optional)

### 3. Run the application

```bash
mvn clean compile
```

Then run `app.Main` from your IDE.

The API starts on `http://localhost:7070/api/v1`.

## CI/CD And Deployment

The project is prepared for a simple GitHub Actions and Docker Hub pipeline.

Included files:

- [Dockerfile](/D:/TheFileSTATIONÆR/eru/Dockerfile)
- [.github/workflows/workflow.yml](/D:/TheFileSTATIONÆR/eru/.github/workflows/workflow.yml)

The intended pipeline is:

1. GitHub Actions builds and tests the project with Maven
2. Maven creates `target/app.jar`
3. Docker builds an image from the `Dockerfile`
4. The image is pushed to Docker Hub
5. A server can pull and run the image with environment variables for deployment

### Required GitHub secrets

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`

### Runtime environment variables for deployed mode

The deployed Hibernate setup uses:

- `DEPLOYED`
- `DB_NAME`
- `CONNECTION_STR`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`

Optional:

- `OPENAI_API_KEY`
- `OPENAI_MODEL`

## Testing

Run tests with:

```bash
mvn test
```

Notes:

- service tests run without Docker
- the integration test uses Testcontainers and is skipped automatically if Docker is unavailable

## Semester Topic Mapping

This project can be explained in relation to the course topics like this:

- Java Threads: not prioritized, intentionally cut to keep the scope realistic
- JPA Basics: entities, CRUD, DAO pattern, EntityManagerFactory, JPQL/named queries
- JPA Relations: many-to-many relation between `User` and `Role`
- Data Integration: optional OpenAI integration
- REST API: Javalin routes and JSON DTOs
- REST Testing: service tests and one integration test
- Security: registration, login, password hashing, JWT, route roles
- Deployment/DevOps: Dockerized PostgreSQL and environment-based configuration

## What To Emphasize In The Exam

If time is short, focus your explanation on these parts:

1. `Content` is the main domain entity and supports CRUD
2. The layered architecture keeps responsibilities separated
3. Hibernate/JPA handles persistence to PostgreSQL
4. DTOs are used between the API and the domain model
5. Auth uses hashed passwords and JWT-based protection on selected routes

## What To Deprioritize

These parts are safe to describe as optional or future work:

- richer user interaction features
- recommendation logic
- concurrency/background jobs
- deployment beyond local Docker setup


