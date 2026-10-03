import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { SiteSettingsForm } from "@/components/sites/site-settings-form";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { requireUser } from "@/lib/auth";
import { sitesOf } from "@/lib/sites";

export const metadata: Metadata = { title: "Site settings · Whole Story" };

type Props = { params: Promise<{ siteId: string }> };

/** Settings live on a page of their own, like goals: the dashboard stays read-only for the share link. */
export default async function SiteSettingsPage({ params }: Props) {
  const user = await requireUser();
  const { siteId } = await params;

  const sites = await sitesOf(user.organizationId);
  const site = sites.find((candidate) => candidate.siteId === siteId);
  if (!site) notFound();

  // Resolved here rather than in the browser: Node and the browser do not necessarily carry the same ICU data,
  // and a list that differed between the render and the hydration would be a mismatch.
  const zones = Intl.supportedValuesOf("timeZone");
  // A zone stored under an older alias would otherwise vanish from its own select.
  const timezones = zones.includes(site.timezone) ? zones : [site.timezone, ...zones];

  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-6 px-6 py-10">
      <header className="flex flex-col gap-1">
        <h1 className="font-semibold text-2xl tracking-tight">Settings</h1>
        <p className="text-muted-foreground text-sm">
          {site.domain} ·{" "}
          <Link href={`/sites/${siteId}`} className="underline underline-offset-4">
            Back to the dashboard
          </Link>
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="font-normal text-muted-foreground text-sm">Reporting and sharing</CardTitle>
        </CardHeader>
        <CardContent>
          <SiteSettingsForm
            siteId={siteId}
            timezone={site.timezone}
            publicDashboard={site.publicDashboard}
            timezones={timezones}
          />
        </CardContent>
      </Card>
    </main>
  );
}
