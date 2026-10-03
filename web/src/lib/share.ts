import "server-only";
import { api } from "@/lib/api/client";
import { type ActiveFilter, serialiseFilters } from "@/lib/filters";
import type { GoalConversion } from "@/lib/goal-types";
import type { BreakdownEntry, Dimension, Summary, TimeseriesPoint } from "@/lib/stats";

/**
 * The public dashboard's half of the reporting calls.
 *
 * Deliberately a separate set rather than a flag on the ones in `stats.ts`: these address a site by domain
 * instead of by id, and they send no session — the api decides what to answer from the site's own sharing
 * flag. Writing that as a branch inside every existing function would hide the one difference that matters.
 */

export type SharedSite = { domain: string; timezone: string };

type Range = { from: string; to: string };

const EMPTY_SUMMARY: Summary = { visitors: 0, visits: 0, pageviews: 0, bounceRate: 0, averageVisitDuration: 0 };

/** Null when the domain is not tracked, the site was removed, or its owner has not shared it. */
export async function sharedSite(domain: string): Promise<SharedSite | null> {
  const { data } = await api.GET("/api/public/sites/{domain}", {
    params: { path: { domain } },
    cache: "no-store",
  });
  return data ?? null;
}

export async function sharedSummary(domain: string, range: Range, filters: ActiveFilter[] = []): Promise<Summary> {
  const { data } = await api.GET("/api/public/sites/{domain}/stats/summary", {
    params: { path: { domain }, query: withFilters(range, filters) },
    cache: "no-store",
  });
  return data ?? EMPTY_SUMMARY;
}

export async function sharedTimeseries(
  domain: string,
  range: Range,
  filters: ActiveFilter[] = [],
): Promise<TimeseriesPoint[]> {
  const { data } = await api.GET("/api/public/sites/{domain}/stats/timeseries", {
    params: { path: { domain }, query: withFilters(range, filters) },
    cache: "no-store",
  });
  return data ?? [];
}

export async function sharedBreakdown(
  domain: string,
  range: Range,
  dimension: Dimension,
  filters: ActiveFilter[] = [],
  limit = 8,
): Promise<BreakdownEntry[]> {
  const { data } = await api.GET("/api/public/sites/{domain}/stats/breakdown", {
    params: { path: { domain }, query: { ...withFilters(range, filters), dimension, limit } },
    cache: "no-store",
  });
  return data ?? [];
}

export async function sharedGoalConversions(
  domain: string,
  range: Range,
  filters: ActiveFilter[] = [],
): Promise<GoalConversion[]> {
  const { data } = await api.GET("/api/public/sites/{domain}/stats/goals", {
    params: { path: { domain }, query: withFilters(range, filters) },
    cache: "no-store",
  });
  return data ?? [];
}

function withFilters(range: Range, filters: ActiveFilter[]) {
  return filters.length === 0 ? range : { ...range, filter: serialiseFilters(filters) };
}

export async function sharedRealtimeVisitors(domain: string): Promise<number> {
  const { data } = await api.GET("/api/public/sites/{domain}/stats/realtime", {
    params: { path: { domain } },
    cache: "no-store",
  });
  return data?.visitors ?? 0;
}
