# Fase 1 — Autenticación y Usuarios: Especificación Técnica

> Versión: 1.0 — 2026-08-06
> Propietario: Subagente Fase 1
> Estado: Aprobada (base para la implementación)
> Dependencias: Fase 0 (bootstrap y arquitectura base)

---

## 1. Objetivo

Implementar el sistema de autenticación de NeonVibe: login con Google OAuth2 (código de
autorización) + emisión de tokens JWT internos (access + refresh) y una entidad `User`
persistente. Establecer el filtro de seguridad de Spring (rutas públicas vs protegidas) y
los endpoints `POST /auth/google`, `POST /auth/refresh` y `GET /auth/me`.

## 2. Alcance

**Incluye:**

- Entidad `User` + repositorio + migración Flyway `V2__add_users_table.sql`.
- Integración Google OAuth2 (flujo de código de autorización) con emisión de JWT interno.
- Utilidades JWT (generación, validación, extracción de claims) para access y refresh tokens.
- Filtro de autenticación `OncePerRequestFilter` que lee `Authorization: Bearer`.
- `SecurityFilterChain` con rutas públicas vs protegidas, CORS integrado y sesiones stateless.
- Endpoints: `POST /api/v1/auth/google`, `POST /api/v1/auth/refresh`, `GET /api/v1/auth/me`.
- DTOs: `GoogleTokenRequest`, `RefreshTokenRequest`, `AuthResponse`, `UserResponse`.
- Tests (mínimo 4 clases): token provider, controller, service, security config, repository.
- Modo mock/dev para validar sin credenciales reales de Google.

**Excluye:**

- Frontend y almacenamiento de tokens en cliente (lo gestiona el frontend en Fase 4+).
- Rotación de refresh tokens (MVP: refresh token único y sin revocación).
- OAuth 2 client / server nativo de Spring (`spring-boot-starter-oauth2-client` no genera
  nuestros JWT internos; usamos ese starter solo como transporte del login con Google).
- Cualquier otra entidad de dominio (Fase 2).

## 3. Flujo OAuth2 Google — Código de Autorización

```
┌─────────┐        ┌──────────────┐        ┌──────────────┐        ┌────────┐
│ Browser │  1     │  Frontend    │  2     │  Google      │  3     │ Neon   │
│ (user)  │───────►│ React/Vite   │───────►│  (Accounts)  │───────►│Vibe    │
└─────────┘        └──────────────┘        └──────────────┘        └───┬────┘
                                                                      │
                                       4. Code de autorización ◄─┘
                                          (redirect con ?code=...)
```

### Pasos en detalle

1. El usuario pulsa "Continuar con Google" en el frontend.
2. El frontend redirige al navegador a la URL de autorización de Google:
   `https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&response_type=code&scope=openid email profile&state=...`
3. Google muestra la pantalla de consentimiento. Si el usuario acepta, redirige de vuelta a
   `redirect_uri` con un parámetro `code=...` (y `state` para CSRF).
4. El frontend envía ese `code` a `POST /api/v1/auth/google`.
5. El backend intercambia `code + client_id + client_secret + redirect_uri` por un
   `id_token` y `access_token` en el endpoint `https://oauth2.googleapis.com/token`.
6. El backend obtiene la información del usuario (email, name, picture, sub) del `id_token`:
   - Verificando la firma con las claves públicas de Google
     (`https://www.googleapis.com/oauth2/v3/certs`, JWKS) o
   - Consultando el endpoint `https://oauth2.googleapis.com/tokeninfo?id_token=<id_token>`
     (no requiere secret de cliente para verificar id_token).
7. El backend crea o actualiza el `User` y genera sus propios JWT (access + refresh).
8. Responde `AuthResponse { access_token, refresh_token, token_type, expires_in }`.

### Endpoints de Google involucrados

| Función            | Endpoint                                            |
|--------------------|-----------------------------------------------------|
| Autorización       | `https://accounts.google.com/o/oauth2/v2/auth`      |
| Intercambio token  | `https://oauth2.googleapis.com/token`               |
| Verificación token | `https://oauth2.googleapis.com/tokeninfo?id_token=…`|
| Claves públicas    | `https://www.googleapis.com/oauth2/v3/certs`        |

