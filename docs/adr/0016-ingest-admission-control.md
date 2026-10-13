# 0016. Admission control at ingest

- Status: Accepted
- Date: 2026-09-29

## Context
`ingest` is the only service exposed to the internet and it accepts an unauthenticated POST from every visitor's
browser. Two things have to be kept out: events for domains that are not registered, and enough traffic from one
source to make a site's numbers wrong. Inflated numbers are worse than missing ones, because nobody can tell.

## Decision
- **Payload size.** A request body may be at most `ingest.max-event-bytes` (8 KiB). A tracker payload is a few
  hundred bytes; the headroom covers a long url, a long referrer and custom event props. The limit is applied
  before the body is bound, because a limit checked after the read is not a limit: the body would already be in
  memory. `Content-Length` is rejected when it declares too much, which is the cheapest refusal available, but
  the read itself is capped as well — a chunked request declares no length and a caller may understate the one
  it does declare. Without this, the rate limit below bounded how many events a visitor could send and nothing
  bounded what each one cost, in memory or in a row of `analytics.events`.
- **Origin.** When the request carries an `Origin` header, its host must equal the normalised `data-domain`;
  otherwise the event is rejected. A missing or opaque (`null`) origin is accepted: browsers are inconsistent about
  sending the header on same-origin requests, and a client that is not a browser can put anything there anyway.
  This is a correctness guard against a page on another domain reporting events for someone else's site through a
  real browser, not a security boundary.
- **Rate limit.** One visitor may report 60 events per minute. The bucket is a one-minute counter in Redis, keyed on
  **the visitor hash, not the IP address**. Carrier-grade NAT puts thousands of real people behind one address, so
  an IP bucket would drop genuine visits on a popular site. Protecting the infrastructure from a flood is left to
  the reverse proxy, which is the layer that can reject a request without doing any work.
- The raw IP is never part of a Redis key, in line with [ADR 0005](0005-privacy-by-design.md): it does not leave
  ingest in any form other than the visitor hash and the resolved location.
- Every rejection increments `events.rejected` tagged with its reason (`unknown_domain`, `origin_mismatch`,
  `rate_limited`, `payload_too_large`, `malformed_payload`, …), so a site that is misconfigured or under attack
  is visible rather than silently absent from the dashboard.
- Rejections answer `400`, except the rate limit (`429`) and an oversized body (`413`). Those two get their own
  status because they are the two a site's own developer can act on, and the status says which without them
  having to read our source.

## Consequences
- The size limit is the only check that runs before the request is a known site's at all, so it is also the only
  one an unregistered domain cannot get past cheaply. That ordering is deliberate: it is the check that protects
  the service rather than the numbers.
- A site that legitimately sends more than 8 KiB of props per event is refused, and the only signal is a `413`
  its browser never reports. The limit is therefore configuration rather than a constant, and `events.rejected`
  carries `payload_too_large` so the refusal is visible on our side even though nobody is watching on theirs.
- Capping the read means an oversized request is answered before its body has finished arriving. The connection
  is closed rather than drained, which is the right trade for a fire-and-forget collector.
- The rate limit runs after the site lookup and the visitor hash, because it needs that hash. It is therefore not
  the cheapest possible rejection; that is the trade-off for not punishing visitors who share an address.
- The bucket depends on the client address, which ingest reads from `X-Forwarded-For`. That header is only
  trustworthy because a reverse proxy sets it: measured against production, an event sent with an invented
  `X-Forwarded-For` was counted under the real client address and located there, so the header a caller writes
  reaches neither the visitor hash nor the GeoIP lookup. It follows that ingest must never be reachable except
  through that proxy — and that a load generator can only stand in for many visitors where there is no proxy,
  which is why the local measurement in `docs/performance.md` can do it and nobody else can.
- An attacker who varies the User-Agent gets a fresh bucket for every variation. The limit raises the cost of
  inflating a site's numbers, it does not remove it. A per-site cap belongs with the monthly event limits of
  Site Management, where an organization's plan is known.
- A fixed one-minute window allows a burst of up to twice the limit around a minute boundary. A sliding window
  would cost more Redis work than that inaccuracy is worth at this scale.
- One extra Redis increment per accepted event, plus an expiry on the first event of each window.
