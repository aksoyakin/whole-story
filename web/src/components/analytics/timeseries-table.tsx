import { formatBucketLong, type Interval } from "@/lib/buckets";
import type { TimeseriesPoint } from "@/lib/stats";

const numbers = new Intl.NumberFormat("en-US");

/**
 * The time series as a table, rendered on the server and hidden visually.
 * <p>
 * It is here rather than inside the chart because the chart is drawn by the browser: a reader using a screen
 * reader, or anyone without JavaScript, would otherwise be left with an empty box.
 */
export function TimeseriesTable({
  points,
  metric,
  label,
  timezone,
  interval,
}: {
  points: TimeseriesPoint[];
  metric: "visitors" | "pageviews";
  label: string;
  timezone: string;
  interval: Interval;
}) {
  return (
    <table className="sr-only">
      <caption>{label} over time</caption>
      <thead>
        <tr>
          <th scope="col">When</th>
          <th scope="col">{label}</th>
        </tr>
      </thead>
      <tbody>
        {points.map((point) => (
          <tr key={point.bucket}>
            <th scope="row">{formatBucketLong(point.bucket, timezone, interval)}</th>
            <td>{numbers.format(metric === "visitors" ? point.visitors : point.pageviews)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
