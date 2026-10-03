# Fase 0 — Bootstrap y Arquitectura Base: Plan de Implementación

> Versión: 1.0 — 2026-08-06
> Referencia: `docs/specs/fase-0-bootstrap-spec.md`

---

## 1. Orden de implementación

La implementación se hace en pasos pequeños, cada uno con un checkpoint verificable
antes de continuar. El orden minimiza el tiempo entre "crear archivo" y "poder compilar".

| Paso | Descripción                                                       | Checkpoint                       |
|------|-------------------------------------------------------------------|----------------------------------|
| 1    | Crear `neonvibe/backend/` con `pom.xml` (Spring Boot 3.4.22, Java 21) | `pom.xml` válido (XML parseable) |
| 2    | Añadir Maven wrapper (`./mvnw`)                                   | `./mvnw -v` imprime versión      |
| 3    | Añadir `.gitignore` de backend                                    | Archivo presente                 |
| 4    | Clase principal `NeonVibeApplication` + paquetes (`package-info.java`/`.gitkeep`) | Estructura de carpetas creada |
| 5    | Archivos de configuración: `application.yml`, `application-dev.yml`, `application-test.yml` | YAML válidos |
| 6    | Beans de configuración: `CorsConfig`, `JacksonConfig`             | Compila (se valida en paso 9)    |
| 7    | `GlobalExceptionHandler` (@ControllerAdvice)                      | Compila                          |
| 8    | Flyway `V1__init_schema.sql` (placeholder)                        | Migración presente               |
| 9    | Test de contexto `NeonVibeApplicationTests`                       | `./mvnw test` pasa               |
| 10   | `docker-compose.dev.yml` (PostgreSQL 16)                          | `docker compose config` válido   |
| 11   | Build completo + verificación de health (ver §3)                  | Criterios de aceptación          |

## 2. Archivos a crear (en orden)

1. `neonvibe/backend/pom.xml`
2. `neonvibe/backend/.mvn/wrapper/maven-wrapper.properties`
3. `neonvibe/backend/mvnw` + `neonvibe/backend/mvnw.cmd` (generados por `mvn wrapper:wrapper` o copiados del wrapper oficial)
4. `neonvibe/backend/.gitignore`
5. `neonvibe/backend/src/main/java/com/neonvibe/NeonVibeApplication.java`
6. `package-info.java` de cada paquete (`config`, `controller`, `service`, `repository`, `domain`, `dto`, `mapper`, `security`, `scanner`, `websocket`, `infra`, `exception`)
7. `neonvibe/backend/src/main/java/com/neonvibe/config/CorsConfig.java`
8. `neonvibe/backend/src/main/java/com/neonvibe/config/JacksonConfig.java`
9. `neonvibe/backend/src/main/java/com/neonvibe/exception/GlobalExceptionHandler.java`
10. `neonvibe/backend/src/main/resources/application.yml`
11. `neonvibe/backend/src/main/resources/application-dev.yml`
12. `neonvibe/backend/src/main/resources/application-test.yml`
13. `neonvibe/backend/src/main/resources/db/migration/V1__init_schema.sql`
14. `neonvibe/backend/src/test/java/com/neonvibe/NeonVibeApplicationTests.java`
15. `neonvibe/backend/src/test/resources/application-test.yml`
16. `neonvibe/docker-compose.dev.yml`

## 3. Checkpoints de testing

- **Tras paso 2:** `./mvnw -v` muestra la versión de Maven → indica que el wrapper funciona.
- **Tras paso 9:** `./mvnw test` → el test de contexto carga el contexto de Spring y pasa.
- **Tras paso 11 (validación final):**
  1. `./mvnw clean compile` → 0 errores.
  2. `docker compose -f docker-compose.dev.yml up -d` → PostgreSQL UP.
  3. `./mvnw spring-boot:run` (profile dev) → arranca sin errores.
  4. `curl http://localhost:8080/actuator/health` → `{"status":"UP"}` (con componente `db` UP).
  5. `./mvnw test` → pasa.

## 4. Mitigación de riesgos

| Riesgo                                              | Mitigación                                                                 |
|-----------------------------------------------------|----------------------------------------------------------------------------|
| Maven no instalado en el sistema                    | Uso del Maven wrapper (`mvnw`), que descarga Maven automáticamente.        |
| Descarga de dependencias lenta / sin red            | Primera ejecución `clean compile` descarga el classpath; tiempos largos tolerados en el primer intento. |
| Puerto 5432 ya ocupado en el host                   | Comprobar puerto antes de `docker compose up`; si está ocupado, mapear a otro puerto host (documentar). |
| Puertos 8080 ocupados por otros servicios           | Spring Boot fallback a `8080` libre; si no, comprobar con `lsof`.          |
| `ddl-auto: validate` sin tabla en DB                | Flyway es la única fuente de esquema; V1 es placeholder, sin tablas aún, así que `validate` pasa sin esquema. |
| `mapstruct`/`lombok` incompatibles con la versión    | Reportar error de annotation processor y ajustar a versión compatible del BOM. |
| Fallo de un paso por >2 minutos sin avance          | Documentar bloqueante en el review y continuar con el resto; no bloquear la fase. |

## 5. Estrategia de commit

Commits en inglés con conventional commits (AGENTS.md §12):

1. `docs(fase-0): add bootstrap plan` — plan.
2. `feat(fase-0): bootstrap Spring Boot project with PostgreSQL and Flyway` — build completo.
3. `docs(fase-0): add bootstrap review` — resultados de revisión.
4. `git push` al finalizar.
