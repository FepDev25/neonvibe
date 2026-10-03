# Fase 1 — Autenticación y Usuarios: Plan de Implementación

> Versión: 1.0 — 2026-08-06
> Propietario: Subagente Fase 1
> Estado: Ejecutable
> Base: `docs/specs/fase-1-auth-spec.md`

---

## 1. Orden de implementación

```text
1. Dependencias (pom.xml): jjwt-api/impl/jackson + spring-boot-starter-oauth2-client
2. Entidad User + repositorio + migración Flyway V2
3. Utilidades JWT (JwtTokenProvider, UserPrincipal)
4. JwtAuthenticationFilter
5. SecurityConfig (filter chain, entry point, CORS, stateless)
6. DTOs (GoogleTokenRequest, RefreshTokenRequest, AuthResponse, UserResponse)
7. AuthService + UserService + controller AuthController
8. application.yml / dev / test (jwt + google.auth config)
9. Tests (provider, controller, service, repository, security config)
10. Compilación + verificación completa
```

### Racional del orden

- Primero las dependencias para que el IDE/compilador reconozca las clases de jjwt.
- La entidad + migración antes del servicio de auth (el servicio depende del repositorio).
- Las utilidades JWT antes del filtro y el filtro antes de SecurityConfig.
- DTOs antes del servicio/controller (estos referencian los DTOs).
- Tests al final, una vez el código compila (test-driven no aplica aquí; validación posterior).

---

## 2. Secuencia de archivos por crear

### Paso 1 — Dependencias (`pom.xml`)

Modificar `backend/pom.xml` añadiendo, con versión gestionada explícitamente porque jjwt
no la gestiona el BOM de Spring:

