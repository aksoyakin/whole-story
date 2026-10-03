"use client";

import { useEffect, useState } from "react";

const REFRESH_MS = 15_000;

const numbers = new Intl.NumberFormat("en-US");

type Props = {
  /** The route handler to ask; the owner's and the shared dashboard's differ in what they are allowed to answer. */
  href: string;
  /** Rendered on the server, so the number is right on first paint and there is nothing to flash. */
  initial: number;
};

/**
 * How many people are on the site right now, kept current while the page is open.
 * <p>
 * The one number here that would be a lie if it were rendered once and left: a dashboard stays open far
 * longer than the few minutes this counts over.
 * <p>
 * The server's value wins whenever it changes. Without that, navigating — a new period, a new filter — would
 * re-render the page with a fresh number while this component kept the one it last polled, and the two would
 * disagree on screen. That is the same drift that made a saved timezone show the old zone (D-132), in the one
 * shape this component can have it.
 */
export function RealtimeVisitors({ href, initial }: Props) {
  const [visitors, setVisitors] = useState(initial);

  useEffect(() => setVisitors(initial), [initial]);

  useEffect(() => {
    let cancelled = false;
    const ask = async () => {
      try {
        const response = await fetch(href, { cache: "no-store" });
        // A refusal means the session went or the dashboard stopped being shared: keep the last number rather
        // than showing a zero nobody can tell apart from an empty site.
        if (!response.ok) return;
        const body: { visitors: number } = await response.json();
        if (!cancelled) setVisitors(body.visitors);
      } catch {
        // A dropped request is not worth anything on screen.
      }
    };
    const timer = setInterval(ask, REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [href]);

  return (
    <p className="flex items-center gap-2 text-muted-foreground text-sm" aria-live="polite">
      <span
        aria-hidden="true"
        className={visitors > 0 ? "size-2 rounded-full bg-foreground" : "size-2 rounded-full bg-border"}
      />
      <span className="tabular-nums">{numbers.format(visitors)}</span>
      {visitors === 1 ? "current visitor" : "current visitors"}
    </p>
  );
}
