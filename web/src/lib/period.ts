export const PERIODS = ["today", "7d", "30d"] as const;
export type Period = (typeof PERIODS)[number];

export const PERIOD_LABELS: Record<Period, string> = {
  today: "Today",
  "7d": "Last 7 days",
  "30d": "Last 30 days",
};

const DEFAULT_PERIOD: Period = "30d";

export function parsePeriod(value: string | string[] | undefined): Period {
  return PERIODS.find((period) => period === value) ?? DEFAULT_PERIOD;
}

/** Inclusive ISO date range ending today. M1 uses UTC; the site timezone arrives with Site Management. */
export function toDateRange(period: Period, now: Date = new Date()): { from: string; to: string } {
  const days = period === "today" ? 0 : Number.parseInt(period, 10) - 1;
  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - days));
  return { from: isoDate(from), to: isoDate(now) };
}

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}
