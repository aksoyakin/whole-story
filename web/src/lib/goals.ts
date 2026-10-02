import "server-only";
import { api } from "@/lib/api/client";
import { sessionHeaders } from "@/lib/auth";
import { type ActiveFilter, serialiseFilters } from "@/lib/filters";

export type GoalType = "EVENT" | "PAGEVIEW";

/** A goal as it was defined: what to look for, which is also what the dashboard prints as its name. */
export type Goal = {
  goalId: string;
  type: string;
  target: string;
  createdAt: string;
};

/** How a goal did over a period. `rate` is 0..1, the share of the period's visitors who completed it. */
export type GoalConversion = {
  goalId: string;
  type: string;
  target: string;
  visitors: number;
  completions: number;
  rate: number;
};

type Range = { from: string; to: string };

export const GOAL_TYPE_LABELS: Record<GoalType, string> = {
  EVENT: "Custom event",
  PAGEVIEW: "Page visit",
};

export async function goalsOf(siteId: string): Promise<Goal[]> {
  const { data } = await api.GET("/api/sites/{siteId}/goals", {
    params: { path: { siteId } },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? [];
}

export async function goalConversions(
  siteId: string,
  range: Range,
  filters: ActiveFilter[] = [],
): Promise<GoalConversion[]> {
  const query = filters.length === 0 ? range : { ...range, filter: serialiseFilters(filters) };
  const { data } = await api.GET("/api/sites/{siteId}/stats/goals", {
    params: { path: { siteId }, query },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? [];
}
