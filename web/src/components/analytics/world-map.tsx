"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ComposableMap, Geographies, Geography } from "react-simple-maps";
import { ALPHA2_BY_NUMERIC } from "@/lib/country-codes";
import type { BreakdownEntry } from "@/lib/stats";

const numbers = new Intl.NumberFormat("en-US");
const countryNames = new Intl.DisplayNames(["en"], { type: "region" });

/**
 * A sequential ramp: one hue, light to dark. The theme has no hue at all, and a neutral ramp is sound used this
 * way — it is only as a categorical palette that it fails, because lightness alone cannot tell two series apart.
 * The lightest step is left out: it does not reach three to one against the page, and a country has to be
 * readable as filled or not.
 */
const STEPS = ["var(--chart-2)", "var(--chart-3)", "var(--chart-4)", "var(--chart-5)"];
const NO_VISITORS = "var(--muted)";

type Hovered = { name: string; visitors: number; x: number; y: number };

type Props = {
  entries: BreakdownEntry[];
  /** The dashboard's URL without a country filter; clicking a country appends its own. */
  filterHrefBase: string;
};

/**
 * Where the visitors are. The ranked list beside it carries the same numbers, so this is the picture rather than
 * the only way to read them — which is what a map drawn by the browser has to be.
 */
export function WorldMap({ entries, filterHrefBase }: Props) {
  const router = useRouter();
  const [hovered, setHovered] = useState<Hovered | null>(null);

  const visitorsByCountry = new Map(entries.filter((entry) => entry.key !== "").map((e) => [e.key, e.visitors]));
  const highest = Math.max(0, ...visitorsByCountry.values());

  return (
    <figure className="relative flex flex-col gap-3">
      <div className="w-full [&_svg]:h-auto [&_svg]:w-full">
        <ComposableMap
          projection="geoEqualEarth"
          projectionConfig={{ scale: 150 }}
          width={800}
          height={380}
          aria-label="Visitors by country"
        >
          <Geographies geography="/geo/countries-110m.json">
            {({ geographies }) =>
              geographies.map((geo) => {
                const code = ALPHA2_BY_NUMERIC[String(geo.id)];
                const visitors = code ? (visitorsByCountry.get(code) ?? 0) : 0;
                const name = code ? nameOf(code) : (geo.properties?.name ?? "");
                return (
                  <Geography
                    key={geo.rsmKey}
                    geography={geo}
                    fill={fillFor(visitors, highest)}
                    stroke="var(--background)"
                    strokeWidth={0.5}
                    style={{ outline: "none" }}
                    onMouseEnter={(event) => setHovered({ name, visitors, x: event.clientX, y: event.clientY })}
                    onMouseMove={(event) =>
                      setHovered((current) => (current ? { ...current, x: event.clientX, y: event.clientY } : current))
                    }
                    onMouseLeave={() => setHovered(null)}
                    onClick={() => code && visitors > 0 && router.push(`${filterHrefBase}&filter=COUNTRY:${code}`)}
                    className={visitors > 0 && code ? "cursor-pointer transition-opacity hover:opacity-80" : undefined}
                  />
                );
              })
            }
          </Geographies>
        </ComposableMap>
      </div>

      {hovered && (
        <div
          role="status"
          className="pointer-events-none fixed z-10 rounded-lg border border-border bg-card px-3 py-2 text-sm shadow-sm"
          style={{ left: hovered.x + 12, top: hovered.y + 12 }}
        >
          <p className="font-medium">{hovered.name}</p>
          <p className="text-muted-foreground tabular-nums">
            {hovered.visitors === 0 ? "No visitors" : `${numbers.format(hovered.visitors)} visitors`}
          </p>
        </div>
      )}

      <figcaption className="flex items-center gap-2 text-muted-foreground text-xs">
        <span>Fewer</span>
        {[NO_VISITORS, ...STEPS].map((step) => (
          <span
            key={step}
            aria-hidden="true"
            className="h-3 w-6 rounded-[2px] border border-border"
            style={{ backgroundColor: step }}
          />
        ))}
        <span>More</span>
        <span className="ml-2">
          {highest === 0 ? "No visits yet" : `Up to ${numbers.format(highest)} visitors in one country`}
        </span>
      </figcaption>
    </figure>
  );
}

function fillFor(visitors: number, highest: number): string {
  if (visitors <= 0 || highest <= 0) {
    return NO_VISITORS;
  }
  const share = visitors / highest;
  const step = Math.min(STEPS.length - 1, Math.floor(share * STEPS.length - 1e-9));
  return STEPS[Math.max(0, step)];
}

function nameOf(code: string): string {
  try {
    return countryNames.of(code) ?? code;
  } catch {
    return code;
  }
}
