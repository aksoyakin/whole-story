import type { Metadata } from "next";
import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

export const metadata: Metadata = {
  title: "Whole Story — privacy-first web analytics",
  description:
    "Cookieless web analytics. No cookies, no personal data, no IP addresses stored, and a tracking script under a kilobyte.",
};

const SOURCE = "https://github.com/aksoyakin/whole-story";

/**
 * The tracking script's gzipped size, as the build reports it. Stated rather than rounded because the whole
 * page's argument is that its numbers are measured — which makes it a number that has to be updated when
 * the tracker is: `pnpm --filter @wholestory/tracker build` prints it, and the build fails past 1 KB.
 */
const TRACKER_BYTES = 583;

const SNIPPET = `<script defer
  data-domain="your-site.com"
  src="https://wholestory.world/js/ws.js"></script>`;

/**
 * The one marketing page. Deliberately not a set of them: a pricing page would advertise plans that do not
 * exist, and the install instructions are shown by the dashboard at the moment somebody needs them.
 *
 * Every number here is measured and the page says where from. That is the whole argument it makes, so an
 * unmeasured adjective would cost more than it bought.
 */
export default function Home() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-20 px-6 py-10 sm:py-16">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <span className="font-medium tracking-tight">Whole Story</span>
        <nav className="flex items-center gap-5 text-muted-foreground text-sm">
          <a href={SOURCE} className="underline-offset-4 hover:underline">
            Source
          </a>
          <Link href="/login" className="underline-offset-4 hover:underline">
            Sign in
          </Link>
        </nav>
      </header>

      <main className="flex flex-col gap-20">
        <section className="flex flex-col gap-6">
          <h1 className="max-w-xl font-semibold text-4xl tracking-tight sm:text-5xl">
            Know your traffic. Keep nothing about your visitors.
          </h1>
          <p className="max-w-xl text-lg text-muted-foreground">
            Cookieless web analytics: no cookies, no personal data, no IP addresses stored. There is no banner to show
            and nothing to leak.
          </p>
          <div className="flex flex-wrap items-center gap-3">
            {/* The button's styles on a link, since these navigate. Base UI's Button takes `render` rather
                than `asChild`, and a link is what this is. */}
            <Link href="/share/wholestory.world" className={buttonVariants({ size: "lg" })}>
              See it live
            </Link>
            <Link href="/register" className={buttonVariants({ variant: "outline", size: "lg" })}>
              Create an account
            </Link>
          </div>
          <p className="max-w-xl text-muted-foreground text-sm">
            That dashboard is this website's own traffic, measured by the thing you are reading about, and public
            because its owner chose to share it.
          </p>
        </section>

        <section className="flex flex-col gap-8">
          <h2 className="font-medium text-xl tracking-tight">What "cookieless" actually means here</h2>
          {/* Two columns rather than three: at this width a third of the page is too narrow for prose, and a
              term that wrapped to two lines pushed its own description out of line with its neighbours'. */}
          <dl className="flex flex-col gap-6">
            <div className="grid gap-1 sm:grid-cols-[15rem_1fr] sm:gap-8">
              <dt className="font-medium text-sm">Nothing is stored on the device</dt>
              <dd className="text-muted-foreground text-sm">
                No cookie, no local storage, no identifier of any kind. A visitor is recognised for one day by a hash
                the server computes and cannot reverse.
              </dd>
            </div>
            <div className="grid gap-1 sm:grid-cols-[15rem_1fr] sm:gap-8">
              <dt className="font-medium text-sm">The day's key is thrown away</dt>
              <dd className="text-muted-foreground text-sm">
                That hash is salted with a random value replaced every day, and yesterday's is deleted. The same person
                is a different number tomorrow, to us as much as to anyone else.
              </dd>
            </div>
            <div className="grid gap-1 sm:grid-cols-[15rem_1fr] sm:gap-8">
              <dt className="font-medium text-sm">The address never gets further than the door</dt>
              <dd className="text-muted-foreground text-sm">
                It is used to hash and to look up a country, then dropped by the first service that sees it. It reaches
                no queue, no log and no database.
              </dd>
            </div>
          </dl>
        </section>

        <section className="flex flex-col gap-6">
          <h2 className="font-medium text-xl tracking-tight">One tag, {TRACKER_BYTES} bytes</h2>
          <p className="max-w-xl text-muted-foreground">
            Put this in your <code className="font-mono text-sm">&lt;head&gt;</code>. There is nothing else to configure
            and nothing for your visitors to accept.
          </p>
          <pre className="overflow-x-auto rounded-lg border bg-muted/50 p-4 font-mono text-xs leading-relaxed">
            <code>{SNIPPET}</code>
          </pre>
          <p className="max-w-xl text-muted-foreground text-sm">
            {TRACKER_BYTES} bytes is the gzipped size of that script, and the build fails if it grows past a kilobyte.
            It counts pageviews, single-page navigations and any event you name.
          </p>
        </section>

        <section className="flex flex-col gap-6">
          <h2 className="font-medium text-xl tracking-tight">Measured, not asserted</h2>
          <dl className="flex flex-col gap-6 sm:grid sm:grid-cols-3">
            <div className="flex flex-col gap-1">
              <dt className="font-semibold text-2xl tabular-nums tracking-tight">8,000</dt>
              <dd className="text-muted-foreground text-sm">
                events a second accepted, with the service's own 99th percentile at 10 ms
              </dd>
            </div>
            <div className="flex flex-col gap-1">
              <dt className="font-semibold text-2xl tabular-nums tracking-tight">983,780</dt>
              <dd className="text-muted-foreground text-sm">
                events accepted in one run, and 983,780 rows stored — nothing was lost
              </dd>
            </div>
            <div className="flex flex-col gap-1">
              <dt className="font-semibold text-2xl tabular-nums tracking-tight">4,279,247</dt>
              <dd className="text-muted-foreground text-sm">
                records read a second time, changing none of the stored numbers
              </dd>
            </div>
          </dl>
          <p className="max-w-xl text-muted-foreground text-sm">
            From a load test on one laptop running the production images, so it is a floor rather than a capacity — the
            generator ran out of room before the services did.{" "}
            <a href={`${SOURCE}/blob/main/docs/performance.md`} className="underline underline-offset-4">
              The method, the full tables, and the two measurements that turned out to be measuring the measuring
            </a>
            .
          </p>
        </section>
      </main>

      <footer className="flex flex-col gap-2 border-t pt-8 text-muted-foreground text-sm">
        <p>
          Open source under the{" "}
          <a href={`${SOURCE}/blob/main/LICENSE`} className="underline underline-offset-4">
            GNU GPLv3
          </a>
          .
        </p>
        <p>
          This product includes GeoLite2 data created by MaxMind, available from{" "}
          <a href="https://www.maxmind.com" className="underline underline-offset-4">
            maxmind.com
          </a>
          .
        </p>
      </footer>
    </div>
  );
}
