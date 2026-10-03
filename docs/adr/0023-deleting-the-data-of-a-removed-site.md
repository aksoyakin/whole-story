# 0023. Deleting the data of a removed site

- Status: Accepted
- Date: 2026-10-03

## Context
Removing a site has always been a soft delete: the row is marked, the domain frees up immediately, and
everything collected under it stays exactly where it was. The background job that was supposed to clear it up
was named when the data model was decided and never written, so the product's promise that removing a site
removes its data has not been true.

Two things make this less trivial than a `DELETE`.

The first is ownership. The service that knows a site was removed is `api`; the service that may delete the
rows is `processor`, which owns the `analytics` schema. `api` can read four views and nothing else, by database
privilege ([ADR 0003](0003-schema-ownership-and-database-roles.md)). The purge therefore cannot be a method
call: it has to cross a service boundary.

The second is `identity.password_reset_tokens`, which has the same shape of problem for a different reason.
Nothing has ever deleted a row from it either: following a link and letting it expire both only need the row to
stay and refuse. It grows by one row for every link anyone ever asks for.

## Decision

### The purge runs in the processor, asked for over its own topic
`api` publishes `SitePurgeV1` — the site id and when it was removed — through the transactional outbox
([ADR 0009](0009-transactional-outbox-with-spring-modulith.md)) to a new topic, `site-purge`. `processor`
consumes it and deletes the site's rows from `events`, `sessions`, `page_hourly` and `custom_event_hourly`.

**It is a topic of its own, not a second use of `site-events`.** Reusing that one was the obvious move and it
is wrong for a reason worth writing down: `site-events` is log compacted and keyed on the *domain*, because it
carries the current state of each domain ([ADR 0019](0019-telling-ingest-which-domains-are-tracked.md)). A
purge is not a state, it is a piece of work that has to happen once. Put it on that topic and registering the
same domain again writes a newer record under the same key, and compaction then erases the instruction to
delete the previous site's data — a purge that is silently cancelled by an unrelated event.

**`site-purge` is compacted too, keyed on the site id.** The alternative, ordinary time-based retention, drops
the record if the processor is away longer than the retention window, which for a deletion is the wrong
failure. A site id is never reused, so compaction can only ever replace a site's record with its own, and the
work survives for as long as the topic does. The cost is one small record per site ever removed, kept for
ever — the same trade already accepted for `site-events`.

### Removing a site records two domain events
`Site.remove` records `SiteRemoved` **and** `SiteDataPurgeRequested`. They are two statements to two services:
one frees the domain for `ingest`, the other releases the data for `processor`, and they travel on topics with
different keys and different guarantees. Spring Modulith maps one event to one message — `mapping` and `route`
each yield a single target — so one event could not have produced both. Both are recorded inside the existing
idempotency guard, so removing an already removed site still announces nothing.

### The deletion is chunked, and picks rows by primary key
Rows go five thousand at a time, each chunk its own transaction. A single statement over a busy site would hold
locks and pile up WAL for long enough to miss the consumer's poll deadline, which drops it from the group,
rebalances, retries the same batch and starts again — a purge that can never finish.

Chunks are selected by primary key rather than by `ctid`. `events` and `sessions` are partitioned, and a `ctid`
is only unique within one partition, so matching on it through the parent table could delete a row that was
never selected. Table and key names are constants in the code and never come from the message.

### Expired reset tokens are deleted nightly
A scheduled job in `api` deletes from `identity.password_reset_tokens` where the expiry has passed. Expiry is
the only condition: a spent link is refused by the aggregate and dies within the hour regardless
([ADR 0021](0021-password-reset-and-sending-mail.md)), so every row reaches this eventually and there is no
second rule to keep in step with the first.

## Consequences
- The product's promise now holds: removing a site removes what was collected for it, a short while later.
- The purge is idempotent, which is what makes redelivery and a replayed topic harmless
  ([ADR 0006](0006-idempotent-at-least-once-processing.md)). An integration test purges twice and seeds a
  second site to prove the deletion does not reach across to it.
- The first deployment reads the topic from the beginning, so sites removed before this existed are cleaned up
  too, without anything being run by hand.
- A purge that fails is retried with backoff like any other batch, which means a permanently failing one would
  hold up the purges behind it. That is the same trade this service already makes for event processing, and the
  queue it blocks is a slow one.
- `sites.sites` keeps its soft-deleted row. The domain is already free through the partial unique index, and
  the row is the only remaining record that the site existed; it is one row per site ever registered.
- Deleting a topic is still a two-step operation, and there are now three to recreate rather than two
  (`docs/deployment.md`).
