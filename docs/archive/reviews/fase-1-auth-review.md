# Fase 1 — Autenticación y Usuarios: Revisión

> Fecha: 2026-08-06
> Revisado por: Subagente Fase 1 (entorno sandbox)
> Base: spec (`docs/specs/fase-1-auth-spec.md`) y plan (`docs/plans/fase-1-auth-plan.md`)

---

## 1. Resumen

Se implementó el sistema de autenticación de NeonVibe (Fase 1): entidad `User` persistida
con migración Flyway, login con Google OAuth2 (id_token), emisión de JWT internos
(access + refresh), filtro de seguridad de Spring con rutas públicas vs protegidas, y los
endpoints `POST /api/v1/auth/google`, `POST /api/v1/auth/refresh` y `GET /api/v1/auth/me`.
Incluye modo mock para desarrollo sin credenciales reales de Google.

## 2. Resultados de compilación y tests

| Prueba | Resultado |
|--------|-----------|
| `./mvnw clean compile` | **PASS** (0 errores) |
| `./mvnw test` | **PASS** (29/29, 0 failures, 0 errors) |

### Detalle de tests (29)

| Clase de test | Tests | Resultado |
|---------------|-------|-----------|
| `security/JwtTokenProviderTest` | 6 | PASS |
| `controller/AuthControllerTest` (`@WebMvcTest`) | 6 | PASS |
| `service/AuthServiceTest` | 5 | PASS |
| `repository/UserRepositoryTest` (`@DataJpaTest`) | 4 | PASS |
| `config/SecurityConfigTest` (integración) | 3 | PASS |
| `exception/GlobalExceptionHandlerTest` | 4 | PASS |
| `NeonVibeApplicationTests` (context load) | 1 | PASS |

## 3. Checklist de seguridad

| Ítem | Estado |
|------|--------|
| Secreto JWT externalizado (nunca hardcodeado) | ✅ PASS — `neonvibe.jwt.secret: ${JWT_SECRET:neonvibe-dev-only-secret-change-me...}` con placeholder claramente dev-only |
| Sesiones stateless | ✅ PASS — `SessionCreationPolicy.STATELESS` |
| CSRF deshabilitado (API Bearer stateless) | ✅ PASS |
| Rutas públicas vs protegidas | ✅ PASS — públicas: `/actuator/health`, `/api/v1/auth/**`, `/error`; resto `/api/**` autenticado |
| CORS reutilizando `CorsConfig` | ✅ PASS — `cors(withDefaults())` usa el bean `CorsConfigurationSource` existente |
| 401 con JSON estándar (no página por defecto) | ✅ PASS — `AuthenticationEntryPoint` personalizado devuelve `{error:unauthorized,...}` |
| Access vs refresh token distinguibles | ✅ PASS — claim `typ` (`access`/`refresh`), validación cruzada rechaza el tipo incorrecto |
| Sin `@Autowired` en campos (constructor injection) | ✅ PASS — 0 en `src/main`; los 4 de tests son idiomáticos |
| Controllers no exponen entidades JPA | ✅ PASS — solo DTOs (`UserResponse`, `AuthResponse`, etc.) |
| Modo mock/dev documentado | ✅ PASS — `neonvibe.auth.google.enabled=false` en dev/test |

## 4. Modo mock/dev documentado

- `application-dev.yml`: `neonvibe.auth.google.enabled: false`.
- `application-test.yml` (main y test resources): `neonvibe.auth.google.enabled: false`.
- Con `enabled=false`, `AuthService` acepta cualquier `id_token` no vacío y deriva un
  usuario de prueba estable: `dev-<hash>@neonvibe.local` con `google_id = idToken`.
- Con `enabled=true` (default/prod), valida el `id_token` contra el endpoint público de
  Google `https://oauth2.googleapis.com/tokeninfo?id_token=...` (sin client_secret).

## 5. Desviaciones respecto al spec/plan

1. **`@WebMvcTest` requiere contexto de security:** para que `AuthControllerTest`
   (@WebMvcTest) cargara `SecurityConfig`, se añadió `@Import({SecurityConfig.class,
   JwtAuthenticationFilter.class, JwtTokenProvider.class})` con el `JwtTokenProvider`
   real (el secreto se inyecta desde `application.yml` vía `${neonvibe.jwt.secret}`).
