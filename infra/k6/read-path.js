// Times the dashboard's endpoints end to end, against the corpus seed-analytics.sql builds
// (docs/performance.md). Run through docker-compose.readpath.yml.
//
// This is the second half of the read-side measurement. The first, read-path-queries.sql, times the SQL and
// is where the architectural questions get answered; this one answers the one the SQL cannot: what a person
// opening the dashboard waits for. The two differ by JDBC, row mapping, JSON and HTTP, and reporting both is
// what shows whether any of that matters next to the query.
//
// Shape of the run, and why:
//
//   one VU, one request at a time   This measures latency, not throughput. A dashboard is opened by a person,
//                                   not hammered, and the number worth publishing is what one request costs
//                                   with nothing else competing. Running the periods as parallel scenarios
//                                   would have them contend for the same database and each would report the
//                                   others' queueing as its own latency — the mistake the write-path run made
//                                   with preAllocatedVUs before it was resized (D-148).
//   the whole matrix per iteration  Period x endpoint x filtered, in a fixed order, repeated. Every
//                                   combination therefore gets the same number of samples and the same
//                                   cache conditions, which is what makes them comparable to each other.
//   a dashboard total               The page makes fourteen requests: a summary, one time series, the goals,
//                                   the realtime count and ten breakdowns. Timing them individually says
//                                   which is slow; timing the set says whether the page is. Both measures of
//                                   the series are requested here, so the total covers one of them and the
//                                   other is reported on its own -- a reader never sees both (D-100), and a
//                                   total that added them would describe a page nobody opens.
//
// The public endpoints are used rather than the owner's. The SQL is identical — the rules are shared in
// StatsRequests and only the source of authority differs (D-137) — and this needs no session cookie, which
// keeps a login out of a measurement that is not about logging in.

import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { fail } from 'k6';

const BASE = __ENV.BASE_URL || 'http://api:8080';
const DOMAIN = __ENV.DOMAIN || 'readpath.example';
const ITERATIONS = Number(__ENV.ITERATIONS || 10);

const ROOT = `${BASE}/api/public/sites/${DOMAIN}/stats`;

// The ten cards the dashboard draws, in the order it draws them. Entry and exit page joined the set
// with D-166; a render total that still summed eight would quietly flatter the page.
const DIMENSIONS = ['PAGE', 'ENTRY_PAGE', 'EXIT_PAGE', 'SOURCE', 'COUNTRY', 'REGION', 'CITY', 'BROWSER', 'OS', 'DEVICE'];

// A filter the corpus actually contains, so that the filtered path measures work rather than an empty result.
const FILTER = 'COUNTRY:TR';

// Overridable so a before-and-after can hold one period still. Walking all four in one loop leaves a
// twelve-month sweep settling while the thirty-day requests arrive, and that contention is larger than most
// of the differences a comparison is looking for.
const PERIODS = (__ENV.PERIODS || 'today,7d,30d,12m').split(',').map((p) => p.trim());

// Declared up front, every one of them, because k6 only allows a metric to be created in the init context --
// building them lazily on first use fails the iteration with "metrics must be declared in the init context".
// Found by running the script once with a single iteration before trusting it, which is the same rule the
// deployment notes apply to configuration: something that has only ever existed in the repository has never
// run (D-147).
// The time series is timed once per measure. The chart draws one at a time (D-100) and since the endpoint
// stopped computing both, the two cost very different amounts: visitors is a distinct count per bucket over
// the sessions, pageviews a sum over the hourly rollup. Timing only one of them would describe half the page.
const MEASURES = ['VISITORS', 'PAGEVIEWS'];

const ENDPOINTS = ['summary', 'goals', 'realtime']
  .concat(MEASURES.map((m) => `timeseries_${m.toLowerCase()}`))
  .concat(DIMENSIONS.map((d) => `breakdown_${d.toLowerCase()}`));

const trends = {};
for (const period of PERIODS) {
  for (const filtered of ['plain', 'filtered']) {
    for (const endpoint of ENDPOINTS) {
      const key = `${endpoint}_${period}_${filtered}`;
      trends[key] = new Trend(`t_${key}`, true);
    }
    const key = `dashboard_${period}_${filtered}`;
    trends[key] = new Trend(`t_${key}`, true);
  }
}

function record(key, value) {
  if (!trends[key]) throw new Error(`no metric declared for ${key}`);
  trends[key].add(value);
}

export const options = {
  scenarios: {
    read: { executor: 'per-vu-iterations', vus: 1, iterations: ITERATIONS, maxDuration: '2h' },
  },
  // Deliberately none. A threshold here would be a claim about a number nobody has measured yet; the point of
  // this run is to produce the number, and the write-up decides afterwards what is acceptable.
  thresholds: {},
};

