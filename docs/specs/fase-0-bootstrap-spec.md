# Fase 0 — Bootstrap y Arquitectura Base: Especificación Técnica

> Versión: 1.0 — 2026-08-06
> Propietario: Subagente Fase 0
> Estado: Aprobada (base para la implementación)

---

## 1. Objetivo

Establecer el esqueleto del backend de NeonVibe: un proyecto Maven con Spring Boot que
compile, corra localmente y se conecte a PostgreSQL a través de Docker Compose, con la
estructura de paquetes, configuración base (CORS, Jackson, validación, manejo global de
excepciones) y Flyway listos para las fases siguientes.

## 2. Alcance

**Incluye:**

- Proyecto Maven `neonvibe/backend/` con packaging JAR.
- Spring Boot 3.4.x con Java 21.
- Docker Compose para PostgreSQL 16 (dev).
- Estructura de paquetes `com.neonvibe`.
- Configuración base: CORS, Jackson, validación, exception handler global.
- Flyway activo con script placeholder `V1__init_schema.sql`.
- Health endpoint vía Spring Boot Actuator.
- Maven wrapper funcional.

**Excluye:**

- Cualquier código frontend (React/Vite). Fase exclusivamente backend.
- Entidades de dominio reales (Fase 2), auth (Fase 1), scanner (Fase 2).
- Despliegue en producción (Fase 11).

## 3. Versiones exactas

| Componente    | Versión          | Justificación                                  |
|---------------|------------------|------------------------------------------------|
| Spring Boot   | 3.4.13 (3.4.x)   | Última 3.4.x publicada en Maven Central        |
| Java          | 21 LTS           | LTS, stack definido en AGENTS.md                |
| PostgreSQL    | 16               | Requisito de fase                               |
| Maven         | 3.9.x (via wrapper) | Build tool                                  |
| Flyway        | gestionada por Spring Boot BOM (spring-boot 3.4.13) | |
| Lombok        | gestionada por BOM (1.18.x) |                                |
| MapStruct     | 1.6.x            | Mapeo DTO↔Entidad                              |
| H2 (test)     | gestionada por BOM | Base de datos embebida para tests            |

## 4. Dependencias Maven (GAV)

Todas las versiones de dependencias gestionadas por el BOM `spring-boot-starter-parent:3.4.13`
salvo indicación contraria.

| Artefacto                                          | Grupo                                         | Propósito                         |
|----------------------------------------------------|-----------------------------------------------|-----------------------------------|
| `spring-boot-starter-web`                          | `org.springframework.boot`                    | REST API, Tomcat embebido         |
| `spring-boot-starter-data-jpa`                     | `org.springframework.boot`                    | Spring Data JPA + Hibernate       |
| `spring-boot-starter-validation`                   | `org.springframework.boot`                    | Bean Validation (`@Valid`)        |
| `spring-boot-starter-actuator`                     | `org.springframework.boot`                    | Health/metrics endpoints          |
| `spring-boot-starter-security`                     | `org.springframework.boot`                    | Base security (auth en Fase 1)    |
| `spring-boot-starter-websocket`                    | `org.springframework.boot`                    | WebSockets (sync en Fase 3)       |
| `flyway-core` + `flyway-database-postgresql`       | `org.flywaydb`                                | Migraciones de base de datos      |
| `postgresql`                                       | `org.postgresql`                              | Driver JDBC PostgreSQL (runtime)  |
| `h2`                                               | `com.h2database`                              | DB embebida para tests (test)     |
| `lombok`                                           | `org.projectlombok`                           | Boilerplate (getters/setters)     |
| `mapstruct` + `mapstruct-processor`                | `org.mapstruct`                               | Mapeo DTO↔Entidad                 |
| `spring-boot-starter-test`                         | `org.springframework.boot`                    | JUnit 5, AssertJ, MockMvc (test)  |
| `spring-security-test`                             | `org.springframework.security`                | Soporte de tests security (test)  |

Nota: en Fase 0 no se definen entidades, por lo que `mapstruct` y parte de JPA se declaran
para establecer el patrón sin introducir código de mapeo todavía.

## 5. Docker Compose — PostgreSQL 16

Archivo: `neonvibe/docker-compose.dev.yml`

| Servicio         | `db`                                          |
|------------------|-----------------------------------------------|
| Imagen           | `postgres:16-alpine`                          |
| Puerto host      | `5432:5432`                                   |
| DB               | `neonvibe`                                    |
| Usuario          | `neonvibe`                                    |
| Password         | `neonvibe` (dev solamente; sobrescribible por env) |
| Volumen          | `postgres_data` → `/var/lib/postgresql/data` (named volume) |
| Healthcheck      | `pg_isready -U neonvibe -d neonvibe`          |

## 6. Estructura de paquetes