### Decisión de implementación (MVP)

Para el MVP no configuramos el "login managed" completo de Spring
(`spring-boot-starter-oauth2-client` con auto-redirect). En su lugar:

- El frontend obtiene el `id_token` de Google mediante su propia integración
  (Google Identity Services / Sign In) o un redirect manual.
- `POST /api/v1/auth/google` recibe ese `id_token` en `GoogleTokenRequest`.
- El backend valida el `id_token` usando el endpoint `tokeninfo` de Google
  (HTTP GET público, sin secret) o verificando la firma JWKS.
- Esta aproximación no requiere `client_secret` en el backend para verificar el id_token
  y simplifica el flujo MVP. Si más adelante queremos el redirect-managed de Spring,
  es compatible con el diseño (se puede añadir sin romper los endpoints).

## 4. Diseño JWT

### 4.1 Access token

- **Uso:** autorización de endpoints protegidos (`/api/**`).
- **Algoritmo de firma:** HS256 (secreto simétrico configurable). Elegido por simplicidad
  en un MVP self-hosted; documentado como candidato a RS256 en producción multi-instancia.
- **Expiración:** 15 minutos.
- **Claims:** `sub` (user id UUID), `email`, `name`, `iat`, `exp`, `iss`, `aud` opcional,
  y `typ` = `access`.

### 4.2 Refresh token

- **Uso:** renovar el access token vía `POST /auth/refresh`.
- **Algoritmo de firma:** HS256 (mismo secreto).
- **Expiración:** 7 días.
- **Claims:** `sub`, `iat`, `exp`, `typ` = `refresh`, `email`.

### 4.3 Tabla de claims

| Claim | Access | Refresh | Descripción                          |
|-------|:------:|:-------:|--------------------------------------|
| `sub` | ✅     | ✅      | UUID del usuario                     |
| `email` | ✅   | ✅      | Email del usuario                    |
| `name` | ✅    |         | Nombre mostrado                      |
| `iat` | ✅     | ✅      | Issued at (epoch seconds)            |
| `exp` | ✅     | ✅      | Expiration (epoch seconds)           |
| `typ` | ✅     | ✅      | `access` o `refresh`                 |
| `iss` | ✅     |         | Emisor (opcional, `neonvibe`)        |

### 4.4 Librería

- **jjwt** (`io.jsonwebtoken:jjwt-api/impl/jackson:0.12.x`). Si surgiera cualquier
  problema de compatibilidad, fallback a los beans `JwtEncoder`/`JwtDecoder` de Spring
  (basados en Nimbus) — se documentará la elección en la review.

## 5. Entidad User

| Campo      | Tipo        | Constraints                                | Notas                          |
|------------|-------------|--------------------------------------------|--------------------------------|
| id         | UUID        | PK, generado por app (`@UuidGenerator`)    | Clave primaria                 |
| email      | varchar     | NOT NULL, UNIQUE                           | Identificador de login         |
| name       | varchar     | NOT NULL                                   | Nombre mostrado                |
| avatar_url | varchar     | NULL                                       | Foto de perfil de Google       |
| google_id  | varchar     | UNIQUE, NULL                               | Identificador `sub` de Google  |
| created_at | timestamptz | NOT NULL, default now()                    | Audit                          |
| updated_at | timestamptz | NOT NULL, default now()                    | Audit                          |

- Tabla `users` (nombre `user` es reservado en PostgreSQL).
- `google_id` se mantiene nullable y único por si en el futuro hay login local/email (no en
  este MVP). La tabla se relacionará con `PlayQueue`, `Playlist`, `PlayHistory`, `Favorite`
  mediante `user_id` (UUID) en Fase 2 — ahora solo se define `users` y la columna referida
  se añadirá en las migraciones de Fase 2.

## 6. Arquitectura de Seguridad (Spring Security)

### 6.1 Filtro

