import Link from "next/link";
import type React from "react";
import { BreakdownCard } from "@/components/analytics/breakdown-card";
import { CustomRangePicker } from "@/components/analytics/custom-range";
import { type Chip, FilterChips } from "@/components/analytics/filter-chips";
import { GoalsCard } from "@/components/analytics/goals-card";
import { RealtimeVisitors } from "@/components/analytics/realtime-visitors";
import { StatTiles } from "@/components/analytics/stat-tiles";
import { TimeseriesTable } from "@/components/analytics/timeseries-table";
import { VisitorsChart } from "@/components/analytics/visitors-chart";
import { WorldMap } from "@/components/analytics/world-map";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { Interval } from "@/lib/buckets";
import { type ActiveFilter, absentLabel, serialiseFilters, withFilter, withoutFilter } from "@/lib/filters";
import type { GoalConversion } from "@/lib/goal-types";
import { METRIC_LABELS, METRICS, type Metric } from "@/lib/metrics";
import { type CustomRange, isCustom, PERIOD_LABELS, PERIODS, type Selection, selectionParams } from "@/lib/period";
import type { BreakdownEntry, Dimension, Summary, TimeseriesPoint } from "@/lib/stats";
import { cn } from "@/lib/utils";

export type DashboardData = {
  summary: Summary;
  points: TimeseriesPoint[];
  goals: GoalConversion[];
  pages: BreakdownEntry[];
  entryPages: BreakdownEntry[];
  exitPages: BreakdownEntry[];
  sources: BreakdownEntry[];
  countries: BreakdownEntry[];
  regions: BreakdownEntry[];
  cities: BreakdownEntry[];
  browsers: BreakdownEntry[];
  systems: BreakdownEntry[];
  devices: BreakdownEntry[];
};

type Props = {
  /** The page's own heading: the owner's says which site and links away, a shared one says less. */
  heading: React.ReactNode;
  /** Where this dashboard lives, so that a period, a metric or a filter stays on the same page. */
  basePath: string;
  timezone: string;
  period: Selection;
  /** The window `period` resolves to, so the range picker opens on what is actually shown. */
  range: CustomRange;
  metric: Metric;
  filters: ActiveFilter[];
  interval: Interval;
  data: DashboardData;
  /** Where the browser asks for the current visitor count; the two dashboards have different route handlers. */
  realtimeHref: string;
  /** Server-rendered so the number is right on first paint. */
  realtimeVisitors: number;
  /** Omitted on a shared dashboard, which is read-only: nobody holding the link may edit the goals. */
  manageGoalsHref?: string;
};

/**
 * Everything below the heading, rendered the same whether the reader is the site's owner or somebody who was
 * given the link. The two pages differ only in where the numbers came from and in what the heading says.
 */
