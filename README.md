# NeonVibe

> Tu servidor de música personal, con alma cyberpunk.

NeonVibe es un reproductor de música self-hosted diseñado para quienes tienen su propia biblioteca digital y quieren una experiencia moderna, rápida y visualmente inmersiva — sin depender de servicios de streaming corporativos.

---

## Características

- **Biblioteca propia:** Escaneo en tiempo real de tu colección local (MP3, FLAC, AAC, OGG, WAV).
- **Experiencia neón:** Interfaz inspirada en cyberpunk con modo oscuro predeterminado y modo claro alternativo.
- **Reproductor avanzado:** Cola persistente, historial de reproducción, shuffle, repeat, crossfade.
- **Vista "Now Playing":** carátula grande, ambiente neón animado, controles y acciones (favorito, añadir a playlist, letras, radio, visualizador, descarga).
- **Calidad seleccionable:** transcodificación AAC on-demand (Original · 320 · 192 · 128) con caché.
- **Búsqueda instantánea:** filtros por texto en biblioteca, álbumes y artistas (índices `pg_trgm`).
- **Estadísticas:** tiempo escuchado, actividad, top de artistas/canciones/álbumes/géneros y por hora.
- **Notificaciones nativas:** Web Push (VAPID), con aviso al completar un escaneo.
- **Playlists:** Crea, edita y comparte tus playlists.
- **Letras en vivo:** Sincronización con LRCLIB (con caché local).
- **Carátulas automáticas:** Descarga desde múltiples fuentes (iTunes, MusicBrainz, Last.fm) con caché local y subida manual.
- **Radio por similitud:** Descubre música de tu biblioteca basada en lo que estás escuchando.
- **Scrobbling:** Integración con Last.fm (conecta tu cuenta desde Ajustes).
- **Historial:** Página con tus reproducciones recientes.
- **Descargas offline:** Descarga canciones, álbumes o playlists y gestiónalas desde Ajustes.
- **Sesiones robustas:** Refresh token con rotación y revocación.
- **Multi-usuario:** Autenticación con Google OAuth y allowlist de cuentas/administradores.
- **PWA:** Instalable en mobile como aplicación nativa.
- **Web & Mobile:** Diseño responsive pensado primero para teléfonos.

---

## Stack Tecnológico

| Capa | Tecnología |
|---|---|
| Backend | Java 21 + Spring Boot 3.4.x |
| Frontend | React 18 + TypeScript + Vite + Tailwind CSS |
| Base de datos | PostgreSQL 15+ |
| Auth | Google OAuth2 + JWT |
| Tiempo real | WebSockets (STOMP) |
| Media | FFmpeg (transcodificación AAC on-demand) |
| Push | Web Push + VAPID |
| Build | Maven (backend) + pnpm (frontend) |

---

## Estructura del Proyecto

```
neonvibe/
├── backend/          # Spring Boot (Java 21, Maven)
│   └── src/main/...
├── frontend/         # React + Vite + pnpm
│   └── src/...
├── deploy/           # Scripts de build y despliegue
├── docs/             # Especificaciones, planes, arquitectura y ADRs
├── AGENTS.md         # Guía para agentes de código (stack, decisiones, convenciones)
├── CHANGELOG.md      # Histórico de cambios por versión
├── CONTRIBUTING.md   # Guía para contribuir
├── SECURITY.md       # Política de seguridad
└── README.md         # Este archivo
```

Documentación de referencia:
- **Arquitectura:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- **Despliegue:** [docs/DEPLOY.md](docs/DEPLOY.md)
- **API:** snapshot OpenAPI en [docs/api/openapi.yaml](docs/api/openapi.yaml) (live en `/swagger-ui` en dev)

---

## Desarrollo

### Requisitos

- Java 21 JDK
- Node.js 20+ + pnpm
- PostgreSQL 15+
- Maven 3.9+

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
pnpm install
pnpm dev
pnpm test    # Vitest + Testing Library (jsdom)
```

### Tests

```bash
cd backend && ./mvnw test        # JUnit 5 + Mockito (+ PostgreSQL vía Testcontainers)
cd frontend && pnpm test         # Vitest + Testing Library (jsdom)
```

El backend tiene **541 tests** (cobertura JaCoCo, build falla por debajo de
**líneas ≥ 82%** y **ramas ≥ 63%**) y el frontend **109 tests**. El CI de GitHub
Actions ejecuta ambos y empaqueta el JAR.

---

## Despliegue

NeonVibe se despliega como un JAR ejecutable en el servidor Debian Trixie vía systemd. Cloudflare gestiona el dominio y proxy.

```bash
./deploy/build.sh          # genera dist/neonvibe.jar (frontend embebido)
sudo ./deploy/setup.sh     # primera vez en el servidor (Java, PostgreSQL, systemd, secrets)
sudo ./deploy/deploy.sh    # despliega y reinicia el servicio
```

Guía completa paso a paso: **[docs/DEPLOY.md](docs/DEPLOY.md)**

---

## Roadmap

### v0.1 — MVP (completado)
Auth, streaming, playlists, carátulas, letras, scanner real-time, visualizador, PWA.

### v0.2
- [x] Last.fm scrobbling
- [x] Radio por similitud
- [x] Sync multi-dispositivo vía WebSocket
- [x] Compartir playlists
- [x] Descarga offline (PWA) y gestión de descargas
- [x] Background playback (MediaSession API)
- [x] Refresh token con rotación/revocación
- [x] UI de scanner (admin) y página de historial
- [x] Quality selector / transcodificación (cache-transcode AAC) — v0.3
- [x] Notificaciones nativas (Web Push + VAPID) — v0.3

### v0.3 (completada)
- [x] Búsqueda avanzada fulltext (pg_trgm)
- [x] Notificaciones nativas (Web Push)
- [x] Quality selector / transcodificación (AAC cacheada)
- [x] Stats y analytics personales

### v0.4
Audiolibros/podcasts y Chromecast/Bluetooth.

---

## Licencia

MIT — ver [LICENSE](LICENSE).

---

*NeonVibe — servidor de música personal.*
