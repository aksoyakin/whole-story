import type { ActiveFilter } from "@/lib/filters";
import { serialiseFilters } from "@/lib/filters";
import type { Metric } from "@/lib/metrics";
import { MAX_RANGE_DAYS, type CustomRange as Range } from "@/lib/period";

type Props = {
  /** Where the dashboard lives; the form submits back to it. */
  basePath: string;
  /** What is shown now, so the inputs open on it rather than empty. */
  range: Range;
  /** Carried through the submit, because a range is a change of window and not of anything else. */
  metric: Metric;
  filters: ActiveFilter[];
  /** Highlighted while a typed range is what the dashboard is showing. */
  active: boolean;
};

/**
 * Picking an arbitrary window, which `scope.md` has wanted since v1 was written.
 *
 * A plain GET form. The browser turns the fields into the query string the dashboard already reads, so there
 * is no client JavaScript, no state to keep in step with the URL, and the result is a normal link somebody
 * can bookmark or share -- the same reasoning that made the site switcher a disclosure of links rather than a
 * select that navigates.
 *
 * The native date inputs carry the limits as attributes, so a browser refuses an impossible range before a
 * request is made. That is a convenience and not the limit: the api enforces the same ceiling and is the only
 * thing that counts, because a form is trivially bypassed and the public half of these endpoints answers
 * without a session.
 */
export function CustomRangePicker({ basePath, range, metric, filters, active }: Props) {
  return (
    <form method="get" action={basePath} className="flex flex-wrap items-end gap-2">
      <input type="hidden" name="period" value="custom" />
      <input type="hidden" name="metric" value={metric} />
      {serialiseFilters(filters).map((filter) => (
        <input key={filter} type="hidden" name="filter" value={filter} />
      ))}

      <label className="flex flex-col gap-1 text-muted-foreground text-xs">
        From
        <input
          type="date"
          name="from"
          defaultValue={range.from}
          max={range.to}
          required
          className="rounded-md border border-border bg-background px-2 py-1 text-foreground text-sm"
        />
      </label>
      <label className="flex flex-col gap-1 text-muted-foreground text-xs">
        To
        <input
          type="date"
          name="to"
          defaultValue={range.to}
          min={range.from}
          required
          className="rounded-md border border-border bg-background px-2 py-1 text-foreground text-sm"
        />
      </label>
      <button
        type="submit"
        className={
          active
            ? "rounded-md bg-background px-3 py-1.5 font-medium text-sm shadow-sm"
            : "rounded-md px-3 py-1.5 text-muted-foreground text-sm transition-colors hover:text-foreground"
        }
      >
        Apply
      </button>
      <p className="w-full text-muted-foreground text-xs">
        Up to {MAX_RANGE_DAYS} days. Dates are read in the site's own timezone.
      </p>
    </form>
  );
}