2. **`UserRepositoryTest` necesita `@ActiveProfiles("test")`:** sin el profile activo,
   `@DataJpaTest` no cargaba el `application-test.yml` que desactiva Flyway, y H2 no
   acepta `TIMESTAMPTZ` del script `V2`. Al activar `test`, Flyway queda desactivado y
   Hibernate genera el esquema (`ddl-auto: create-drop`).
3. **`GET` a endpoint POST-only:** inicialmente devolvía 500 (caía en el handler genérico).
   Se añadió un `@ExceptionHandler(HttpRequestMethodNotSupportedException)` en
   `GlobalExceptionHandler` que devuelve 405 JSON, y el test de security lo verifica.
4. **Fallback JWT no fue necesario:** jjwt 0.12.6 funcionó sin problemas con Java 21 y
   Spring Boot 3.4.13 (no se requirió `JwtEncoder`/`Decoder` de Spring).
5. **`UserPrincipal` es un `record` simple** (no implementa `UserDetails` completo); es
   suficiente para el MVP (solo identidad, sin password). Documentado en el código.

## 6. Endpoints implementados

| Método | Ruta                  | Acceso             | Descripción                                        |
|--------|-----------------------|--------------------|----------------------------------------------------|
| POST   | `/api/v1/auth/google` | Pública (mock/real)| Login con id_token de Google → `AuthResponse`      |
| POST   | `/api/v1/auth/refresh`| Pública            | Refresh token → nuevo access token                 |
| GET    | `/api/v1/auth/me`     | Bearer             | Datos del usuario autenticado → `UserResponse`     |

## 7. Archivos creados/modificados

### Creados (main)
- `config/SecurityConfig.java`
- `domain/User.java`
- `repository/UserRepository.java`
- `security/JwtTokenProvider.java`
- `security/JwtAuthenticationFilter.java`
- `security/UserPrincipal.java`
- `service/AuthService.java`
- `service/UserService.java`
- `controller/AuthController.java`
- `dto/GoogleTokenRequest.java`, `dto/RefreshTokenRequest.java`, `dto/AuthResponse.java`, `dto/UserResponse.java`
- `exception/InvalidTokenException.java`
- `resources/db/migration/V2__add_users_table.sql`

### Modificados
- `pom.xml` (jjwt-api/impl/jackson 0.12.6, spring-boot-starter-oauth2-client)
- `exception/GlobalExceptionHandler.java` (handlers 401 y 405)
- `application.yml`, `application-dev.yml`, `application-test.yml` (main y test)

### Creados (test)
- `security/JwtTokenProviderTest.java`
- `controller/AuthControllerTest.java`
- `service/AuthServiceTest.java`
- `repository/UserRepositoryTest.java`
- `config/SecurityConfigTest.java`
- `exception/GlobalExceptionHandlerTest.java` (ampliado)

### Documentación
- `docs/specs/fase-1-auth-spec.md`, `docs/plans/fase-1-auth-plan.md`, `docs/reviews/fase-1-auth-review.md`

## 8. Bloqueantes / no verificable

- **Integración real con PostgreSQL:** igual que en Fase 0, no se pudo lanzar Docker
  (permisos) ni validar la migración `V2` contra PostgreSQL real. La migración
  `V2__add_users_table.sql` usa `TIMESTAMPTZ` y `uuid`, tipos válidos en PostgreSQL;
  debe confirmarse con `docker compose up` + profile dev en un entorno con permisos.
- **Flujo real de Google OAuth2:** no verificable sin credenciales reales. El modo mock
  cubre la lógica de negocio; la validación real vía `tokeninfo` está implementada pero
  pendiente de un `client_id` real de proyecto Google.

## 9. Notas para la Fase 2

- El scanner y las entidades musicales (`Track`, `Album`, `Artist`, `PlayQueue`,
  `Playlist`, `PlayHistory`, `Favorite`) deberán añadir columnas `user_id` (UUID)
  referenciando `users.id`. La tabla `users` ya existe con `id uuid PRIMARY KEY`.
- Reutilizar `JwtTokenProvider` y el `SecurityContext` (`UserPrincipal`) para filtrar por
  usuario en los queries de queue/playlists/history/favorites.
- Si en Fase 2 se agrega `spring-boot-starter-oauth2-client` con redirect-managed, el
  diseño actual (endpoint `POST /auth/google` con id_token) es compatible y no requiere
  romper los endpoints existentes.
- Considerar rotación + revocación de refresh tokens (actualmente sin rotación, válido
  7 días, reutilizable).
