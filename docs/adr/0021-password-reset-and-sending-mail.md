# 0021. Password reset, and how this product sends mail

- Status: Accepted
- Date: 2026-10-02

## Context
An account needs a way back in when its password is forgotten, and that means this product has to send mail for
the first time. Three things had to be settled: who carries the mail, how a link can be a credential without
becoming one at rest, and what happens to the sessions that were already open.

The volume is the thing that decides the first question. v1 deliberately has no address verification
([scope](../../README.md)), so password reset is the only mail there is: a handful a day. The mid-term plan does
include e-mail reports, which is a different product with different needs.

## Decision

### SMTP, through the domain's own mail host
- `api` sends with Spring's `JavaMailSender` over the mail host that already serves this domain, on **port 465**.
  No transactional mail provider.
  - What Resend, Postmark or SES actually sell is deliverability reporting: bounce and complaint webhooks,
    suppression lists, templates. None of that is used by a product that sends one kind of mail to people who
    just asked for it, and SPF, DKIM and DMARC are already set for this domain.
  - *Rejected for now, not forever.* The day mail becomes a product surface — reports going out to people who
    did not ask for one just now — "who did not receive this" is a question SMTP cannot answer, and the decision
    should be taken again. The adapter behind `PasswordResetMailer` is the only thing that has to change.
- **Port 465 is implicit TLS**, configured as `mail.smtp.ssl.enable`. Pairing that port with `starttls.enable`
  is the standard mistake and simply hangs.
- **Certificate identity is checked explicitly** (`mail.smtp.ssl.checkserveridentity`). Without it TLS is
  encryption without the assurance that the mailbox password is going to the right server. It is stated rather
  than inherited from a default, and the one place it is switched off is the test, where the server's certificate
  names no host at all.
- **Timeouts are set, all three of them.** Jakarta Mail waits forever by default, so an unreachable mail host
  would hold a thread until it is restarted. This is the least interesting decision here and the most likely to
  be the one that matters in production.

### The link is a credential, so it is stored as a hash
- A reset token is 32 random bytes, base64url, in the query string of a link to the dashboard. Only its SHA-256
  reaches the database, in `identity.password_reset_tokens`.
  - Unsalted and uniterated, unlike a password: there is no dictionary to stretch against 256 random bits, and
    the value stops existing within the hour.
- It expires after **one hour** and can be followed **once**. Following one invalidates every other link
  outstanding for that account.
- Unknown, spent, expired and not-a-token all answer `400`, identically. Which of the four it is would be
  information for whoever is holding the link, and it is never their business.
- **The mail is sent through the outbox, and the event carries no secret.** The publication registry serialises
  an event into `platform.event_publication`, so a token in the event would be a working credential at rest in
  a table — exactly what hashing it prevents. The event holds the user id and nothing else; the token is minted
  by the listener at the moment of sending, and the address is read from the user then.
- The listener runs in its own transaction and is allowed to fail: an unreachable mail host rolls the token back
  and leaves the publication incomplete, and the resubmission below comes back to it with a fresh token.

### Asking for a link says nothing about who has an account
- `POST /api/auth/password-reset/request` answers `204` for a registered address, an unregistered one and a
  malformed one alike. The web form says "if that address has an account" for the same reason.
- **Three links per account per hour**, counted in Redis. Without a cap the endpoint posts mail into somebody
  else's inbox on demand and spends the mail host's allowance doing it. The counter is only ever reached for
  accounts that exist, so refusing cannot be used to enumerate addresses either.

### A reset ends every session
- Resetting a password deletes **all** of that person's sessions, through Spring Session's index on the principal
  name (`spring.session.data.redis.repository-type: indexed`).
- This is the point of the feature. The usual reason to reset a password is that somebody else knows it, and
  without this they would keep the session they already have for the full fourteen days
  ([ADR 0018](0018-authentication-and-sessions.md)).
- The index is also why the bean exists at all: without the setting, `FindByIndexNameSessionRepository` has no
  implementation and the context fails to start. A deployment that forgets it cannot start up quietly wrong.

### Incomplete publications are resubmitted on a schedule
[ADR 0009](0009-transactional-outbox-with-spring-modulith.md) resends on startup, which was the only retry there
was: anything that failed sat in the table until the next deploy, and in production that is precisely what
happened to a `SiteRegistered` announcement. A scheduled job now resubmits publications older than five minutes,
which closes that gap for the Kafka side as well as for mail.

## Consequences
- Deliverability rests on one shared mail host's reputation, and nothing reports a bounce: a reset mail that is
  refused or filed as spam looks exactly like one that arrived. The user-visible recovery is to ask again, which
  is why this is tolerable here and would not be for a report nobody requested.
- The retry can send a second mail with a different link. The earlier link stops working as soon as a later one
  is used, and both die within the hour, so the worst case is two mails and one usable link.
- Resubmitting every five minutes means a consumer can see the same event twice. Both consumers are already
  idempotent by design ([ADR 0006](0006-idempotent-at-least-once-processing.md),
  [ADR 0019](0019-telling-ingest-which-domains-are-tracked.md)).
- Switching the session store to the indexed repository changes the keys it writes, so the sessions open at the
  moment of deployment are not recognised and everyone signs in once more. The same deploy also corrects
  `spring.session.redis.*`, which Boot 4 removed outright: it had been sitting in the configuration doing
  nothing, which is how a namespace that was never applied went unnoticed.
- `api` can now reach the internet, where before it only spoke to the services beside it. It is still not
  reachable *from* the internet.
- The mailbox password is one more secret in the deployment, required rather than optional, so a stack missing it
  refuses to start instead of silently swallowing every reset request.
- Nothing verifies an address at sign-up, so `email_verified_at` stays empty and a reset mail is the first
  evidence that an address exists at all. A typo at registration therefore locks the account out permanently;
  address verification is the feature that fixes it, and it is still out of scope.
- GreenMail runs an SMTP server with TLS inside the test JVM, so the 465 configuration is exercised rather than
  assumed. It was worth it immediately: the first run failed on certificate identity, which is a real property of
  the production setup and not something a mocked mailer would ever have raised.
