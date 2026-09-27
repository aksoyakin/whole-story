import { describe, expect, it } from "vitest";
import { parsePeriod, toDateRange } from "./period";

describe("parsePeriod", () => {
  it("accepts known periods", () => {
    expect(parsePeriod("7d")).toBe("7d");
  });

  it("falls back to 30 days for missing or unknown values", () => {
    expect(parsePeriod(undefined)).toBe("30d");
    expect(parsePeriod("forever")).toBe("30d");
    expect(parsePeriod(["7d", "today"])).toBe("30d");
  });
});

describe("toDateRange", () => {
  const now = new Date("2026-09-27T21:30:00Z");

  it("covers only today", () => {
    expect(toDateRange("today", now)).toEqual({ from: "2026-09-27", to: "2026-09-27" });
  });

  it("includes today in the last 7 days", () => {
    expect(toDateRange("7d", now)).toEqual({ from: "2026-09-21", to: "2026-09-27" });
  });

  it("crosses month boundaries", () => {
    expect(toDateRange("30d", now)).toEqual({ from: "2026-08-29", to: "2026-09-27" });
  });
});
