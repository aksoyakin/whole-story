/**
 * Which measure the time series draws. Kept beside the period rather than inside the chart, because it is URL
 * state that server components parse and the chart itself is a client component (D-128).
 */

export const METRICS = ["visitors", "pageviews"] as const;
export type Metric = (typeof METRICS)[number];

export const METRIC_LABELS: Record<Metric, string> = {
  visitors: "Unique visitors",
  pageviews: "Pageviews",
};

export function parseMetric(value: string | string[] | undefined): Metric {
  return METRICS.find((metric) => metric === value) ?? "visitors";
}
