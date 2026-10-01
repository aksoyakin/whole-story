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
    // The value may be empty: that is the row a breakdown shows for visits where the dimension is unknown.
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
