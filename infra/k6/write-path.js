// Load test of the write path: tracker -> ingest -> Kafka -> processor -> PostgreSQL.
//
// The number worth having is not "requests per second" on its own. This drives the path at a fixed rate so
// that latency can be read at a known load, and it is written so that the two ways the measurement could
// silently become meaningless are impossible:
//
//   1. Admission control is per visitor, 60 events a minute (ADR 0016), and a visitor is
//      hash(dailySalt, siteId, ip, userAgent). Posting everything from one address makes every event the same
//      visitor, and after the first minute the run measures the rate limiter instead of the service. The
//      pool below gives each synthetic visitor a stable address and User-Agent, and the check in setup()
//      refuses to start when the arithmetic would cross that limit.
//   2. The bot filter runs in the processor, after ingest has already answered 202 (D-064). A User-Agent that
//      does not look human is accepted and then dropped, so rows would never appear and "accepted == stored"
//      would fail for a reason that has nothing to do with capacity. Every agent here is a real browser
//      string, and none of them contains "Headless".
//
// Usage (from infra/k6, against the stack described in docs/performance.md):
//   k6 run -e BASE_URL=http://ingest:8081 -e DOMAIN=loadtest.example -e RATE=1000 write-path.js
//
// Environment:
//   BASE_URL   where ingest answers
//   DOMAIN     a domain registered through Site Management, or ingest answers 400 unknown_domain
//   RATE       events per second during the measured phase
//   DURATION   length of the measured phase (default 2m)
//   VISITORS   size of the synthetic visitor pool (default: ten times the rate, see below)

import http from "k6/http";
import { check, fail } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8081";
const DOMAIN = __ENV.DOMAIN || "loadtest.example";
const RATE = Number(__ENV.RATE || 500);
const DURATION = __ENV.DURATION || "2m";
/**
 * Scaled with the rate by default, so that every run in a series has visitors behaving identically — six
 * events a minute each — and the only thing that changes between runs is how many of them there are. A fixed
 * pool would make a faster run also a run of busier visitors, which changes the session shape and with it the
 * number of session upserts, and the series would be comparing two things at once. It is also what real
 * growth looks like: more people, not more impatient ones.
 */
const VISITORS = Number(__ENV.VISITORS || Number(__ENV.RATE || 500) * 10);

/** Admission limit per visitor per minute (ADR 0016). Mirrored here only to refuse an invalid run. */
const MAX_EVENTS_PER_VISITOR_PER_MINUTE = 60;
/** Leaves room for the uneven arrival the generator produces; a run near the cap is not worth defending. */
const SAFE_UTILISATION = 0.5;

const ENDPOINT = `${BASE_URL}/api/event`;

export const options = {
  scenarios: {
    // Lets the JVMs reach steady state before anything is measured: class loading and JIT compilation make
    // the first seconds of any Java service slower than the service is.
    warmup: {
      executor: "constant-arrival-rate",
      rate: Math.max(10, Math.round(RATE / 10)),
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: 50,
      maxVUs: 200,
      tags: { phase: "warmup" },
      exec: "sendEvent",
    },
    // An open model on purpose: with a fixed number of virtual users a slowing server quietly reduces the
    // offered load, and the point where it stops keeping up never shows. Here the arrival rate is held and
    // the queue is allowed to grow, which is what a real stream of visitors does.
    measure: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      startTime: "30s",
      // Sized from Little's law rather than from the rate: the number of virtual users a given arrival
      // rate needs is rate x latency, so at 2 ms a thousand events a second need two. The allowance below
      // is for 50 ms, and the ceiling for 200 ms, which is far past where this path has ever been measured.
      // Allocating against the rate instead cost two runs: at 4000/s k6 spent its CPU managing idle VUs and
      // dropped a quarter of the iterations, and at 8000/s it was killed for running out of memory. Either
      // failure makes the latency the generator's rather than the service's.
      // The allowance is what exists when the measured phase begins; k6 drops an iteration whenever no
      // virtual user is free, and the few hundred lost at the start of a run were exactly that — the ramp,
      // not the service. Allocating for 100 ms up front removes them, and the ceiling still covers 300 ms.
      preAllocatedVUs: Math.max(100, Math.ceil(RATE * 0.1)),
      maxVUs: Math.max(400, Math.ceil(RATE * 0.3)),
      tags: { phase: "measure" },
      exec: "sendEvent",
    },
  },
  thresholds: {
    // Anything but 202 breaks the claim rather than lowering it: a 429 means the pool is too small, a 400
    // means the payload or the domain is wrong, and either way the run is measuring the wrong thing.
    "http_req_failed{phase:measure}": [{ threshold: "rate==0", abortOnFail: false }],
    "checks{phase:measure}": [{ threshold: "rate==1", abortOnFail: false }],
    // Not a product promise, a tripwire: a run this far from the ordinary is reported as a failure so the
    // number never ends up in a document without someone having looked at it.
    "http_req_duration{phase:measure}": ["p(99)<1000"],
  },
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
  // The service records its own histogram of the same requests (ADR 0024). Keeping k6's view separate and
  // comparing the two afterwards is what makes either of them believable.
  discardResponseBodies: true,
};

