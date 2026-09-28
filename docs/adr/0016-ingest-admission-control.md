# 0016. Admission control at ingest

- Status: Accepted
- Date: 2026-09-29

## Context
`ingest` is the only service exposed to the internet and it accepts an unauthenticated POST from every visitor's
browser. Two things have to be kept out: events for domains that are not registered, and enough traffic from one
source to make a site's numbers wrong. Inflated numbers are worse than missing ones, because nobody can tell.

## Decision
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
  `rate_limited`, `malformed_payload`, …), so a site that is misconfigured or under attack is visible rather than
  silently absent from the dashboard.
- Rejections answer `400`, except the rate limit which answers `429`.

## Consequences
- The rate limit runs after the site lookup and the visitor hash, because it needs that hash. It is therefore not
  the cheapest possible rejection; that is the trade-off for not punishing visitors who share an address.
- An attacker who varies the User-Agent gets a fresh bucket for every variation. The limit raises the cost of
  inflating a site's numbers, it does not remove it. A per-site cap belongs with the monthly event limits of
  Site Management, where an organization's plan is known.
- A fixed one-minute window allows a burst of up to twice the limit around a minute boundary. A sliding window
  would cost more Redis work than that inaccuracy is worth at this scale.
- One extra Redis increment per accepted event, plus an expiry on the first event of each window.
