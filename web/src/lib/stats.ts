import "server-only";
import { api } from "@/lib/api/client";
import { sessionHeaders } from "@/lib/auth";

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

const EMPTY_SUMMARY: Summary = {
  visitors: 0,
  visits: 0,
  pageviews: 0,
  bounceRate: 0,
  averageVisitDuration: 0,
};

export async function summary(siteId: string, range: Range): Promise<Summary> {
  const { data } = await api.GET("/api/sites/{siteId}/stats/summary", {
    params: { path: { siteId }, query: range },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? EMPTY_SUMMARY;
}

export async function timeseries(siteId: string, range: Range): Promise<TimeseriesPoint[]> {
  const { data } = await api.GET("/api/sites/{siteId}/stats/timeseries", {
    params: { path: { siteId }, query: range },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? [];
}

export async function breakdown(siteId: string, range: Range, dimension: Dimension, limit = 8) {
  const { data } = await api.GET("/api/sites/{siteId}/stats/breakdown", {
    params: { path: { siteId }, query: { ...range, dimension, limit } },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? [];
}
