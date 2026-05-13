# Kairos Backend

ASP.NET Core controller API for Kairos sync. The first slice provides simple email/password auth, per-device refresh tokens, and user-owned task endpoints backed by PostgreSQL.

## Local Run

```powershell
cd backend
copy .env.example .env
# Edit .env and set POSTGRES_PASSWORD + JWT_SIGNING_KEY before hosting anywhere.
docker compose up --build
```

The API listens on `http://localhost:8080`.

For local development without Docker, run PostgreSQL with the connection string in `src/Kairos.Api/appsettings.Development.json`, then:

```powershell
dotnet run --project src/Kairos.Api/Kairos.Api.csproj
```

The default non-Docker launch profile listens on `http://localhost:5255`.

For machine-specific secrets outside Docker, copy `src/Kairos.Api/appsettings.Local.example.json`
to `src/Kairos.Api/appsettings.Local.json`. The local file is ignored by git and overrides
the committed appsettings files.

## Auth Endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

Email confirmation and outbound email are intentionally disabled for v1.
