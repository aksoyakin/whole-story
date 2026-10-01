export const PERIODS = ["today", "7d", "30d"] as const;
export type Period = (typeof PERIODS)[number];

export const PERIOD_LABELS: Record<Period, string> = {
  today: "Today",
  "7d": "Last 7 days",
  "30d": "Last 30 days",
};

const DEFAULT_PERIOD: Period = "7d";

export function parsePeriod(value: string | string[] | undefined): Period {
  return PERIODS.find((period) => period === value) ?? DEFAULT_PERIOD;
}

/**
 * The inclusive range of local days the period covers, in the site's own timezone: "today" is today where the
 * site is, not where the person looking at it happens to be (D-020).
 */
export function toDateRange(period: Period, timezone: string, now: Date = new Date()): { from: string; to: string } {
  const today = localDate(now, timezone);
  const days = period === "today" ? 0 : Number.parseInt(period, 10) - 1;
  return { from: shiftDays(today, -days), to: today };
}

/** The calendar date at `instant` in `timezone`, as YYYY-MM-DD. */
function localDate(instant: Date, timezone: string): string {
  // "en-CA" renders ISO-like dates, which is what the API expects.
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(instant);
}

function shiftDays(date: string, days: number): string {
  const [year, month, day] = date.split("-").map(Number);
  // Shifted as a plain calendar date in UTC, so no zone rule can move it by an hour.
  const shifted = new Date(Date.UTC(year, month - 1, day + days));
  return shifted.toISOString().slice(0, 10);
}
