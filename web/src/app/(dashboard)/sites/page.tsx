import type { Metadata } from "next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { requireUser } from "@/lib/auth";

export const metadata: Metadata = { title: "Sites · Whole Story" };

export default async function SitesPage() {
  const user = await requireUser();

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 px-6 py-10">
      <header className="flex flex-col gap-1">
        <h1 className="font-semibold text-2xl tracking-tight">Sites</h1>
        <p className="text-muted-foreground text-sm">Signed in as {user.name}.</p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="font-normal text-muted-foreground text-sm">No sites yet</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm">
            Adding a site arrives with the next slice: Site Management will register the domain, hand you the snippet,
            and tell the collector to start accepting events for it.
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
