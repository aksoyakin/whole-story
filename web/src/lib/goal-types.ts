/**
 * Goal shapes and labels, kept apart from the calls that fetch them.
 * <p>
 * `goals.ts` is `server-only` and reaches for `next/headers` to forward the session cookie, so a client
 * component importing anything from it drags that into the browser bundle and the build refuses. Only the
 * bundler enforces that boundary — TypeScript and the unit tests see nothing wrong — which is why the split is
 * explicit here, the same way `filters.ts` stays client-safe while `stats.ts` does the fetching.
 */

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

export const GOAL_TYPE_LABELS: Record<GoalType, string> = {
  EVENT: "Custom event",
  PAGEVIEW: "Page visit",
};
