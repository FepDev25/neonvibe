# Security Policy

## Supported Versions

Only the latest release on the `main` branch receives security fixes. There are
no long-term support (LTS) releases.

| Version | Supported |
|---|---|
| `main` (latest) | Yes |
| Older releases | No |

## Reporting a Vulnerability

NeonVibe is a personal, self-hosted project. If you find a security
vulnerability, report it privately instead of opening a public issue.

- **Email:** report via a private channel to the maintainer (check the repository
  owner on GitHub).
- **Response time:** you will receive an acknowledgement within 72 hours, and a
  first assessment (accepted, declined, or needs more detail) within one week.
- **Do not** include live secrets, session tokens, or personal data in the report.

When reporting, include:

1. A description of the vulnerability and its impact.
2. The affected component (backend, frontend, scanner, WebSocket, auth, ...).
3. Steps to reproduce, or a minimal proof of concept.
4. Versions involved (JAR/release and, if known, the commit).

## Security Model

This project is deployed as a self-hosted server exposed through a Cloudflare
tunnel. The following controls are part of its security posture:

- **Authentication:** Google Identity Services on the frontend; the backend
  validates the `id_token` with Google's `tokeninfo` and verifies the `aud`
  claim against `GOOGLE_CLIENT_ID`. Tokens not issued for this application are
  rejected.
- **Authorization:** logins are gated by `ALLOWED_EMAILS`. Accounts outside the
  allowlist receive `403`. An empty allowlist is only acceptable in development.
- **Sessions:** stateless JWT (access + refresh) with short-lived access tokens.
  The signing secret (`JWT_SECRET`) must be unique per deployment.
- **Secrets:** all runtime secrets come from environment variables
  (`/opt/neonvibe/neonvibe.env`). The Google OAuth client secret is kept outside
  the repository. Nothing secret is committed.
- **Production startup guard:** the application refuses to start in the `prod`
  profile without `JWT_SECRET`, `GOOGLE_CLIENT_ID`, and `ALLOWED_EMAILS`.

## Deployment Hardening

- Keep `/opt/neonvibe/neonvibe.env` mode `0600` and out of version control.
- Restrict firewall access to ports `80`/`443` (Cloudflare) and keep PostgreSQL
  bound to localhost.
- Update the deployed JAR regularly and review `journalctl -u neonvibe` after
  each deployment.
