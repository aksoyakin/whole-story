"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatBucket, formatBucketLong, type Interval } from "@/lib/buckets";
import type { TimeseriesPoint } from "@/lib/stats";

export type Metric = "visitors" | "pageviews";

type Props = {
  points: TimeseriesPoint[];
  metric: Metric;
  /** Buckets are labelled in the site's timezone, so the chart agrees with the numbers above it. */
  timezone: string;
  interval: Interval;
};

const METRIC_LABELS: Record<Metric, string> = {
  visitors: "Unique visitors",
  pageviews: "Pageviews",
};

const numbers = new Intl.NumberFormat("en-US");

/**
 * One measure at a time, on one axis.
 * <p>
 * The chart needs the viewport's width, so it only exists once the browser runs: the same numbers are rendered
 * server side as a table next to it, which is what anyone without that gets.
 * <p>
 * Visitors and pageviews are different scales, and two y-axes would let the picture say whatever the scaling
 * chose. The toggle above the chart switches which one is drawn. A single series also needs no legend and no
 * second colour, which suits a theme that is deliberately without hue: the bars carry magnitude, the axis and
 * the tooltip carry the numbers.
 */
export function VisitorsChart({ points, metric, timezone, interval }: Props) {
  const label = METRIC_LABELS[metric];
  const data = points.map((point) => ({
    bucket: point.bucket,
    tick: formatBucket(point.bucket, timezone, interval),
    value: metric === "visitors" ? point.visitors : point.pageviews,
  }));

  return (
    <figure className="flex flex-col gap-2">
      <div className="h-64 w-full" role="img" aria-label={`${label} over time`}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} barCategoryGap={2} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke="var(--border)" />
            <XAxis
              dataKey="tick"
              tickLine={false}
              axisLine={false}
              minTickGap={24}
              tick={{ fill: "var(--muted-foreground)", fontSize: 12 }}
            />
            <YAxis
              allowDecimals={false}
              width={40}
              tickCount={4}
              tickLine={false}
              axisLine={false}
              tick={{ fill: "var(--muted-foreground)", fontSize: 12 }}
            />
            <Tooltip
              cursor={{ fill: "var(--muted)" }}
              content={({ active, payload }) => {
                if (!active || !payload?.length) return null;
                const point = payload[0].payload as { bucket: string; value: number };
                return (
                  <div className="rounded-lg border border-border bg-card px-3 py-2 text-sm shadow-sm">
                    <p className="text-muted-foreground text-xs">
                      {formatBucketLong(point.bucket, timezone, interval)}
                    </p>
                    <p className="font-medium tabular-nums">
                      {numbers.format(point.value)} {label.toLowerCase()}
                    </p>
                  </div>
                );
              }}
            />
            {/* Rounded at the data end only, so every bar still starts from the same baseline. Drawn at once
                rather than animated: this chart is redrawn on every filter and period change, and growing the
                bars again each time only delays reading them. */}
            <Bar
              dataKey="value"
              fill="var(--chart-3)"
              radius={[4, 4, 0, 0]}
              maxBarSize={48}
              isAnimationActive={false}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </figure>
  );
}
