import { describe, expect, it } from "vitest";
import { parsePeriod, toDateRange } from "./period";

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
