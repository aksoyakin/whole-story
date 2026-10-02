# 0022. Goals and the conversion rate

- Status: Accepted
- Date: 2026-10-02

## Context
A site owner needs to say what counts as success — a signup, a purchase, reaching a thank-you page — and see how
often it happens. Everything needed was already in place: the tracker reports custom events by name, the
processor records them next to pageviews, and `sites.goals` has been in the schema since the first migration.
What was missing was the definition and the report.

The questions worth deciding were where a goal lives in the model, how it is matched, and which table answers.

## Decision

### A goal is its own aggregate
`Goal` references a `SiteId` rather than hanging off the `Site` aggregate. The test was the invariant question:
no rule needs to see all of a site's goals at once, since uniqueness of a target is the index's job exactly as
it is for a domain. The alternative has a measurable cost — `SiteAccess.readableBy` runs on every reporting
request and a single dashboard makes ten of them, so goals inside `Site` would be read every time for no rule.

A goal records **no domain event**. Unlike a site, it changes nothing about what is collected: ingest and
processor have no reason to hear about it. That is also why a goal defined today reports on last month.

### The target is one value object
`GoalTarget` carries a type and a value, which is what the table's check constraint says in SQL: an event name
or a page pattern, never both and never neither. Two nullable fields would let the invalid combination be built
and then rejected by the database; one value object makes it unrepresentable.

Its validation is aimed entirely at goals that could never match. A path without its leading slash, a whole URL,
`/thanks?ref=mail`, an empty event name — each looks reasonable and would count zero for ever, and nobody can
tell "no conversions yet" from "this was never going to work". An event name longer than 120 characters is
refused for the same reason: ingest will not accept one.

### `*` is the wildcard, and SQL's own are escaped
A page goal may use `*`, meaning any part of a path, so `/blog/*` is one goal rather than one per post. It is
translated to a SQL `like` pattern, and `%`, `_` and the escape character are escaped on the way: without that
`/100%-free` would quietly match every path starting with `/100`. The pattern is bound as a parameter; the
goal's text never becomes part of the statement.

One wildcard character and one rule. A distinction between `*` and `**` buys precision nobody asked for.

**`*` does not stop at a slash.** It is not a glob: it stands for any part of a path, including none and
including the separators. Taking `wholestory.world` and the paths it might record, these are the matches, run
against the database rather than reasoned about:

| Goal | Becomes | Matches | Worth knowing |
|---|---|---|---|
| `/pricing` | `/pricing` | `/pricing` | not `/pricing/enterprise` |
| `/docs/*` | `/docs/%` | `/docs/`, `/docs/install`, `/docs/install/next` | **not `/docs` itself** — the trailing slash is required |
| `/docs*` | `/docs%` | `/docs`, `/docs/`, `/docs/install`, `/docs/install/next` | would also take `/docsearch` |
| `/docs/*/next` | `/docs/%/next` | `/docs/install/next`, `/docs/a/b/next` | not `/docs/next` |
| `/100%-off` | `/100\%-off` | `/100%-off` | the per cent sign is escaped, so it matches nothing else |
| `/100*` | `/100%` | `/100%-off`, `/1000-off` | |
| `/a_b` | `/a\_b` | `/a_b` | the underscore is escaped, so not `/axb` |

The second row is the one that will catch people: a goal meant as "the whole blog" written `/blog/*` leaves out
`/blog` itself. `/blog*` covers both.

The two escaped rows are why escaping exists at all. Without it `/100%-off` would read as "starts with `/100`
and ends with `-off`" and `/a_b` as "any single character between a and b" — both would quietly count more than
they say, and a goal that over-counts is worse than one that refuses, because the number still looks plausible.

### Conversions are counted from the events, in one pass
Each goal contributes two expressions to a single query over `api_events`: the distinct visitors who completed
it, and how many times it happened. The denominator of the rate is the period's visitors from `api_sessions`,
which is the same number the summary tile shows, so the page agrees with itself.

This departs from the data model's first sketch, which had the completion count coming from the hourly rollups.
A conversion rate needs the visitors who converted, which is a distinct count and therefore not something a
rollup can hold ([ADR 0007](0007-events-sessions-and-additive-rollups.md)). The rollup could still have supplied
the completion total, but that means two sources for one card and a way for them to disagree — and as soon as
anything is filtered the rollup cannot answer at all ([ADR 0020](0020-reporting-queries.md)).

### Defining them and reporting them are different things
`/api/sites/{siteId}/goals` is the site's configuration; `/api/sites/{siteId}/stats/goals` is a question about
its events. The definitions are owned by Site Management and asked for synchronously through its published
interface, like every other question one context asks another (D-088), then translated into the read side's own
small `GoalDefinition` — Analytics has no domain model and does not borrow another module's vocabulary.

In the web app, goals are managed on a page of their own and the dashboard only reports them. The dashboard is
the page a public share link will render ([ADR 0011](0011-backend-for-frontend.md) keeps its state in the URL for
that), and an add-and-remove form has no business on something readable by anyone with the link.

## Consequences
- The goals report reads `api_events` over the whole period, which puts it in the same class as the top pages
  report: the heaviest query the dashboard makes. One pass covers every goal, so the cost grows with the range
  rather than with the number of goals. It belongs in the load tests rather than in an argument about taste.
- A `like` with a leading wildcard cannot use an index. These predicates narrow a range already selected by
  `(site_id, timestamp)`, which is the same trade the filters make.
- Column aliases are generated per goal (`v0`, `c0`, …), so a site with a very large number of goals would
  produce a very wide query. Nothing caps that yet; goals are defined by hand and realistically number a handful.
- Removing a goal removes the question, not the data: the events stay and re-adding the goal brings the numbers
  back.
- The conversion rate is a share of visitors, so it can only be read against the period shown. A goal completed
  by someone who first visited last week still counts in this week's numerator, because the visitor is counted
  in the period where the event happened.
- Two goals can overlap — `/blog/*` and `/blog/privacy` — and a visitor reaching the latter converts both. That
  is what the owner asked for, and the rates are each a share of visitors rather than parts of a whole, so
  nothing adds up wrongly.