```xml
<!-- JWT (jjwt) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<!-- Google OAuth2 client (login con Google) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

Property: `<jjwt.version>0.12.6</jjwt.version>`.

### Paso 2 — Entidad + Repositorio + Migración

- `domain/User.java` — `@Entity @Table(name = "users")`, Lombok `@Getter @Setter @NoArgsConstructor`, campos según spec §5, `@PrePersist/@PreUpdate` para timestamps.
- `repository/UserRepository.java` — `extends JpaRepository<User, UUID>`, `Optional<User> findByEmail`, `Optional<User> findByGoogleId`.
- `db/migration/V2__add_users_table.sql` — `CREATE TABLE users (...)` con constraints e índices UNIQUE. El placeholder `V1` no se toca.

### Paso 3 — Utilidades JWT

- `security/JwtTokenProvider.java` — `@Component`. Lee `neonvibe.jwt.*` vía constructor. Genera access/refresh con `Jwts.builder()`, valida firma y expiración, extrae claims. Acepta `typ` para distinguir access de refresh.
- `security/UserPrincipal.java` — record simple con `UUID id`, `String email`, `String name` (wrapper para el SecurityContext; no implementa `UserDetails` completa, basta para el MVP).

### Paso 4 — Filtro

- `security/JwtAuthenticationFilter.java` — `OncePerRequestFilter`. Extrae Bearer, valida con `JwtTokenProvider`, si es access token válido construye `UsernamePasswordAuthenticationToken` con `UserPrincipal` y lo pone en `SecurityContextHolder`.

### Paso 5 — SecurityConfig

- `config/SecurityConfig.java` — `@Configuration @EnableWebSecurity`. Bean `SecurityFilterChain`:
  - `csrf().disable()`, `sessionManagement().stateless()`
  - `authorizeHttpRequests`: pública `/actuator/health`, `/api/v1/auth/**`, `/error`; el resto autenticado.
  - `exceptionHandling().authenticationEntryPoint(customEntryPoint)`
  - `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`
  - `cors(withDefaults())` para reutilizar el bean `CorsConfigurationSource` de `CorsConfig`.
- Bean `PasswordEncoder` (BCrypt) documentado como futuro auth local.
- Bean `AuthenticationEntryPoint` que devuelve JSON 401 estándar.

### Paso 6 — DTOs

- `dto/GoogleTokenRequest.java` — `@NotBlank String idToken`.
- `dto/RefreshTokenRequest.java` — `@NotBlank String refreshToken`.
- `dto/AuthResponse.java` — `String accessToken`, `String refreshToken`, `String tokenType`, `long expiresIn`.
- `dto/UserResponse.java` — `UUID id`, `String email`, `String name`, `String avatarUrl`, `Instant createdAt`.

### Paso 7 — Servicios + Controller

- `service/UserService.java` — `findById`, `findByEmail`, `findByGoogleId`, `save`.
- `service/AuthService.java` — `googleLogin(GoogleTokenRequest)`: valida id_token (real vía `RestClient` a `tokeninfo`, o mock si `google.enabled=false`), crea/actualiza `User`, genera tokens, devuelve `AuthResponse`. `refresh(RefreshTokenRequest)`: valida refresh token, devuelve nuevo access. `me()`: carga usuario por `sub`.
- `controller/AuthController.java` — `@RestController @RequestMapping("/api/v1/auth")`; `POST /google`, `POST /refresh`, `GET /me` (usa `@AuthenticationPrincipal`/SecurityContext).

### Paso 8 — Configuración YAML

Añadir a `application.yml` la sección `neonvibe.jwt.*` y `neonvibe.auth.google.*`; en
`application-dev.yml` y `application-test.yml` fijar `google.enabled=false`.

### Paso 9 — Tests

- `security/JwtTokenProviderTest.java` — generar/validar access y refresh; token expirado inválido; typ incorrecto.
- `controller/AuthControllerTest.java` — `@WebMvcTest` con servicios mockeados: login, refresh, me (con y sin token).
- `service/AuthServiceTest.java` — login mock crea/actualiza usuario y devuelve tokens; login real con tokeninfo mockeado; refresh inválido lanza.
- `repository/UserRepositoryTest.java` — `@DataJpaTest`: save + findByEmail/findByGoogleId.
- `config/SecurityConfigTest.java` — `@SpringBootTest` + MockMvc: `/actuator/health` público, `/api/**` 401 sin token, `/api/v1/auth/me` 401 sin token.

---

## 3. Testing checkpoints

| Después de | Verificar |
|------------|-----------|
| Paso 1 | `./mvnw -q dependency:resolve` no reporta errores de jjwt/oauth2-client |
| Paso 2 | `./mvnw -q compile` compila la entidad y repositorio |
| Pasos 3-4 | `./mvnw -q compile` compila las utilidades JWT y el filtro |
| Paso 5 | `./mvnw -q compile` compila SecurityConfig |
| Pasos 6-7 | `./mvnw -q compile` compila DTOs, servicios y controller |
| Paso 8 | Arranca `spring-boot:run` (profile test) sin errores |
| Paso 9 | `./mvnw test` — todos los tests pass |

---

## 4. Riesgos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| Credenciales reales de Google no disponibles en dev | Modo mock: `neonvibe.auth.google.enabled=false` en perfiles dev/test acepta tokens de prueba y crea un usuario ficticio. |
| jjwt incompatibilidad con Java 21 / Spring Boot 3.4 | Fallback a beans Spring `JwtEncoder`/`JwtDecoder` (Nimbus). Se documenta en la review la vía elegida. |
| `id_token` no verificable offline | En prod usamos el endpoint `tokeninfo` de Google (HTTP público, sin client_secret); en dev/test el modo mock no consulta red. |
| Secreto JWT hardcodeado por error | Todo secreto vía `${JWT_SECRET:...}` con placeholder claro; revisado en la review. |
| Nombre `user` reservado en PostgreSQL | Tabla `users` con `@Table(name="users")`. |
| 401 de Spring con página por defecto | `AuthenticationEntryPoint` personalizado que emite el JSON estándar de error. |
| HTTP client para tokeninfo | `RestClient` de Spring 6.1 (compatible Java 21, sin dependencias extra). En tests se mockea. |

---

## 5. Cómo probar el flujo dev (mock)

```bash
# 1. Levantar con profile test o dev (dev requiere PostgreSQL via docker)
./mvnw spring-boot:run -Dspring-boot.run.profiles=test

# 2. Login con token de prueba (cualquier string no vacío)
curl -s -X POST http://localhost:8080/api/v1/auth/google \
  -H 'Content-Type: application/json' \
  -d '{"id_token":"mock-token"}'
# → { access_token, refresh_token, token_type, expires_in }

# 3. GET /auth/me con el access_token
curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <access_token>"

# 4. Refresh
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refresh_token":"<refresh_token>"}'
```

Nota: en profile `test` la app usa H2 en memoria; las tablas se crean con `ddl-auto:
create-drop`, por lo que no se necesita Flyway real para validar localmente (la migración
V2 se valida con PostgreSQL en el entorno con permisos Docker / profile dev).

---

## 6. Consideraciones para el entorno real

- Configurar `JWT_SECRET` (>= 256 bits para HS256) y, si se quiere Google real,
  `GOOGLE_AUTH_ENABLED=true` en el servidor (Fase 11).
- El frontend (Fase 4+) obtendrá el `id_token` de Google y lo enviará al backend.
- Fase 2 añadirá las columnas `user_id` en `PlayQueue`, `Playlist`, `PlayHistory`,
  `Favorite` referenciando `users.id`.
