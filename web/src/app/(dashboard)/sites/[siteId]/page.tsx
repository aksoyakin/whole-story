import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { BreakdownCard } from "@/components/analytics/breakdown-card";
import { StatTiles } from "@/components/analytics/stat-tiles";
import { TimeseriesTable } from "@/components/analytics/timeseries-table";
import { type Metric, VisitorsChart } from "@/components/analytics/visitors-chart";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { requireUser } from "@/lib/auth";
import { PERIOD_LABELS, PERIODS, type Period, parsePeriod, toDateRange } from "@/lib/period";
import { sitesOf } from "@/lib/sites";
import { breakdown, summary, timeseries } from "@/lib/stats";
import { cn } from "@/lib/utils";

export const metadata: Metadata = { title: "Dashboard · Whole Story" };

const METRICS: Metric[] = ["visitors", "pageviews"];
const METRIC_LABELS: Record<Metric, string> = { visitors: "Unique visitors", pageviews: "Pageviews" };

type Props = {
  params: Promise<{ siteId: string }>;
  searchParams: Promise<{ period?: string | string[]; metric?: string | string[] }>;
};

/** Dashboard state lives in the URL, so every view can be shared and survives a reload (D-047). */
export default async function SiteDashboard({ params, searchParams }: Props) {
  const user = await requireUser();
  const { siteId } = await params;
  const query = await searchParams;

  // The site tells us its timezone and its name; a site this organization cannot see is simply not there.
  const sites = await sitesOf(user.organizationId);
  const site = sites.find((candidate) => candidate.siteId === siteId);
  if (!site) notFound();

  const period = parsePeriod(query.period);
  const metric = parseMetric(query.metric);
  const range = toDateRange(period, site.timezone);
  const interval = period === "today" ? "hour" : "day";

  const [stats, points, pages, sources, countries, regions, cities, browsers, systems, devices] = await Promise.all([
    summary(siteId, range),
    timeseries(siteId, range),
    breakdown(siteId, range, "PAGE"),
    breakdown(siteId, range, "SOURCE"),
    breakdown(siteId, range, "COUNTRY"),
    breakdown(siteId, range, "REGION"),
    breakdown(siteId, range, "CITY"),
    breakdown(siteId, range, "BROWSER"),
    breakdown(siteId, range, "OS"),
    breakdown(siteId, range, "DEVICE"),
  ]);

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 px-6 py-10">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="font-semibold text-2xl tracking-tight">{site.domain}</h1>
          <p className="text-muted-foreground text-xs">
            Days start and end in {site.timezone}.{" "}
            <Link href="/sites" className="underline underline-offset-4">
              All sites
            </Link>
          </p>
        </div>
        <nav className="flex gap-1 rounded-lg bg-muted p-1" aria-label="Period">
          {PERIODS.map((option) => (
            <Link
              key={option}
              href={href(siteId, option, metric)}
              aria-current={option === period ? "page" : undefined}
              className={cn(
                "rounded-md px-3 py-1.5 text-sm transition-colors",
                option === period
                  ? "bg-background font-medium shadow-sm"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {PERIOD_LABELS[option]}
            </Link>
          ))}
        </nav>
      </header>

      <StatTiles summary={stats} />

      <Card>
        <CardHeader className="flex flex-wrap items-center justify-between gap-3">
          <CardTitle className="font-normal text-muted-foreground text-sm">Over time</CardTitle>
          <nav className="flex gap-1 rounded-lg bg-muted p-1" aria-label="Metric">
            {METRICS.map((option) => (
              <Link
                key={option}
                href={href(siteId, period, option)}
                aria-current={option === metric ? "page" : undefined}
                className={cn(
                  "rounded-md px-3 py-1 text-sm transition-colors",
                  option === metric
                    ? "bg-background font-medium shadow-sm"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                {METRIC_LABELS[option]}
              </Link>
            ))}
          </nav>
        </CardHeader>
        <CardContent>
          <VisitorsChart points={points} metric={metric} timezone={site.timezone} interval={interval} />
          <TimeseriesTable
            points={points}
            metric={metric}
            label={METRIC_LABELS[metric]}
            timezone={site.timezone}
            interval={interval}
          />
        </CardContent>
      </Card>

      <section className="grid gap-4 md:grid-cols-2">
        <BreakdownCard title="Top pages" entries={pages} />
        <BreakdownCard title="Sources" entries={sources} />
        <BreakdownCard title="Countries" entries={countries} asCountry />
        <BreakdownCard title="Regions" entries={regions} />
        <BreakdownCard title="Cities" entries={cities} keyIsAnId />
        <BreakdownCard title="Browsers" entries={browsers} />
        <BreakdownCard title="Operating systems" entries={systems} />
        <BreakdownCard title="Devices" entries={devices} capitalise />
      </section>
    </main>
  );
}

function parseMetric(value: string | string[] | undefined): Metric {
  return METRICS.find((metric) => metric === value) ?? "visitors";
}

function href(siteId: string, period: Period, metric: Metric): string {
  return `/sites/${siteId}?period=${period}&metric=${metric}`;
}
