import { describe, expect, it } from "vitest";
import { intervalFor, MAX_RANGE_DAYS, parseCustomRange, parsePeriod, parseSelection, toDateRange } from "./period";

describe("parsePeriod", () => {
  it("accepts known periods", () => {
    expect(parsePeriod("today")).toBe("today");
    expect(parsePeriod("30d")).toBe("30d");
  });

  it("falls back to 7 days for missing or unknown values", () => {
    expect(parsePeriod(undefined)).toBe("7d");
    expect(parsePeriod("forever")).toBe("7d");
    expect(parsePeriod(["7d", "today"])).toBe("7d");
  });
});

describe("toDateRange", () => {
  const now = new Date("2026-09-27T21:30:00Z");

  it("covers only today", () => {
    expect(toDateRange("today", "UTC", now)).toEqual({ from: "2026-09-27", to: "2026-09-27" });
  });

  it("includes today in the last 7 days", () => {
    expect(toDateRange("7d", "UTC", now)).toEqual({ from: "2026-09-21", to: "2026-09-27" });
  });

  it("crosses month boundaries", () => {
    expect(toDateRange("30d", "UTC", now)).toEqual({ from: "2026-08-29", to: "2026-09-27" });
  });

  /** The site decides what "today" is, which is the whole reason the timezone is stored on it (D-020). */
  it("asks what day it is where the site is, not where the viewer is", () => {
    // 21:30 UTC is already the 28th in Istanbul and still the 27th in Los Angeles.
    expect(toDateRange("today", "Europe/Istanbul", now)).toEqual({ from: "2026-09-28", to: "2026-09-28" });
    expect(toDateRange("today", "America/Los_Angeles", now)).toEqual({ from: "2026-09-27", to: "2026-09-27" });
  });

  it("shifts whole calendar days, so a daylight saving change cannot move the boundary", () => {
    // The US falls back on 1 November 2026; a range across it is still seven calendar days.
    const afterTheChange = new Date("2026-11-03T12:00:00Z");

    expect(toDateRange("7d", "America/New_York", afterTheChange)).toEqual({
      from: "2026-10-28",
      to: "2026-11-03",
    });
  });
});

describe("a typed range", () => {
  it("is used when the URL says custom and the dates are real", () => {
    expect(parseSelection({ period: "custom", from: "2026-01-01", to: "2026-03-31" })).toEqual({
      from: "2026-01-01",
      to: "2026-03-31",
    });
  });

  it("is passed through untouched, because it is already a pair of local days", () => {
    expect(toDateRange({ from: "2026-01-01", to: "2026-03-31" }, "Europe/Istanbul")).toEqual({
      from: "2026-01-01",
      to: "2026-03-31",
    });
  });

  /**
   * The ceiling is the api's and this copy only spares the form a request it knows will be refused, so the
   * boundary has to be pinned on both sides. 366 days is a year counted inclusively from 1 January.
   */
  it("stops at the same length the api stops at", () => {
    expect(MAX_RANGE_DAYS).toBe(366);
    expect(parseCustomRange("2026-01-01", "2027-01-01")).not.toBeNull();
    expect(parseCustomRange("2026-01-01", "2027-01-02")).toBeNull();
  });

  it("refuses anything it would not have produced itself", () => {
    expect(parseCustomRange("2026-03-31", "2026-01-01")).toBeNull(); // backwards
    expect(parseCustomRange("2026-02-30", "2026-03-01")).toBeNull(); // not a day that exists
    expect(parseCustomRange("31/03/2026", "2026-04-01")).toBeNull(); // not the shape
    expect(parseCustomRange("2026-01-01", undefined)).toBeNull();
  });

  it("falls back to the default rather than erroring, the way an unknown filter is dropped", () => {
    expect(parseSelection({ period: "custom", from: "2026-03-31", to: "2026-01-01" })).toBe("7d");
    expect(parseSelection({ period: "custom" })).toBe("7d");
    // from and to without period=custom are not a range: the named period still wins.
    expect(parseSelection({ period: "30d", from: "2026-01-01", to: "2026-03-31" })).toBe("30d");
  });

  it("reads a single day hour by hour, like the api does", () => {
    expect(intervalFor({ from: "2026-01-01", to: "2026-01-01" })).toBe("hour");
    expect(intervalFor({ from: "2026-01-01", to: "2026-01-02" })).toBe("day");
  });
});