```
com.neonvibe
├── NeonVibeApplication.java   # @SpringBootApplication
├── config/                    # CORS, Jackson, WebSocket, Security
├── controller/                # REST controllers
├── service/                   # Lógica de negocio @Service
├── repository/                # Interfaces Spring Data JPA
├── domain/                    # Entidades JPA
├── dto/                       # Request/Response objects
├── mapper/                    # MapStruct mappers
├── security/                  # JWT, OAuth2, security filters
├── scanner/                   # File watcher (Fase 2)
├── websocket/                 # STOMP handlers (Fase 3)
├── infra/                     # Carátulas, lyrics, last.fm clients
└── exception/                 # Tipos de excepción + GlobalExceptionHandler
```

En Fase 0 solo se materializan los paquetes cuyo contenido existe hoy:
`config` (CORS, Jackson), `exception` (GlobalExceptionHandler). El resto se establece con
archivos `package-info.java` o `.gitkeep` para fijar la estructura y evitar refactors en
fases posteriores.

## 7. Configuración (archivos)

| Archivo                          | Rol                                                        |
|----------------------------------|------------------------------------------------------------|
| `src/main/resources/application.yml` | Profile por defecto (orientado a producción)            |
| `src/main/resources/application-dev.yml` | Overrides de desarrollo (DB local, logging DEBUG)   |
| `src/main/resources/application-test.yml` | Profile de test (H2 en memoria)                   |

Claves base (`application.yml`):

```yaml
spring:
  application:
    name: neonvibe
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate   # esquema gobernado por Flyway
spring.jackson:
  property-naming-strategy: SNAKE_CASE
management:
  endpoints:
    web:
      exposure:
        include: health,info
neonvibe:
  music:
    paths: /srv/Music
    supported-formats: mp3,flac,aac,ogg,m4a,wav
    scanner:
      interval-seconds: 0
  covers:
    cache-path: ./data/covers
    max-size-mb: 500
  websocket:
    allowed-origins: "*"
```

Dev (`application-dev.yml`): sobreescribe datasource a `jdbc:postgresql://localhost:5432/
neonvibe`, usuario/password `neonvibe`, y logging DEBUG para `com.neonvibe`.

Test (`application-test.yml`): `jdbc:h2:mem:neonvibe;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`,
`ddl-auto: none`, Flyway desactivado (el script V1 es solo placeholder).

## 8. Migración de base de datos (Flyway)

- Ubicación: `src/main/resources/db/migration/`
- Script `V1__init_schema.sql`: solo comentario placeholder
  `-- Placeholder: schema will be defined in Phase 2`.
- Convención de nombrado: `V{n}__{descripcion_snake}.sql` (AGENTS.md §8).

## 9. Contrato del endpoint de salud

**Endpoint:** `GET /actuator/health`

| Método | Ruta               | Código esperado | Cuerpo                              |
|--------|--------------------|-----------------|-------------------------------------|
| GET    | `/actuator/health` | `200 OK`        | `{"status":"UP"}`                   |

En profile dev, al conectar a PostgreSQL, el estado también refleja el componente `db`:
`{"status":"UP","components":{"db":{"status":"UP"}}}`.

## 10. Lista de archivos/carpetas a crear

```
neonvibe/backend/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/wrapper/maven-wrapper.properties
├── .gitignore
├── src/main/java/com/neonvibe/
│   ├── NeonVibeApplication.java
│   ├── config/
│   │   ├── CorsConfig.java
│   │   ├── JacksonConfig.java
│   │   └── package-info.java
│   ├── controller/package-info.java
│   ├── service/package-info.java
│   ├── repository/package-info.java
│   ├── domain/package-info.java
│   ├── dto/package-info.java
│   ├── mapper/package-info.java
│   ├── security/package-info.java
│   ├── scanner/package-info.java
│   ├── websocket/package-info.java
│   ├── infra/package-info.java
│   └── exception/
│       ├── GlobalExceptionHandler.java
│       └── package-info.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-test.yml
│   └── db/migration/V1__init_schema.sql
├── src/test/java/com/neonvibe/
│   └── NeonVibeApplicationTests.java
└── src/test/resources/application-test.yml
neonvibe/docker-compose.dev.yml
neonvibe/docs/specs/fase-0-bootstrap-spec.md
neonvibe/docs/plans/fase-0-bootstrap-plan.md
neonvibe/docs/reviews/fase-0-bootstrap-review.md
```

## 11. Criterios de aceptación

1. `./mvnw clean compile` termina con 0 errores.
2. `docker compose -f docker-compose.dev.yml up -d` levanta PostgreSQL accesible.
3. `./mvnw spring-boot:run` levanta sin errores.
4. `GET http://localhost:8080/actuator/health` devuelve `{"status":"UP"}`.
5. `./mvnw test` pasa (context load test).