/**
 * Globally routable addresses from several allocations, so the GeoIP lookup in ingest does real work and the
 * run produces a spread of countries rather than one. They are only ever used as input to a hash and a
 * database lookup: nothing is sent to them, and ingest discards the address like any other (ADR 0005).
 */
const IP_PREFIXES = ["5.44", "24.57", "41.90", "77.111", "101.53", "130.185", "177.72", "200.49"];

/** Real browser strings. Yauaa has to classify these as human, or the processor drops the events (D-063). */
const USER_AGENTS = [
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Safari/605.1.15",
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
  "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1",
  "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
  "Mozilla/5.0 (iPad; CPU OS 18_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1",
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0",
];

/** Weighted by repetition rather than by a weight table: a landing page really is most of the traffic. */
const PATHS = [
  "/",
  "/",
  "/",
  "/pricing",
  "/pricing",
  "/docs",
  "/docs/install",
  "/docs/install/next",
  "/blog",
  "/blog/privacy-first-analytics",
  "/blog/why-no-cookies",
  "/about",
];

/**
 * A mix the referrer classifier has to work through: names it knows by host, one it knows by path
 * (bing.com/images/search), a shortener, an unknown host it keeps as-is, navigation inside the tracked site
 * that must come out as Direct, and no referrer at all (ADR 0015).
 */
const REFERRERS = [
  null,
  null,
  null,
  "https://www.google.com/",
  "https://www.google.de/",
  "https://duckduckgo.com/",
  "https://t.co/abc123",
  "https://news.ycombinator.com/",
  "https://www.bing.com/images/search?q=analytics",
  "https://some-unknown-blog.example/post/1",
  `https://${DOMAIN}/pricing`,
];

/** A fifth of arrivals carry campaign parameters, which is roughly what a site running ads looks like. */
const CAMPAIGNS = [
  null,
  null,
  null,
  null,
  "utm_source=newsletter&utm_medium=email&utm_campaign=launch",
];

/** Custom events are the exception, not the rule: most of what a tracker sends is a pageview. */
const CUSTOM_EVENT_IN = 20;

export function setup() {
  const perVisitorPerMinute = (RATE * 60) / VISITORS;
  const ceiling = MAX_EVENTS_PER_VISITOR_PER_MINUTE * SAFE_UTILISATION;
  if (perVisitorPerMinute >= ceiling) {
    fail(
      `${RATE} events/s across ${VISITORS} visitors is ${perVisitorPerMinute.toFixed(1)} events per visitor ` +
        `per minute, and ingest admits ${MAX_EVENTS_PER_VISITOR_PER_MINUTE} (ADR 0016). This run would ` +
        `measure the rate limiter. Raise VISITORS to at least ${Math.ceil((RATE * 60) / ceiling)}.`,
    );
  }
  // Fails the run early and loudly rather than producing a summary of 400s nobody reads.
  const probe = http.post(ENDPOINT, body(visitorOf(0), "pageview", "/"), { headers: headersFor(visitorOf(0)) });
  if (probe.status !== 202) {
    fail(
      `ingest answered ${probe.status} to the first event for ${DOMAIN}. A 400 means the domain is not ` +
        `registered: add the site through Site Management and wait for ingest to read site-events (ADR 0019).`,
    );
  }
  return { startedAt: new Date().toISOString(), perVisitorPerMinute };
}

export function sendEvent() {
  // Spread across the pool while letting each visitor recur, so sessions are many events long rather than
  // one event each: a session that never continues exercises neither sessionization nor the upsert path.
  const visitor = visitorOf((__VU * 7919 + __ITER) % VISITORS);
  const custom = __ITER % CUSTOM_EVENT_IN === 0;
  const path = pick(PATHS, __ITER + __VU);

  const response = http.post(
    ENDPOINT,
    custom ? body(visitor, "Signup", path, { plan: "pro" }) : body(visitor, "pageview", path),
    { headers: headersFor(visitor), tags: { name: "POST /api/event" } },
  );
  check(response, { "accepted (202)": (r) => r.status === 202 });
}

