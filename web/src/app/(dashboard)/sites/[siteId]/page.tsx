import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { BreakdownCard } from "@/components/analytics/breakdown-card";
import { type Chip, FilterChips } from "@/components/analytics/filter-chips";
import { GoalsCard } from "@/components/analytics/goals-card";
import { StatTiles } from "@/components/analytics/stat-tiles";
import { TimeseriesTable } from "@/components/analytics/timeseries-table";
import { type Metric, VisitorsChart } from "@/components/analytics/visitors-chart";
import { WorldMap } from "@/components/analytics/world-map";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { requireUser } from "@/lib/auth";
import {
  type ActiveFilter,
  absentLabel,
  parseFilters,
  serialiseFilters,
  withFilter,
  withoutFilter,
} from "@/lib/filters";
import { goalConversions } from "@/lib/goals";
import { PERIOD_LABELS, PERIODS, type Period, parsePeriod, toDateRange } from "@/lib/period";
import { sitesOf } from "@/lib/sites";
import { type BreakdownEntry, breakdown, type Dimension, summary, timeseries } from "@/lib/stats";
import { cn } from "@/lib/utils";

export const metadata: Metadata = { title: "Dashboard · Whole Story" };

const METRICS: Metric[] = ["visitors", "pageviews"];
const METRIC_LABELS: Record<Metric, string> = { visitors: "Unique visitors", pageviews: "Pageviews" };

type Props = {
  params: Promise<{ siteId: string }>;
  searchParams: Promise<{
    period?: string | string[];
    metric?: string | string[];
    filter?: string | string[];
  }>;
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
  const filters = parseFilters(query.filter);
  const range = toDateRange(period, site.timezone);
  const interval = period === "today" ? "hour" : "day";

  const [stats, points, goals, pages, sources, countries, regions, cities, browsers, systems, devices] =
    await Promise.all([
      summary(siteId, range, filters),
      timeseries(siteId, range, filters),
      goalConversions(siteId, range, filters),
      breakdown(siteId, range, "PAGE", filters),
      breakdown(siteId, range, "SOURCE", filters),
      breakdown(siteId, range, "COUNTRY", filters),
      breakdown(siteId, range, "REGION", filters),
      breakdown(siteId, range, "CITY", filters),
      breakdown(siteId, range, "BROWSER", filters),
      breakdown(siteId, range, "OS", filters),
      breakdown(siteId, range, "DEVICE", filters),
    ]);

  const link = (next: ActiveFilter[]) => href(siteId, period, metric, next);
  const narrow = (dimension: Dimension) => (key: string) => link(withFilter(filters, dimension, key));
  const entriesOf: Record<string, BreakdownEntry[]> = {
    PAGE: pages,
    SOURCE: sources,
    COUNTRY: countries,
    REGION: regions,
    CITY: cities,
    BROWSER: browsers,
    OS: systems,
    DEVICE: devices,
  };
  // The label of a filtered value is read back from the breakdown that is still showing it, so a chip can say
  // "Istanbul" where the URL only carries a geoname id.
  const chips: Chip[] = filters.map((filter) => ({
    filter,
    label:
      filter.value === ""
        ? absentLabel(filter.dimension)
        : (entriesOf[filter.dimension]?.find((entry) => entry.key === filter.value)?.label ?? filter.value),
    removeHref: link(withoutFilter(filters, filter.dimension)),
  }));

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 px-6 py-10">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="font-semibold text-2xl tracking-tight">{site.domain}</h1>
          <p className="text-muted-foreground text-xs">
            Days start and end in{" "}
            <Link href={`/sites/${siteId}/settings`} className="underline underline-offset-4">
              {site.timezone}
            </Link>
            .{" "}
            <Link href="/sites" className="underline underline-offset-4">
              All sites
            </Link>
          </p>
        </div>
        <nav className="flex gap-1 rounded-lg bg-muted p-1" aria-label="Period">
          {PERIODS.map((option) => (
            <Link
              key={option}
              href={href(siteId, option, metric, filters)}
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

      <FilterChips chips={chips} clearHref={href(siteId, period, metric, [])} />

      <StatTiles summary={stats} />

      <Card>
        <CardHeader className="flex flex-wrap items-center justify-between gap-3">
          <CardTitle className="font-normal text-muted-foreground text-sm">Over time</CardTitle>
          <nav className="flex gap-1 rounded-lg bg-muted p-1" aria-label="Metric">
            {METRICS.map((option) => (
              <Link
                key={option}
                href={href(siteId, period, option, filters)}
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

      <GoalsCard conversions={goals} manageHref={`/sites/${siteId}/goals`} />

      <Card>
        <CardHeader>
          <CardTitle className="font-normal text-muted-foreground text-sm">Where visitors are</CardTitle>
        </CardHeader>
        <CardContent>
          <WorldMap entries={countries} filterHrefBase={link(withoutFilter(filters, "COUNTRY"))} />
        </CardContent>
      </Card>

      <section className="grid gap-4 md:grid-cols-2">
        <BreakdownCard title="Top pages" entries={pages} absent={absentLabel("PAGE")} filterHref={narrow("PAGE")} />
        <BreakdownCard title="Sources" entries={sources} absent={absentLabel("SOURCE")} filterHref={narrow("SOURCE")} />
        <BreakdownCard
          title="Countries"
          entries={countries}
          absent={absentLabel("COUNTRY")}
          asCountry
          filterHref={narrow("COUNTRY")}
        />
        <BreakdownCard title="Regions" entries={regions} absent={absentLabel("REGION")} filterHref={narrow("REGION")} />
        <BreakdownCard
          title="Cities"
          entries={cities}
          absent={absentLabel("CITY")}
          keyIsAnId
          filterHref={narrow("CITY")}
        />
        <BreakdownCard
          title="Browsers"
          entries={browsers}
          absent={absentLabel("BROWSER")}
          filterHref={narrow("BROWSER")}
        />
        <BreakdownCard
          title="Operating systems"
          entries={systems}
          absent={absentLabel("OS")}
          filterHref={narrow("OS")}
        />
        <BreakdownCard
          title="Devices"
          entries={devices}
          absent={absentLabel("DEVICE")}
          capitalise
          filterHref={narrow("DEVICE")}
        />
      </section>
    </main>
  );
}

function parseMetric(value: string | string[] | undefined): Metric {
  return METRICS.find((metric) => metric === value) ?? "visitors";
}

function href(siteId: string, period: Period, metric: Metric, filters: ActiveFilter[]): string {
  const query = new URLSearchParams({ period, metric });
  for (const filter of serialiseFilters(filters)) {
    query.append("filter", filter);
  }
  return `/sites/${siteId}?${query}`;
}