```
Request ──► CorsFilter ──► JwtAuthenticationFilter ──► AuthorizationFilter ──► Controller
                                 │
                                 ├─ Sin/Token inválido → continúa sin auth
                                 └─ Token válido → SecurityContext = UserPrincipal
```

- `JwtAuthenticationFilter` extiende `OncePerRequestFilter`, lee el header
  `Authorization: Bearer <token>`, valida el token y, si es un **access** token válido,
  carga el `UserPrincipal` en el `SecurityContext`.
- Si no hay token o no es válido, simplemente continúa la cadena (la decisión de 401 la
  toma el `AuthorizationFilter` al evaluar las reglas de autorización por ruta).

### 6.2 Rutas públicas vs protegidas

| Ruta                | Acceso          | Notas                                |
|---------------------|-----------------|--------------------------------------|
| `/actuator/health`  | Pública         | Health check sin auth                |
| `/api/v1/auth/**`   | Pública         | login, refresh (login/me no)          |
| `/error`            | Pública         | Manejo de errores de Spring          |
| `/api/**`           | Autenticada     | Requiere JWT válido (futuras fases)   |
| `/actuator/**`      | Autenticada     | Sensible; solo health es público      |

Nota: `/api/v1/auth/**` pública cubre `POST /auth/google`, `POST /auth/refresh` y
`GET /auth/me` (me autentica por Bearer manualmente aunque la ruta sea pública, para
devolver 401 claro cuando falta token).

### 6.3 Configuración

- `@EnableWebSecurity` + `SecurityFilterChain`.
- `SessionCreationPolicy.STATELESS`.
- CSRF deshabilitado (API stateless con Bearer header).
- CORS integrado reutilizando `CorsConfig` existente (`corsConfigurationSource` bean).
- `JwtAuthenticationFilter` registrado antes de `UsernamePasswordAuthenticationFilter`.
- Sin formulario HTTP básico ni login por defecto.

### 6.4 Password encoder

No aplica para login OAuth. Se añade un bean `PasswordEncoder` (`BCryptPasswordEncoder`)
documentado como "disponible para futuro auth local por email" (no se usa en este MVP).

## 7. Almacenamiento de tokens

- **Decisión:** `Authorization: Bearer <access_token>` en el header para endpoints
  protegidos. No cookies.
- El frontend (Fase 4+) es responsable de guardar access/refresh tokens (p. ej.
  `localStorage`/`sessionStorage` o memoria) y enviar el header en cada request.
- Sin decisión de almacenamiento cliente en este repo (backend only).

## 8. Flujo de Refresh

### 8.1 Contrato

`POST /api/v1/auth/refresh`
- Body: `{ "refresh_token": "<refresh_token>" }`
- Valida el refresh token (firma + expiración + `typ == refresh`).
- Carga el usuario por `sub`.
- Devuelve un **nuevo** access token (y el mismo refresh token; sin rotación en MVP).
- Si el refresh token ha expirado o es inválido → `401`.

### 8.2 Estrategia de rotación

- **MVP:** sin rotación. El refresh token es reutilizable hasta que expire (7 días).
- **Nota para producción:** considerar rotación + revocación (bloqueo de refresh) en una
  fase posterior.

## 9. Manejo de errores

| Situación                  | Código | Cuerpo (contrato estándar)                         |
|----------------------------|--------|----------------------------------------------------|
| Token JWT ausente          | 401    | `{error:"unauthorized", message, timestamp}`       |
| Token JWT inválido/expirado| 401    | `{error:"unauthorized", message, timestamp}`       |
| Refresh token inválido     | 401    | `{error:"unauthorized", message, timestamp}`       |
| id_token de Google inválido| 401    | `{error:"unauthorized", message, timestamp}`       |
| Validation de @Valid       | 400    | `{error:"validation_error", message, timestamp}`   |
| Usuario no encontrado      | 404    | `{error:"not_found", message, timestamp}`          |
| Error de servidor          | 500    | `{error:"internal_error", message, timestamp}`     |

