import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Dashboard } from "@/components/analytics/dashboard";
import { parseFilters } from "@/lib/filters";
import { parseMetric } from "@/lib/metrics";
import { parsePeriod, toDateRange } from "@/lib/period";
import { sharedBreakdown, sharedGoalConversions, sharedSite, sharedSummary, sharedTimeseries } from "@/lib/share";

type Props = {
  params: Promise<{ domain: string }>;
  searchParams: Promise<{
    period?: string | string[];
    metric?: string | string[];
    filter?: string | string[];
  }>;
};

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { domain } = await params;
  const site = await sharedSite(decodeURIComponent(domain));
  // Nothing to say about a dashboard nobody shared, including whether it exists.
  return site ? { title: `${site.domain} · Whole Story` } : { title: "Whole Story" };
}

/**
 * A dashboard its owner chose to make public: the same body as theirs, read-only and without a session.
 * <p>
 * Outside the `(dashboard)` group on purpose — it has no sign-out header and nothing here may edit anything —
 * and outside `(marketing)` as well, so our own tracking script is not injected into a page we hand to
 * somebody else's audience.
 */
export default async function SharedDashboard({ params, searchParams }: Props) {
  const { domain } = await params;
  const query = await searchParams;

  // A site that is not shared, one that was removed and a domain nobody tracks all arrive here as null.
  const site = await sharedSite(decodeURIComponent(domain));
  if (!site) notFound();

  const period = parsePeriod(query.period);
  const metric = parseMetric(query.metric);
  const filters = parseFilters(query.filter);
  const range = toDateRange(period, site.timezone);
  const interval = period === "today" ? "hour" : "day";

  const [stats, points, goals, pages, sources, countries, regions, cities, browsers, systems, devices] =
    await Promise.all([
      sharedSummary(site.domain, range, filters),
      sharedTimeseries(site.domain, range, filters),
      sharedGoalConversions(site.domain, range, filters),
      sharedBreakdown(site.domain, range, "PAGE", filters),
      sharedBreakdown(site.domain, range, "SOURCE", filters),
      sharedBreakdown(site.domain, range, "COUNTRY", filters),
      sharedBreakdown(site.domain, range, "REGION", filters),
      sharedBreakdown(site.domain, range, "CITY", filters),
      sharedBreakdown(site.domain, range, "BROWSER", filters),
      sharedBreakdown(site.domain, range, "OS", filters),
      sharedBreakdown(site.domain, range, "DEVICE", filters),
    ]);

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 px-6 py-10">
      <Dashboard
        heading={
          <div className="flex flex-col gap-1">
            <h1 className="font-semibold text-2xl tracking-tight">{site.domain}</h1>
            <p className="text-muted-foreground text-xs">Public dashboard · days start and end in {site.timezone}</p>
          </div>
        }
        basePath={`/share/${encodeURIComponent(site.domain)}`}
        timezone={site.timezone}
        period={period}
        metric={metric}
        filters={filters}
        interval={interval}
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

      <footer className="text-muted-foreground text-xs">
        Measured by{" "}
        <Link href="/" className="underline underline-offset-4">
          Whole Story
        </Link>{" "}
        — privacy-first analytics. No cookies, no personal data, no IP addresses stored.
      </footer>
    </main>
  );
}
