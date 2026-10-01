export type Interval = "hour" | "day";

/** Short axis label. */
export function formatBucket(bucket: string, timeZone: string, interval: Interval): string {
  const options: Intl.DateTimeFormatOptions =
    interval === "hour" ? { hour: "2-digit", minute: "2-digit", hour12: false } : { day: "numeric", month: "short" };
  return new Intl.DateTimeFormat("en-GB", { timeZone, ...options }).format(new Date(bucket));
}

/** The long form, for a tooltip or a table row. */
export function formatBucketLong(bucket: string, timeZone: string, interval: Interval): string {
  const options: Intl.DateTimeFormatOptions =
    interval === "hour"
      ? { weekday: "short", hour: "2-digit", minute: "2-digit", hour12: false }
      : { weekday: "short", day: "numeric", month: "long" };
  return new Intl.DateTimeFormat("en-GB", { timeZone, ...options }).format(new Date(bucket));
}
