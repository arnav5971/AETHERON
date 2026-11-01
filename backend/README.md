# AETHERON API

A small Ktor service used by AETHERON's optional AI assistant.

## Routes

- `GET /health` — service health.
- `POST /api/v1/ai/chat` — forwards a user prompt and optional user-approved page context to the OpenAI Chat Completions API.

Example request:

```json
{
  "message": "Summarize this page",
  "pageTitle": "Example",
  "pageUrl": "https://example.com",
  "pageText": "Text explicitly selected by the user for AI analysis."
}
```

## Configuration

- `PORT` — HTTP port (default 8080).
- `OPENAI_API_KEY` — server-side secret. Never put this in the Android app or repository.
- `OPENAI_MODEL` — optional model name (default `gpt-4o-mini`).

The service returns 503 when no provider key is configured. The endpoint limits prompt and page-context sizes and applies a basic per-IP in-memory rate limit. Production deployment should additionally use HTTPS termination, a trusted reverse proxy, persistent distributed rate limiting, monitoring, and an abuse-control policy. The in-memory limiter is per process and is not a substitute for an edge/API gateway.

## Run locally

```bash
gradle :backend:installDist
OPENAI_API_KEY=your-server-side-key PORT=8080 backend/build/install/backend/bin/backend
```

## Container

Build the distribution first, then build the container from the repository root:

```bash
gradle :backend:installDist
docker build -f backend/Dockerfile -t aetheron-api .
```

The Android client must not send an OpenAI API key. It should call the deployed AETHERON API over HTTPS. Page content must only be collected and sent after explicit user consent.