function rangeOf(period) {
  const to = new Date();
  const from = new Date(to);
  if (period === 'today') {
    // from === to: the api reads a single day as hourly buckets (ADR 0020).
  } else if (period === '7d') {
    from.setUTCDate(from.getUTCDate() - 6);
  } else if (period === '30d') {
    from.setUTCDate(from.getUTCDate() - 29);
  } else if (period === '12m') {
    from.setUTCMonth(from.getUTCMonth() - 12);
  }
  const iso = (d) => d.toISOString().slice(0, 10);
  return `from=${iso(from)}&to=${iso(to)}`;
}

function get(path, label, period, filtered) {
  const response = http.get(path, {
    tags: { endpoint: label, period: period, filtered: String(filtered) },
  });
  if (response.status !== 200) {
    fail(`${label} ${period} answered ${response.status}: ${String(response.body).slice(0, 200)}`);
  }
  record(`${label}_${period}_${filtered ? 'filtered' : 'plain'}`, response.timings.duration);
  return response;
}

// Two guards, both of which exist because without them a run can look fine while measuring nothing. The
// write-path script carries the same idea (see conventions/testing.md).
export function setup() {
  // 1. The dashboard has to be readable at all: a site that is not shared answers exactly like one that does
  //    not exist (D-137), so a 404 here would otherwise be measured as a very fast endpoint.
  const probe = http.get(`${ROOT}/summary?${rangeOf('30d')}`);
  if (probe.status !== 200) {
    fail(`${DOMAIN} is not readable (${probe.status}). Seed the corpus first: infra/k6/seed-analytics.sh`);
  }

  // 2. The corpus has to cover the periods. Querying a window the seed does not reach would measure an empty
  //    range and report it as excellent, which is the read-side version of measuring an empty table.
  const summary = probe.json();
  if (!summary.visitors || summary.visitors <= 0) {
    fail(`the last 30 days hold no visitors; the corpus does not cover the measured window`);
  }
  const long = http.get(`${ROOT}/summary?${rangeOf('12m')}`).json();
  if (!long.visitors || long.visitors <= summary.visitors) {
    fail(`12 months hold no more visitors than 30 days (${long.visitors} vs ${summary.visitors}); ` +
         `the corpus is shorter than the longest measured period`);
  }
  return { visitors30d: summary.visitors, visitors12m: long.visitors };
}

export default function () {
  for (const period of PERIODS) {
    const range = rangeOf(period);

    for (const filtered of [false, true]) {
      const suffix = filtered ? `&filter=${FILTER}` : '';

      const started = Date.now();
      get(`${ROOT}/summary?${range}${suffix}`, 'summary', period, filtered);
      // The default measure, and the only one a render makes: the chart draws one at a time (D-100).
      get(`${ROOT}/timeseries?${range}&measure=${MEASURES[0]}${suffix}`,
          `timeseries_${MEASURES[0].toLowerCase()}`, period, filtered);
      get(`${ROOT}/goals?${range}${suffix}`, 'goals', period, filtered);
      get(`${ROOT}/realtime`, 'realtime', period, filtered);
      for (const dimension of DIMENSIONS) {
        get(`${ROOT}/breakdown?dimension=${dimension}&${range}${suffix}`,
            `breakdown_${dimension.toLowerCase()}`, period, filtered);
      }
      // What the page costs, which is the only number a visitor actually experiences. Closed before the
      // other measure is asked for: a reader who switches the chart pays one of the two, never both, so a
      // total that included each would describe a render nobody performs.
      record(`dashboard_${period}_${filtered ? 'filtered' : 'plain'}`, Date.now() - started);

      // The same series, the other measure, timed outside the total. This is the one the change is about.
      for (const measure of MEASURES.slice(1)) {
        get(`${ROOT}/timeseries?${range}&measure=${measure}${suffix}`,
            `timeseries_${measure.toLowerCase()}`, period, filtered);
      }
    }
  }
}

export function handleSummary(data) {
  const rows = [];
  for (const [name, metric] of Object.entries(data.metrics)) {
    if (!name.startsWith('t_')) continue;
    const v = metric.values;
    // No sample count: a Trend publishes avg/min/med/max and the percentiles, not how many it saw, and
    // printing v.count gave a column of "undefined".
    rows.push({ key: name.slice(2), min: v.min, med: v.med, p95: v['p(95)'], max: v.max });
  }
  rows.sort((a, b) => a.key.localeCompare(b.key));

  const pad = (s, n) => String(s).padEnd(n);
  const num = (x) => (x === undefined ? '-' : x.toFixed(1).padStart(9));
  let out = '\nread path, milliseconds (one request at a time)\n\n';
  out += `${pad('case', 42)}${'min'.padStart(10)}${'med'.padStart(10)}${'p95'.padStart(10)}${'max'.padStart(10)}\n`;
  out += '-'.repeat(82) + '\n';
  for (const r of rows) {
    out += `${pad(r.key, 42)}${num(r.min)}${num(r.med)}${num(r.p95)}${num(r.max)}\n`;
  }
  out += '\nDashboard rows are the sum of the fourteen requests the page makes.\n';

  return {
    stdout: out,
    'results/read-path.json': JSON.stringify(data, null, 2),
  };
}
