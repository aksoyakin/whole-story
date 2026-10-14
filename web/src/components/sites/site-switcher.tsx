import Link from "next/link";
import type { Metric } from "@/lib/metrics";
import type { Period } from "@/lib/period";
import type { Site } from "@/lib/sites";

type Props = {
  /** Every site this organization has; the list the dashboard already fetched to find the current one. */
  sites: Site[];
  current: Site;
  period: Period;
  metric: Metric;
};

/**
 * Moving to another site's dashboard without going back to the list.
 *
 * A disclosure of plain links rather than a `<select>` that navigates on change. There is no client
 * JavaScript, nothing to hydrate and no component state that can drift from the URL — which is the failure
 * this project has already met twice in forms (D-129, D-132). It works with the keyboard and without
 * JavaScript, because a disclosure of links is all it ever was.
 *
 * The period and the metric travel with the move; the filters do not. How you are looking is a property of
 * the reader, but what you have filtered to names values belonging to the site you are leaving — carrying a
 * `/pricing` page filter onto another domain would produce an empty dashboard that reads as broken rather
 * than as filtered.
 *
 * Only rendered on the owner's dashboard. A shared dashboard must never list the other sites of the person
 * who shared it (D-137): the link grants one site, not an inventory.
 */
export function SiteSwitcher({ sites, current, period, metric }: Props) {
  const others = sites.filter((site) => site.siteId !== current.siteId);
  if (others.length === 0) {
    return <h1 className="font-semibold text-2xl tracking-tight">{current.domain}</h1>;
  }

  // Deliberately without the filters: see above.
  const query = new URLSearchParams({ period, metric });

  return (
    <div className="flex flex-wrap items-center gap-3">
      <h1 className="font-semibold text-2xl tracking-tight">{current.domain}</h1>
      <details className="relative">
        <summary className="inline-flex cursor-pointer list-none items-center gap-1 rounded-full border border-border bg-muted px-3 py-1 text-muted-foreground text-xs transition-colors hover:bg-background [&::-webkit-details-marker]:hidden">
          Switch site
          <span aria-hidden="true">▾</span>
        </summary>
        <nav
          aria-label="Other sites"
          className="absolute left-0 z-10 mt-2 min-w-56 rounded-md border border-border bg-background p-1 shadow-sm"
        >
          <ul className="flex flex-col">
            {others.map((site) => (
              <li key={site.siteId}>
                <Link
                  href={`/sites/${site.siteId}?${query}`}
                  className="block rounded-sm px-3 py-2 text-sm transition-colors hover:bg-muted"
                >
                  {site.domain}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      </details>
    </div>
  );
}
