import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Dashboard } from "@/components/analytics/dashboard";
import { requireUser } from "@/lib/auth";
import { parseFilters } from "@/lib/filters";
import { goalConversions } from "@/lib/goals";
import { parseMetric } from "@/lib/metrics";
import { parsePeriod, toDateRange } from "@/lib/period";
import { sitesOf } from "@/lib/sites";
import { breakdown, realtimeVisitors, summary, timeseries } from "@/lib/stats";

export const metadata: Metadata = { title: "Dashboard · Whole Story" };

type Props = {
  params: Promise<{ siteId: string }>;
  searchParams: Promise<{
    period?: string | string[];
    metric?: string | string[];
    filter?: string | string[];
  }>;
};

/** The owner's view. The shared one renders the same body from the public endpoints (`/share/[domain]`). */
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

  const [stats, points, goals, onSiteNow, pages, sources, countries, regions, cities, browsers, systems, devices] =
    await Promise.all([
      summary(siteId, range, filters),
      timeseries(siteId, range, filters),
      goalConversions(siteId, range, filters),
      realtimeVisitors(siteId),
      breakdown(siteId, range, "PAGE", filters),
      breakdown(siteId, range, "SOURCE", filters),
      breakdown(siteId, range, "COUNTRY", filters),
      breakdown(siteId, range, "REGION", filters),
      breakdown(siteId, range, "CITY", filters),
      breakdown(siteId, range, "BROWSER", filters),
      breakdown(siteId, range, "OS", filters),
      breakdown(siteId, range, "DEVICE", filters),
    ]);

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 px-6 py-10">
      <Dashboard
        heading={
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
        }
        basePath={`/sites/${siteId}`}
        timezone={site.timezone}
        period={period}
        metric={metric}
        filters={filters}
        interval={interval}
        realtimeHref={`/api/sites/${siteId}/realtime`}
        realtimeVisitors={onSiteNow}
        manageGoalsHref={`/sites/${siteId}/goals`}
        data={{
          summary: stats,
          points,
          goals,
          pages,
          sources,
          countries,
          regions,
          cities,
          browsers,
          systems,
          devices,
        }}
      />
    </main>
  );
}
