# 0015. Referrer source classification

- Status: Accepted
- Date: 2026-09-28

## Context
A dashboard needs to say where visitors came from. The raw referrer URL is not usable for that: `google.de`,
`google.co.uk` and `www.google.com` are one source but three different hosts, link wrappers like `t.co` and
`lnkd.in` hide the real source, and `bing.com` and `bing.com/images/search` are different products.

Grouping them needs a database of known referrers. The established one is `referers.yml` from
[referer-parser](https://github.com/snowplow-referer-parser/referer-parser), which Plausible also uses: 433 sources
across 2753 domains.

## Decision
- `processor` classifies the referrer in the enrichment stage, using the vendored `referers.yml`. The longest
  matching path wins (`bing.com/images/search` before `bing.com`), then the bare host, with and without `www.`.
- **Only the `domains` of each provider are used.** The file also carries `parameters`, which exist to pull the
  search keywords out of a referrer URL. Reading them would mean collecting what people searched for, which
  contradicts the product (see [ADR 0005](0005-privacy-by-design.md)).
- A referrer on the tracked site's own hostname is internal navigation, not a source, and is stored as `NULL`
  (the dashboard shows those visits as direct), as are missing and unparseable referrers.
- An unknown referrer falls back to its host, so traffic from a source the database does not know is still counted
  and still grouped, just under a plainer name.
- `utm_source` stays in its own column and does not override `referrer_source`. The two answer different questions
  and the schema already keeps both.

## Consequences
- **Whole Story is licensed under the GPL v3.** `referers.yml` derives from Matomo's `SearchEngines.php` and
  `Socials.php`, copyright 2012 Matthieu Aubry, and is published under the GPL v3; bundling it into the distributed
  service brings the project under the same licence. `LICENSE` at the repository root and the README next to the
  data file record this. The alternative, a hand-maintained list of the largest sources, was rejected in favour of
  the completeness of the real database.
- The database is a point-in-time copy: updating means replacing the file from upstream. It currently has no entry
  for `x.com`, which therefore appears under its host name; traffic through `t.co`, which is how most links from
  that platform arrive, is still resolved to Twitter.
- No new dependency: the YAML is read with the SnakeYAML that Spring Boot already ships. The 2753 domains are
  loaded into a map once at startup.
- Search keywords are never extracted, so the "top search terms" report that other analytics products offer cannot
  be built from this data. That is the intended trade-off.
