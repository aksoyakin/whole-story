# 0018. Authentication and sessions

- Status: Accepted
- Date: 2026-10-01

## Context
The dashboard needs accounts: registration, login, logout and password reset. Three things have to be settled
before any of it is written — where the session lives, how passwords are stored, and which layer answers for CSRF.

The shape of the system constrains the answer. The browser only ever talks to the Next application; `api` is not
reachable from the internet and is called from the Next server over the internal Docker network
(see [ADR 0011](0011-backend-for-frontend.md)). Authorization questions, however, are business rules — "is this
user a member of the organization that owns this site?" — and they belong next to the aggregates that hold them.

## Decision
- **The session is owned by `api`: Spring Session backed by Redis.** The session identifier is opaque and travels
  in a cookie that is `HttpOnly`, `Secure`, `SameSite=Lax` and host-only on `app.wholestory.world`. The browser
  sends it to the Next server, which forwards it on its server-side call to `api`.
  - Authorization therefore stays inside the modular monolith, where the rules and the data are.
  - A session can be revoked immediately, which a signed token cannot.
  - Redis is already part of the stack for sessionization, rate limiting and the daily salt, so nothing new is
    introduced.
  - *Rejected: JWT access and refresh tokens.* There is no third-party client and no mobile app, so statelessness
    buys nothing here, while revocation would need a denylist in Redis anyway — the state comes back, without the
    ability to expire a session centrally.
  - *Rejected: a session owned by the Next application, calling `api` with a service token.* `api` would no longer
    know who is asking, so every authorization rule would move into the frontend.
- **Passwords are hashed with bcrypt at strength 12**, through Spring Security's `DelegatingPasswordEncoder`, so
  every hash is stored with its algorithm prefix (`{bcrypt}$2a$12$…`). OWASP prefers Argon2id; the prefix is what
  turns that into a later decision rather than a data migration: Argon2id joins the encoder map, becomes the id
  used for encoding, and every hash already stored keeps verifying. Re-hashing an old password on the next
  successful login needs a `UserDetailsPasswordService`, which is deliberately not wired yet — there is nothing to
  upgrade from. Choosing Argon2id today would mean a BouncyCastle dependency and memory and parallelism
  parameters that should be measured on the actual VPS first.
- **No composition rules on passwords**, only a length range of 8 to 200 characters, following NIST SP 800-63B:
  length is what matters, and forced character classes mostly produce predictable substitutions.
- **The raw password never becomes a loggable string.** It is carried to the hashing port inside a `Password`
  value object whose `toString` hides the value, so it cannot reach a log line or an exception message.
- **CSRF protection is disabled in `api`, and enforced at the browser-facing boundary instead.** `api` is not
  routed from the internet and receives no requests from a browser, so there is no cross-site request for it to
  defend against. The boundary is the Next application: mutations go through server actions, which verify the
  request origin, and the session cookie is `SameSite=Lax`.
- `GET /api/auth/me` returns the current user and organization, which is what the Next server needs to render the
  dashboard shell and to redirect an anonymous visitor.

## Consequences
- `api` now depends on Redis, which it did not before. A Redis outage logs everyone out rather than degrading
  quietly. The alternative, a JDBC session store, would put a session write into the transactional database on
  every request; for this product an outage that forces a new login is the cheaper failure.
- The cookie is host-only on `app.wholestory.world`, so the marketing site on the apex domain never receives it
  and the tracker endpoint never sees a credential.
- Disabling CSRF in a Spring application is a finding in any review unless the reason is written down. It is only
  defensible while `api` stays off the internet; exposing it later means turning CSRF protection back on in the
  same commit.
- Authentication lives entirely in `api`, so the Next application holds no auth logic beyond forwarding the cookie
  and redirecting on a 401.
- Session fixation is handled where the session is created: the identifier is rotated when an anonymous session
  becomes an authenticated one.
