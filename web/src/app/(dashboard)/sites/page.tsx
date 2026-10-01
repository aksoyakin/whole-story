import type { Metadata } from "next";
import Link from "next/link";
import { removeSite } from "@/app/(dashboard)/sites/actions";
import { AddSiteForm } from "@/components/sites/add-site-form";
import { TrackingSnippet } from "@/components/sites/tracking-snippet";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { requireUser } from "@/lib/auth";
import { toDateRange } from "@/lib/period";
import { sitesOf, TRACKER_SRC } from "@/lib/sites";
import { summary } from "@/lib/stats";

export const metadata: Metadata = { title: "Sites · Whole Story" };

const numbers = new Intl.NumberFormat("en-US");

export default async function SitesPage() {
  const user = await requireUser();
  const sites = await sitesOf(user.organizationId);
  // Each site's own week, because each site decides where its day starts (D-020).
  const visitors = await Promise.all(
    sites.map((site) => summary(site.siteId, toDateRange("7d", site.timezone)).then((stats) => stats.visitors)),
  );

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-8 px-6 py-10">
      <header className="flex flex-col gap-1">
        <h1 className="font-semibold text-2xl tracking-tight">Sites</h1>
        <p className="text-muted-foreground text-sm">
          {sites.length === 0
            ? "Add a site, paste one line into it, and the numbers start arriving."
            : `${sites.length} ${sites.length === 1 ? "site" : "sites"}.`}
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="font-normal text-muted-foreground text-sm">Add a site</CardTitle>
        </CardHeader>
        <CardContent>
          <AddSiteForm organizationId={user.organizationId} />
        </CardContent>
      </Card>

      <section className="flex flex-col gap-4">
        {sites.map((site, index) => (
          <Card key={site.siteId}>
            <CardHeader className="flex flex-wrap items-center justify-between gap-3">
              <CardTitle className="font-medium text-base">
                <Link href={`/sites/${site.siteId}`} className="underline-offset-4 hover:underline">
                  {site.domain}
                </Link>
              </CardTitle>
              <div className="flex items-center gap-3">
                <span className="text-muted-foreground text-sm tabular-nums">
                  {numbers.format(visitors[index])} visitors · 7 days
                </span>
                <span className="text-muted-foreground text-xs">{site.timezone}</span>
                <form action={removeSite}>
                  <input type="hidden" name="siteId" value={site.siteId} />
                  <Button type="submit" variant="destructive" size="sm">
                    Remove
                  </Button>
                </form>
              </div>
            </CardHeader>
            <CardContent>
              <TrackingSnippet domain={site.domain} src={TRACKER_SRC} />
            </CardContent>
          </Card>
        ))}
      </section>
    </main>
  );
}
