import type { Dimension } from "@/lib/stats";

export type ActiveFilter = { dimension: Dimension; value: string };

const DIMENSIONS: Dimension[] = [
  "PAGE",
  "ENTRY_PAGE",
  "EXIT_PAGE",
  "SOURCE",
  "COUNTRY",
  "REGION",
  "CITY",
  "BROWSER",
  "OS",
  "DEVICE",
];

export const DIMENSION_LABELS: Record<Dimension, string> = {
  PAGE: "Page",
  ENTRY_PAGE: "Entry page",
  EXIT_PAGE: "Exit page",
  SOURCE: "Source",
  COUNTRY: "Country",
  REGION: "Region",
  CITY: "City",
  BROWSER: "Browser",
  OS: "Operating system",
  DEVICE: "Device",
};

/** Reads the repeated `filter=DIMENSION:value` parameter; anything unrecognised is dropped rather than guessed. */
export function parseFilters(raw: string | string[] | undefined): ActiveFilter[] {
  const entries = raw === undefined ? [] : Array.isArray(raw) ? raw : [raw];
  return entries.flatMap((entry) => {
    const separator = entry.indexOf(":");
    if (separator < 1) return [];
    const dimension = entry.slice(0, separator) as Dimension;
    // The value may be empty: that is the row a breakdown shows for visits with no value for the dimension.
    return DIMENSIONS.includes(dimension) ? [{ dimension, value: entry.slice(separator + 1) }] : [];
  });
}

export function serialiseFilters(filters: ActiveFilter[]): string[] {
  return filters.map((filter) => `${filter.dimension}:${filter.value}`);
}

/** One filter per dimension: clicking a second country replaces the first rather than asking for both at once. */
export function withFilter(filters: ActiveFilter[], dimension: Dimension, value: string): ActiveFilter[] {
  return [...filters.filter((filter) => filter.dimension !== dimension), { dimension, value }];
}

export function withoutFilter(filters: ActiveFilter[], dimension: Dimension): ActiveFilter[] {
  return filters.filter((filter) => filter.dimension !== dimension);
}

/**
 * What an empty value means in a breakdown, which is not the same thing for every dimension (D-130).
 *
 * For a source it is not missing information at all: the classifier returns nothing exactly when there is no
 * external source to name — the address was typed or bookmarked, the referrer was stripped, or the visitor was
 * moving between pages of the tracked site (ADR 0015).
 *
 * For a region or a city the country is known and nothing finer is: the address belongs to a VPN, to Apple
 * Private Relay, or to a range the geo database only places at country level. That is the privacy tooling
 * working rather than ours failing, and we refuse to guess, so the row says the data is not available instead
 * of implying we lost it.
 *
 * Everything else really is undetermined, and says so in the words the rest of the industry uses.
 */
export function absentLabel(dimension: Dimension): string {
  if (dimension === "SOURCE") return "Direct / None";
  if (dimension === "REGION" || dimension === "CITY") return "Not available";
  return "(not set)";
}
