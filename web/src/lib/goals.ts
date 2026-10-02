import "server-only";
import { api } from "@/lib/api/client";
import { sessionHeaders } from "@/lib/auth";
import { type ActiveFilter, serialiseFilters } from "@/lib/filters";
import type { Goal, GoalConversion } from "@/lib/goal-types";

type Range = { from: string; to: string };

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
