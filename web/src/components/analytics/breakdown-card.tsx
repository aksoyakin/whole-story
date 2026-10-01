import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { BreakdownEntry } from "@/lib/stats";

const numbers = new Intl.NumberFormat("en-US");
const countries = new Intl.DisplayNames(["en"], { type: "region" });

type Props = {
  title: string;
  entries: BreakdownEntry[];
  /** What the row with no value is called; it differs per dimension (see `absentLabel`). */
  absent: string;
  /** Country codes are the one key the browser can name better than the server can. */
  asCountry?: boolean;
  /** Device types are stored as the three canonical lower-case tokens (D-065); the dashboard capitalises them. */
  capitalise?: boolean;
  /**
   * The key is an identifier rather than something to read — a city is a geoname id. Without a name there is
   * nothing to print, so the row says so instead of showing the number.
   */
  keyIsAnId?: boolean;
  /** Narrows the whole dashboard to this row. Without it the rows are plain text. */
  filterHref?: (key: string) => string;
};

/**
 * A ranked list of one dimension. Every row carries its own number, so the bar is a comparison aid rather than
 * the only way to read the value — which is what makes a single neutral fill enough.
 */
export function BreakdownCard({
  title,
  entries,
  absent,
  asCountry = false,
  capitalise = false,
  keyIsAnId = false,
  filterHref,
}: Props) {
  const highest = entries.reduce((most, entry) => Math.max(most, entry.visitors), 0);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-normal text-muted-foreground text-sm">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        {entries.length === 0 ? (
          <p className="text-muted-foreground text-sm">Nothing yet.</p>
        ) : (
          <ol className="flex flex-col gap-2">
            {entries.map((entry) => (
              <li key={entry.key} className="relative flex items-center justify-between gap-4 text-sm">
                <div
                  aria-hidden="true"
                  className="absolute inset-y-0 left-0 rounded-r-[4px] bg-muted"
                  style={{ width: `${highest === 0 ? 0 : (entry.visitors / highest) * 100}%` }}
                />
                {filterHref ? (
                  <Link
                    href={filterHref(entry.key)}
                    className="relative truncate py-1 pl-2 underline-offset-4 hover:underline"
                  >
                    {display(entry, { absent, asCountry, capitalise, keyIsAnId })}
                  </Link>
                ) : (
                  <span className="relative truncate py-1 pl-2">
                    {display(entry, { absent, asCountry, capitalise, keyIsAnId })}
                  </span>
                )}
                <span className="relative shrink-0 py-1 pr-2 tabular-nums">{numbers.format(entry.visitors)}</span>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  );
}

function display(
  entry: BreakdownEntry,
  options: { absent: string; asCountry: boolean; capitalise: boolean; keyIsAnId: boolean },
): string {
  if (entry.key === "") return options.absent;
  if (options.asCountry) {
    try {
      return countries.of(entry.key) ?? entry.key;
    } catch {
      return entry.key;
    }
  }
  // The server falls back to the key when it has no name; for an id that fallback is not readable.
  if (options.keyIsAnId && entry.label === entry.key) return options.absent;
  return options.capitalise ? entry.label.charAt(0).toUpperCase() + entry.label.slice(1) : entry.label;
}
