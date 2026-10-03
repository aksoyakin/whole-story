import { describe, expect, it } from "vitest";
import { absentLabel, parseFilters, serialiseFilters, withFilter, withoutFilter } from "./filters";

describe("parseFilters", () => {
  it("reads a repeated parameter", () => {
    expect(parseFilters(["COUNTRY:DE", "PAGE:/pricing"])).toEqual([
      { dimension: "COUNTRY", value: "DE" },
      { dimension: "PAGE", value: "/pricing" },
    ]);
  });

  it("keeps an empty value, which is the row for visits with no value for the dimension", () => {
    expect(parseFilters("SOURCE:")).toEqual([{ dimension: "SOURCE", value: "" }]);
  });

  it("drops anything it does not recognise rather than guessing", () => {
    expect(parseFilters(["UTM_SOURCE:x", "PAGE", ":DE", ""])).toEqual([]);
  });

  it("round-trips through the query string", () => {
    const filters = parseFilters(["CITY:745044", "SOURCE:"]);
    expect(serialiseFilters(filters)).toEqual(["CITY:745044", "SOURCE:"]);
  });
});

describe("withFilter", () => {
  it("replaces the filter on a dimension instead of adding a second one", () => {
    const first = withFilter([], "COUNTRY", "DE");
    expect(withFilter(first, "COUNTRY", "TR")).toEqual([{ dimension: "COUNTRY", value: "TR" }]);
  });

  it("removes only the dimension asked for", () => {
    const filters = withFilter(withFilter([], "COUNTRY", "DE"), "PAGE", "/");
    expect(withoutFilter(filters, "COUNTRY")).toEqual([{ dimension: "PAGE", value: "/" }]);
  });
});

describe("absentLabel", () => {
  /** A visit with no source is not a visit whose source is unknown (ADR 0015). */
  it("names an absent source rather than calling it unknown", () => {
    expect(absentLabel("SOURCE")).toBe("Direct / None");
  });

  /** The country is known and nothing finer is; that is the geo database's limit, not lost data (D-130). */
  it("says a region or city is not available rather than unknown", () => {
    expect(absentLabel("REGION")).toBe("Not available");
    expect(absentLabel("CITY")).toBe("Not available");
  });

  it("calls anything genuinely undetermined not set", () => {
    expect(absentLabel("COUNTRY")).toBe("(not set)");
    expect(absentLabel("BROWSER")).toBe("(not set)");
    expect(absentLabel("DEVICE")).toBe("(not set)");
  });
});