export function Dashboard({
  heading,
  basePath,
  timezone,
  period,
  range,
  metric,
  filters,
  interval,
  data,
  realtimeHref,
  realtimeVisitors,
  manageGoalsHref,
}: Props) {
  const link = (next: ActiveFilter[]) => href(basePath, period, metric, next);
  const narrow = (dimension: Dimension) => (key: string) => link(withFilter(filters, dimension, key));
  const entriesOf: Record<string, BreakdownEntry[]> = {
    PAGE: data.pages,
    SOURCE: data.sources,
    COUNTRY: data.countries,
    REGION: data.regions,
    CITY: data.cities,
    BROWSER: data.browsers,
    OS: data.systems,
    DEVICE: data.devices,
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
    <>
      <header className="flex flex-wrap items-center justify-between gap-4">
        {heading}
        <div className="flex flex-wrap items-center gap-4">
          <RealtimeVisitors href={realtimeHref} initial={realtimeVisitors} />
          <nav className="flex gap-1 rounded-lg bg-muted p-1" aria-label="Period">
            {PERIODS.map((option) => (
              <Link
                key={option}
                href={href(basePath, option, metric, filters)}
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
          <CustomRangePicker
            basePath={basePath}
            range={range}
            metric={metric}
            filters={filters}
            active={isCustom(period)}
          />
        </div>
      </header>

      <FilterChips chips={chips} clearHref={href(basePath, period, metric, [])} />

      <StatTiles summary={data.summary} />

      <Card>
        <CardHeader className="flex flex-wrap items-center justify-between gap-3">
          <CardTitle className="font-normal text-muted-foreground text-sm">Over time</CardTitle>
          <nav className="flex gap-1 rounded-lg bg-muted p-1" aria-label="Metric">
            {METRICS.map((option) => (
              <Link
                key={option}
                href={href(basePath, period, option, filters)}
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
          <VisitorsChart points={data.points} metric={metric} timezone={timezone} interval={interval} />
          <TimeseriesTable
            points={data.points}
            metric={metric}
            label={METRIC_LABELS[metric]}
            timezone={timezone}
            interval={interval}
          />
        </CardContent>
      </Card>

      <GoalsCard conversions={data.goals} manageHref={manageGoalsHref} />

      <Card>
        <CardHeader>
          <CardTitle className="font-normal text-muted-foreground text-sm">Where visitors are</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <WorldMap entries={data.countries} filterHrefBase={link(withoutFilter(filters, "COUNTRY"))} />
          {/*
            Why some visitors have a country and nothing finer, said once rather than left for the reader to
            wonder about (D-130). The attribution is also MaxMind's licence condition, and this is where it is
            actually relevant.
          */}
          <p className="text-muted-foreground text-xs">
            Some visitors use a VPN, Apple Private Relay or a network we can only place to a country. Their region and
            city are listed as not available rather than guessed. IP geolocation by{" "}
            <a href="https://www.maxmind.com" target="_blank" rel="noreferrer" className="underline underline-offset-4">
              MaxMind
            </a>
            .
          </p>
        </CardContent>
      </Card>

      <section className="grid gap-4 md:grid-cols-2">
        <BreakdownCard
          title="Top pages"
          entries={data.pages}
          absent={absentLabel("PAGE")}
          filterHref={narrow("PAGE")}
        />
        {/*
          A page and an entry page answer different questions and come from different tables: "Top pages"
          counts pageviews, these two count visits by where they began and ended. Filtering by one of them
          narrows the whole dashboard to those visits, which is the one case that needs a semi-join (D-104).
        */}
        <BreakdownCard
          title="Entry pages"
          entries={data.entryPages}
          absent={absentLabel("ENTRY_PAGE")}
          filterHref={narrow("ENTRY_PAGE")}
        />
        <BreakdownCard
          title="Exit pages"
          entries={data.exitPages}
          absent={absentLabel("EXIT_PAGE")}
          filterHref={narrow("EXIT_PAGE")}
        />
        <BreakdownCard
          title="Sources"
          entries={data.sources}
          absent={absentLabel("SOURCE")}
          filterHref={narrow("SOURCE")}
        />
        <BreakdownCard
          title="Countries"
          entries={data.countries}
          absent={absentLabel("COUNTRY")}
          asCountry
          filterHref={narrow("COUNTRY")}
        />
        <BreakdownCard
          title="Regions"
          entries={data.regions}
          absent={absentLabel("REGION")}
          filterHref={narrow("REGION")}
        />
        <BreakdownCard
          title="Cities"
          entries={data.cities}
          absent={absentLabel("CITY")}
          keyIsAnId
          filterHref={narrow("CITY")}
        />
        <BreakdownCard
          title="Browsers"
          entries={data.browsers}
          absent={absentLabel("BROWSER")}
          filterHref={narrow("BROWSER")}
        />
        <BreakdownCard
          title="Operating systems"
          entries={data.systems}
          absent={absentLabel("OS")}
          filterHref={narrow("OS")}
        />
        <BreakdownCard
          title="Devices"
          entries={data.devices}
          absent={absentLabel("DEVICE")}
          capitalise
          filterHref={narrow("DEVICE")}
        />
      </section>
    </>
  );
}

/** Dashboard state lives in the URL, so every view can be shared and survives a reload (D-047). */
function href(basePath: string, period: Selection, metric: Metric, filters: ActiveFilter[]): string {
  const query = new URLSearchParams({ ...selectionParams(period), metric });
  for (const filter of serialiseFilters(filters)) {
    query.append("filter", filter);
  }
  return `${basePath}?${query}`;
}
