import Link from "next/link";
import { notFound } from "next/navigation";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api/client";
import { PERIOD_LABELS, PERIODS, parsePeriod, toDateRange } from "@/lib/period";
import { cn } from "@/lib/utils";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const numberFormat = new Intl.NumberFormat("en-US");

type Props = {
  params: Promise<{ siteId: string }>;
  searchParams: Promise<{ period?: string | string[] }>;
};

/** Dashboard state lives in the URL, so every view can be shared and survives reloads (D-047). */
export default async function SiteDashboard({ params, searchParams }: Props) {
  const { siteId } = await params;
  if (!UUID.test(siteId)) notFound();
  const period = parsePeriod((await searchParams).period);
  const range = toDateRange(period);

  const { data, error } = await api.GET("/api/sites/{siteId}/stats/aggregate", {
    params: { path: { siteId }, query: range },
    cache: "no-store",
  });
  if (error || !data) throw new Error("Could not load statistics");

  const metrics = [
    { label: "Unique visitors", value: data.visitors },
    { label: "Visits", value: data.visits },
    { label: "Pageviews", value: data.pageviews },
  ];

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 px-6 py-10">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-semibold text-2xl tracking-tight">Dashboard</h1>
        <nav className="flex gap-1 rounded-lg bg-muted p-1" aria-label="Period">
          {PERIODS.map((p) => (
            <Link
              key={p}
              href={`?period=${p}`}
              aria-current={p === period ? "page" : undefined}
              className={cn(
                "rounded-md px-3 py-1.5 text-sm transition-colors",
                p === period ? "bg-background font-medium shadow-sm" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {PERIOD_LABELS[p]}
            </Link>
          ))}
        </nav>
      </header>

      <section className="grid gap-4 sm:grid-cols-3">
        {metrics.map((metric) => (
          <Card key={metric.label}>
            <CardHeader>
              <CardTitle className="font-normal text-muted-foreground text-sm">{metric.label}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="font-semibold text-3xl tabular-nums">{numberFormat.format(metric.value)}</p>
            </CardContent>
          </Card>
        ))}
      </section>
    </main>
  );
}
