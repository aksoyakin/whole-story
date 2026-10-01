import Link from "next/link";
import type { ActiveFilter } from "@/lib/filters";
import { DIMENSION_LABELS } from "@/lib/filters";

export type Chip = { filter: ActiveFilter; label: string; removeHref: string };

const countries = new Intl.DisplayNames(["en"], { type: "region" });

/** What the dashboard is narrowed to, and the way back out of each one. */
export function FilterChips({ chips, clearHref }: { chips: Chip[]; clearHref: string }) {
  if (chips.length === 0) {
    return null;
  }
  return (
    <section className="flex flex-wrap items-center gap-2" aria-label="Active filters">
      {chips.map((chip) => (
        <Link
          key={`${chip.filter.dimension}:${chip.filter.value}`}
          href={chip.removeHref}
          className="group inline-flex items-center gap-2 rounded-full border border-border bg-muted px-3 py-1 text-sm transition-colors hover:bg-background"
        >
          <span className="text-muted-foreground">{DIMENSION_LABELS[chip.filter.dimension]}</span>
          <span className="font-medium">{display(chip)}</span>
          <span aria-hidden="true" className="text-muted-foreground group-hover:text-foreground">
            ×
          </span>
          <span className="sr-only">Remove this filter</span>
        </Link>
      ))}
      {chips.length > 1 && (
        <Link href={clearHref} className="text-muted-foreground text-sm underline underline-offset-4">
          Clear all
        </Link>
      )}
    </section>
  );
}

/** A country is named by the browser here too, so the chip reads the same as the row that was clicked. */
function display(chip: Chip): string {
  if (chip.filter.dimension !== "COUNTRY" || chip.filter.value === "") {
    return chip.label;
  }
  try {
    return countries.of(chip.filter.value) ?? chip.label;
  } catch {
    return chip.label;
  }
}
