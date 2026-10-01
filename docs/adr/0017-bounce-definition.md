# 0017. What counts as a bounce, and where that is defined

- Status: Accepted
- Date: 2026-10-01

## Context
`analytics.sessions` carried `is_bounce` as a stored generated column, `pageviews <= 1`. That makes a visitor who
landed on one page and signed up a bounce: the same dashboard would report the conversion and call the visit a
failure. For a one-page site that converts well, the headline bounce rate would be close to 100% and wrong in the
way that matters, because nobody can tell from the number that it is wrong.

The established definition is engagement-based. Plausible marks a session as a bounce unless the visitor saw a
second page or triggered a custom event; in its ingestion code a session leaves the bounce state when
`pageviews >= 2 or (event.interactive? and not pageview?)`. Google's Universal Analytics drew the same line with
interaction events. Both also offer an escape hatch for events that are not real engagement — Plausible's
`interactive: false`, Universal Analytics' `nonInteraction`.

Nothing reads `is_bounce` yet: the api service queries visitors, visits and pageviews only. The definition is
therefore free to change today and expensive to change later.

## Decision
- **A session bounces when the visitor did nothing meaningful: fewer than two pageviews and no custom event.**

  ```sql
  is_bounce = (pageviews < 2 and events = pageviews)
  ```

  This mirrors Plausible's rule, including the case it handles that a plain `events <= 1` would get wrong: a
  session holding a single custom event and no pageview (the visitor returns after the session window and clicks
  something) is engagement, not a bounce.
- **Every custom event counts as engagement**, with no per-event opt-out for now. An equivalent of
  `interactive: false` would cost a tracker option inside the 1 KB budget, a field on the event contract and a
  counter column on `sessions`; it is worth adding when real customer instrumentation shows the need, not before.
- **The definition lives in the `analytics.api_sessions` view, not in a stored column.** The generated column is
  dropped. `pageviews` and `events` are measured facts and belong in the row; whether that constitutes a bounce is
  a reporting judgement and belongs in the contract the api reads.

## Consequences
- Redefining a bounce later costs one view migration. It rewrites no partition, keeps the view's columns, types and
  privileges, and therefore does not break the read contract (compare `ALTER COLUMN ... SET EXPRESSION`, which
  rewrites every partition of a 24-month table under an exclusive lock).
- The definition applies to historical data, so the time series stays comparable across a redefinition. The
  trade-off is that numbers for past periods can move when the definition changes. A value frozen at write time —
  what Plausible does — avoids that at the cost of a silent break in the series instead, and a consistent series is
  the more useful property for a product whose headline claim is accuracy.
- A site that fires a custom event on every pageview (an A/B exposure event, a `page_loaded` event) will see a
  bounce rate of zero, and nothing warns its owner. This is the known cost of the default, and the reason the
  escape hatch above is on the table. It is defensible here because a custom event in this product is a goal event
  by definition, meaning something the site owner chose to call a conversion.
- Bounce rate is computed at query time as `avg(is_bounce::int)` over the view: two integer comparisons per row,
  on a table that is several times smaller than `events` (see [ADR 0007](0007-events-sessions-and-additive-rollups.md)).
- A session with no pageview at all is not a bounce, but it is also not an entrance in the classic sense. A report
  that wants the classic denominator should restrict itself to sessions with `pageviews > 0`.
- `duration_seconds` stays a stored generated column: it is arithmetic over two timestamps, not a definition that
  will be argued about again.
