import "server-only";
import { api } from "@/lib/api/client";
import { sessionHeaders } from "@/lib/auth";
import { type ActiveFilter, serialiseFilters } from "@/lib/filters";

export type Summary = {
  visitors: number;
  visits: number;
  pageviews: number;
  bounceRate: number;
  averageVisitDuration: number;
};

export type TimeseriesPoint = { bucket: string; visitors: number; pageviews: number };
export type BreakdownEntry = { key: string; label: string; visitors: number; visits: number; pageviews: number };

export type Dimension =
  | "PAGE"
  | "ENTRY_PAGE"
  | "EXIT_PAGE"
  | "SOURCE"
  | "COUNTRY"
  | "REGION"
  | "CITY"
  | "BROWSER"
  | "OS"
  | "DEVICE";

type Range = { from: string; to: string };
type Query = Range & { filter?: string[] };

const EMPTY_SUMMARY: Summary = {
  visitors: 0,
  visits: 0,
  pageviews: 0,
  bounceRate: 0,
  averageVisitDuration: 0,
};

export async function summary(siteId: string, range: Range, filters: ActiveFilter[] = []): Promise<Summary> {
  const { data } = await api.GET("/api/sites/{siteId}/stats/summary", {
    params: { path: { siteId }, query: withFilters(range, filters) },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? EMPTY_SUMMARY;
}

export async function timeseries(
  siteId: string,
  range: Range,
  filters: ActiveFilter[] = [],
): Promise<TimeseriesPoint[]> {
  const { data } = await api.GET("/api/sites/{siteId}/stats/timeseries", {
    params: { path: { siteId }, query: withFilters(range, filters) },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? [];
}

export async function breakdown(
  siteId: string,
  range: Range,
  dimension: Dimension,
  filters: ActiveFilter[] = [],
  limit = 8,
) {
  const { data } = await api.GET("/api/sites/{siteId}/stats/breakdown", {
    params: { path: { siteId }, query: { ...withFilters(range, filters), dimension, limit } },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? [];
}

function withFilters(range: Range, filters: ActiveFilter[]): Query {
  return filters.length === 0 ? range : { ...range, filter: serialiseFilters(filters) };
}

/** How many people are on the site right now. Rendered once on the server; the client keeps it current. */
export async function realtimeVisitors(siteId: string): Promise<number> {
  const { data } = await api.GET("/api/sites/{siteId}/stats/realtime", {
    params: { path: { siteId } },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data?.visitors ?? 0;
}
