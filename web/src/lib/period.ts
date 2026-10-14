export const PERIODS = ["today", "7d", "30d"] as const;
export type NamedPeriod = (typeof PERIODS)[number];

export const PERIOD_LABELS: Record<NamedPeriod, string> = {
  today: "Today",
  "7d": "Last 7 days",
  "30d": "Last 30 days",
};

const DEFAULT_PERIOD: NamedPeriod = "7d";

/** An explicit pair of local dates, inclusive at both ends, in the site's timezone. */
export type CustomRange = { from: string; to: string };

/** What the dashboard is showing: one of the named periods, or a range somebody typed. */
export type Selection = NamedPeriod | CustomRange;

/**
 * The longest range one request may ask for.
 *
 * The api enforces this and is the authority; this copy exists so that the form can say no before a request
 * is made, because the alternative is a 400 that the server components would render as a page of zeroes. The
 * two have to move together, and a test on each side pins the boundary.
 *
 * 366 rather than a rounder number: a year plus the leap day, so "the last year" always fits. It is also the
 * longest range that has been measured end to end (docs/performance.md), and a limit you have measured is one
 * you can defend.
 */
export const MAX_RANGE_DAYS = 366;

export function isCustom(selection: Selection): selection is CustomRange {
  return typeof selection !== "string";
}

/**
 * What the URL asks for. The dashboard keeps its state in the query string so that any view can be shared and
 * survives a reload (D-047), which means this has to read back everything the UI can produce.
 *
 * Anything unrecognised falls back to the default rather than erroring, the same way an unrecognised filter is
 * dropped: a URL is something people edit and paste, and the period buttons show which one is active, so the
 * fallback is visible rather than silent.
 */
export function parseSelection(query: {
  period?: string | string[];
  from?: string | string[];
  to?: string | string[];
}): Selection {
  if (first(query.period) === "custom") {
    const custom = parseCustomRange(first(query.from), first(query.to));
    if (custom) return custom;
  }
  return parsePeriod(query.period);
}

export function parsePeriod(value: string | string[] | undefined): NamedPeriod {
  const period = first(value);
  return PERIODS.find((candidate) => candidate === period) ?? DEFAULT_PERIOD;
}

/** Null for anything this would not have produced itself, including a range longer than the api will serve. */
export function parseCustomRange(from: string | undefined, to: string | undefined): CustomRange | null {
  if (!from || !to || !isCalendarDate(from) || !isCalendarDate(to)) return null;
  if (from > to) return null;
  if (daysBetween(from, to) > MAX_RANGE_DAYS) return null;
  return { from, to };
}

/** Inclusive at both ends, so a single day is one day rather than none. */
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1;
}

/**
 * The inclusive range of local days the selection covers, in the site's own timezone: "today" is today where
 * the site is, not where the person looking at it happens to be (D-020). A custom range is already a pair of
 * local days and is passed through untouched.
 */
export function toDateRange(selection: Selection, timezone: string, now: Date = new Date()): CustomRange {
  if (isCustom(selection)) return selection;
  const today = localDate(now, timezone);
  const days = selection === "today" ? 0 : Number.parseInt(selection, 10) - 1;
  return { from: shiftDays(today, -days), to: today };
}

/** A single day is read hour by hour, anything longer day by day — the same rule the api applies. */
export function intervalFor(range: CustomRange): "hour" | "day" {
  return range.from === range.to ? "hour" : "day";
}

/** The query parameters that reproduce this selection. */
export function selectionParams(selection: Selection): Record<string, string> {
  return isCustom(selection) ? { period: "custom", from: selection.from, to: selection.to } : { period: selection };
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** Shape and reality: "2026-02-30" parses as a date in some engines and is not one. */
function isCalendarDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
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
