import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { Summary } from "@/lib/stats";

const numbers = new Intl.NumberFormat("en-US");
const percent = new Intl.NumberFormat("en-US", { style: "percent", maximumFractionDigits: 0 });

/** Five single numbers. A number that stands alone is a stat tile, not a chart. */
export function StatTiles({ summary }: { summary: Summary }) {
  const tiles = [
    { label: "Unique visitors", value: numbers.format(summary.visitors) },
    { label: "Visits", value: numbers.format(summary.visits) },
    { label: "Pageviews", value: numbers.format(summary.pageviews) },
    { label: "Bounce rate", value: percent.format(summary.bounceRate) },
    { label: "Visit duration", value: duration(summary.averageVisitDuration) },
  ];

  return (
    <section className="grid gap-4 sm:grid-cols-3 lg:grid-cols-5">
      {tiles.map((tile) => (
        <Card key={tile.label}>
          <CardHeader>
            <CardTitle className="font-normal text-muted-foreground text-sm">{tile.label}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="font-semibold text-3xl tabular-nums">{tile.value}</p>
          </CardContent>
        </Card>
      ))}
    </section>
  );
}

function duration(seconds: number): string {
  const whole = Math.round(seconds);
  if (whole < 60) return `${whole}s`;
  const minutes = Math.floor(whole / 60);
  return `${minutes}m ${String(whole % 60).padStart(2, "0")}s`;
}