export function handleSummary(data) {
  // Written next to the script so a run can be quoted later without anyone having to remember the console.
  return {
    stdout: textSummary(data),
    [`results/write-path-${RATE}rps.json`]: JSON.stringify(data, null, 2),
  };
}

function visitorOf(index) {
  const n = index % VISITORS;
  const prefix = IP_PREFIXES[n % IP_PREFIXES.length];
  // Two octets from the index, avoiding .0 and .255, which some networks treat specially.
  const third = 1 + (Math.floor(n / IP_PREFIXES.length) % 254);
  const fourth = 1 + (Math.floor(n / (IP_PREFIXES.length * 254)) % 254);
  return {
    ip: `${prefix}.${third}.${fourth}`,
    userAgent: USER_AGENTS[n % USER_AGENTS.length],
  };
}

function headersFor(visitor) {
  return {
    // The tracker posts JSON as text/plain so the browser skips the CORS preflight; ingest parses the body
    // itself rather than relying on the content type, and this keeps the request shaped like a real one.
    "Content-Type": "text/plain",
    "User-Agent": visitor.userAgent,
    // A browser sends this on the cross-origin beacon, and ingest requires it to match the tracked domain
    // when it is present (D-073). Sending it exercises that check instead of skipping past it.
    Origin: `https://${DOMAIN}`,
    // What Traefik would have set in production. Ingest reads the client address from here
    // (forward-headers-strategy: framework), which is what lets one generator stand in for many visitors.
    "X-Forwarded-For": visitor.ip,
  };
}

function body(visitor, name, path, props) {
  const campaign = pick(CAMPAIGNS, path.length + name.length);
  const query = campaign ? `?${campaign}` : "";
  return JSON.stringify({
    name,
    url: `https://${DOMAIN}${path}${query}`,
    domain: DOMAIN,
    referrer: pick(REFERRERS, path.length + visitor.ip.length),
    props,
  });
}

function pick(values, seed) {
  return values[Math.abs(seed) % values.length];
}

/**
 * k6's own textSummary lives in a remote module; this keeps the script dependency-free.
 *
 * It reads the measured phase rather than the totals, because the totals include the warmup and would quote a
 * rate nobody asked for. It also prints every threshold's verdict: defining a threshold and then not showing
 * whether it held would leave a broken run looking like a successful one on screen, and k6's non-zero exit
 * code is easy to lose in a pipeline.
 */
function textSummary(data) {
  const values = (name) => (data.metrics[name] || {}).values || {};
  const requests = values("http_req_duration{phase:measure}");
  const failed = values("http_req_failed{phase:measure}");
  const checks = values("checks{phase:measure}");
  const dropped = values("dropped_iterations");

  const verdicts = [];
  for (const [name, metric] of Object.entries(data.metrics)) {
    for (const [threshold, result] of Object.entries(metric.thresholds || {})) {
      verdicts.push(`    ${result.ok ? "pass" : "FAIL"}  ${name} ${threshold}`);
    }
  }

  const sent = (failed.passes ?? 0) + (failed.fails ?? 0);
  return [
    "",
    `  measured phase     ${RATE} events/s requested for ${DURATION} across ${VISITORS} visitors`,
    `  events sent        ${sent}`,
    `  non-202 responses  ${failed.passes ?? 0} (${((failed.rate ?? 0) * 100).toFixed(3)}%)`,
    // Anything but zero means the generator could not offer the rate, so the latency below is not the
    // latency at that rate and the run has to be repeated with more CPU for k6.
    `  dropped iterations ${dropped.count ?? 0}`,
    `  checks passed      ${checks.passes ?? 0} of ${(checks.passes ?? 0) + (checks.fails ?? 0)}`,
    `  POST /api/event    avg ${fmt(requests.avg)}  med ${fmt(requests.med)}  p95 ${fmt(requests["p(95)"])}  p99 ${fmt(requests["p(99)"])}  max ${fmt(requests.max)}`,
    "",
    "  thresholds",
    ...verdicts,
    "",
  ].join("\n");
}

function fmt(milliseconds) {
  return milliseconds === undefined ? "-" : `${milliseconds.toFixed(1)}ms`;
}
