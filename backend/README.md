# AETHERON API

Ktor service for the optional AETHERON AI assistant.

## Routes

- `GET /health` — process health only; it does not verify provider credentials.
- `POST /api/v1/ai/chat` — authenticated prompt relay to the OpenAI Chat Completions API.

## Required server configuration

- `PORT` — listen port (default `8080`).
- `AETHERON_API_TOKEN` — private bearer token, at least 32 characters. Requests must send `Authorization: Bearer <token>`.
- `OPENAI_API_KEY` — OpenAI secret. It must exist only in the server's secret manager/environment.
- `OPENAI_MODEL` — optional model name (default `gpt-4o-mini`).

The API deliberately returns `503` when either required secret is absent or the AETHERON token is too short. Do not put either secret in the APK, source repository, public issue, or client-side JavaScript. A mobile app needs a real user/session authentication flow or a trusted API gateway; a shared static token embedded in an APK is not secure.

## Request limits and safety

- Maximum HTTP request body: 48 KiB, enforced while receiving the body.
- Prompt: 8,000 characters maximum.
- User-approved page text: 24,000 characters maximum.
- Page title: 300 characters maximum.
- Page URL: 2,048 characters maximum and HTTP/HTTPS only.
- Per-process IP limiter: 20 requests per 60 seconds with strictly bounded key storage.
- Upstream connection timeout: 8 seconds; request timeout: 45 seconds.
- Upstream response is capped at 1 MB.
- Client-supplied `X-Forwarded-For` is ignored.
- Page text is explicitly labelled untrusted context in the prompt.
- Provider errors and credentials are not returned to clients.

The IP limiter is process-local. Production deployments must add HTTPS, a trusted reverse proxy or API gateway, distributed rate limiting, authenticated user/session identity, monitoring, abuse controls and secret rotation. Configure proxy IP extraction only after defining trusted proxy addresses.

## Build and run locally

```bash
gradle :backend:test
gradle :backend:installDist
AETHERON_API_TOKEN='use-a-random-secret-of-at-least-32-characters' \
OPENAI_API_KEY='your-server-side-key' \
PORT=8080 backend/build/install/backend/bin/backend
```

## Container

```bash
gradle :backend:installDist
docker build -f backend/Dockerfile -t aetheron-api .
docker run --rm -p 8080:8080 \
  -e AETHERON_API_TOKEN \
  -e OPENAI_API_KEY \
  aetheron-api
```

The Android client currently does not call this API. The service is source-complete for its current endpoint, but it is not deployed and no live AI requests can succeed until secrets, HTTPS hosting, and client authentication/integration are configured. Page content must only be collected and sent after explicit user consent.