- Los `401` de seguridad se emiten con un `AuthenticationEntryPoint` personalizado que
  devuelve el JSON estándar (no la página por defecto de Spring).
- Errores de validación y servidor reutilizan `GlobalExceptionHandler` existente.
- Se añade `InvalidTokenException` (o similar) y su manejo si la capa de servicio la lanza.

## 10. Modo dev/mock sin credenciales reales de Google

- Clave `neonvibe.auth.google.enabled`.
  - `true` (default/prod): valida `id_token` contra Google (`tokeninfo` o JWKS).
  - `false` (dev/test): acepta cualquier token no vacío y crea/actualiza un usuario de
    prueba con email derivado, para poder desarrollar sin credenciales reales.
- `application-dev.yml` y `application-test.yml` fijan `enabled: false`.
- Documentado claramente en el plan, la review y la configuración.

## 11. Configuración (application.yml)

```yaml
neonvibe:
  jwt:
    secret: ${JWT_SECRET:neonvibe-dev-secret-change-me}
    access-token-expiration-ms: 900000        # 15 min
    refresh-token-expiration-ms: 604800000    # 7 días
  auth:
    google:
      enabled: ${GOOGLE_AUTH_ENABLED:true}
      tokeninfo-url: https://oauth2.googleapis.com/tokeninfo
```

- El secreto JWT **nunca** se hardcodea en archivos versionados: siempre vía
  `${JWT_SECRET:...}` con un placeholder claramente marcado para dev.
- `application-dev.yml` y `application-test.yml` sobreescriben `google.enabled=false`.

## 12. Lista de archivos a crear/modificar

### Crear (main)

```
neonvibe/backend/src/main/java/com/neonvibe/
├── config/SecurityConfig.java
├── domain/User.java
├── repository/UserRepository.java
├── service/AuthService.java
├── service/UserService.java
├── security/JwtTokenProvider.java
├── security/JwtAuthenticationFilter.java
├── security/UserPrincipal.java
├── security/CustomAuthenticationEntryPoint.java  (o dentro de SecurityConfig)
├── controller/AuthController.java
├── dto/GoogleTokenRequest.java
├── dto/RefreshTokenRequest.java
├── dto/AuthResponse.java
├── dto/UserResponse.java
└── exception/InvalidTokenException.java (si aplica)
```

### Crear (resources / db)

```
neonvibe/backend/src/main/resources/db/migration/V2__add_users_table.sql
```

### Modificar

```
neonvibe/backend/pom.xml                              (añadir jjwt + oauth2-client)
neonvibe/backend/src/main/resources/application.yml   (sección neonvibe.jwt / auth.google)
neonvibe/backend/src/main/resources/application-dev.yml (google.enabled=false)
neonvibe/backend/src/main/resources/application-test.yml (google.enabled=false)
```

### Crear (test)

```
neonvibe/backend/src/test/java/com/neonvibe/
├── security/JwtTokenProviderTest.java
├── controller/AuthControllerTest.java
├── service/AuthServiceTest.java
├── repository/UserRepositoryTest.java
└── config/SecurityConfigTest.java
```

### Documentación

```
neonvibe/docs/specs/fase-1-auth-spec.md   (este archivo)
neonvibe/docs/plans/fase-1-auth-plan.md   (plan)
neonvibe/docs/reviews/fase-1-auth-review.md (review)
```

## 13. Criterios de aceptación

1. `./mvnw clean compile` — 0 errores.
2. `./mvnw test` — todas las pruebas pasan (mínimo 4 clases de test).
3. `POST /auth/google` (modo mock) devuelve `AuthResponse` con access + refresh token.
4. `GET /auth/me` con Bearer válido devuelve `UserResponse`.
5. `GET /auth/me` sin token devuelve 401 con el contrato JSON estándar.
6. Rutas públicas (`/actuator/health`) accesibles sin token; `/api/**` protegidas.
7. Secreto JWT externalizado (nunca hardcodeado); `enabled=false` en dev/test documentado.
8. Solo DTOs expuestos en controllers; constructor injection obligatoria.
