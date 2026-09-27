import { readFileSync } from "node:fs";
import { expect, type Page, test } from "@playwright/test";

const TRACKER = readFileSync(new URL("../dist/ws.js", import.meta.url), "utf8");
const QUEUE_SNIPPET =
  "<script>window.wholestory=window.wholestory||function(){(wholestory.q=wholestory.q||[]).push(arguments)}</script>";

declare global {
  interface Window {
    wholestory?: (name: string, options?: { props?: Record<string, string> }) => void;
  }
}

type SentEvent = { name: string; url: string; domain: string; referrer: string | null; props?: Record<string, string> };

/**
 * Serves a fake site at `origin` entirely from Playwright routes (no network, no server)
 * and records every request the tracker sends to the ingest endpoint.
 */
async function site(page: Page, { origin = "https://site.test", head = "", body = "" } = {}) {
  const events: SentEvent[] = [];
  await page.route(`${origin}/**`, (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/js/ws.js") {
      return route.fulfill({ contentType: "application/javascript", body: TRACKER });
    }
    if (url.pathname === "/api/event") {
      events.push(JSON.parse(route.request().postData() ?? "{}"));
      return route.fulfill({ status: 202 });
    }
    return route.fulfill({
      contentType: "text/html",
      body: `<!doctype html><html><head>${head}<script defer data-domain="site.test" src="/js/ws.js"></script></head><body>${body}</body></html>`,
    });
  });
  return events;
}

test("sends a pageview on load", async ({ page }) => {
  const events = await site(page);

  await page.goto("https://site.test/pricing?utm_source=newsletter");

  await expect.poll(() => events).toHaveLength(1);
  expect(events[0]).toEqual({
    name: "pageview",
    url: "https://site.test/pricing?utm_source=newsletter",
    domain: "site.test",
    referrer: null,
  });
});

test("counts client-side navigations once per path", async ({ page }) => {
  const events = await site(page);
  await page.goto("https://site.test/");
  await expect.poll(() => events).toHaveLength(1);

  await page.evaluate(() => {
    history.pushState({}, "", "/docs");
    history.pushState({}, "", "/docs"); // same path again
    location.hash = "#install"; // hash change
  });

  await expect.poll(() => events.map((e) => new URL(e.url).pathname)).toEqual(["/", "/docs"]);
});

test("counts back/forward navigation", async ({ page }) => {
  const events = await site(page);
  await page.goto("https://site.test/");
  await page.evaluate(() => history.pushState({}, "", "/docs"));
  await expect.poll(() => events).toHaveLength(2);

  await page.goBack();

  await expect.poll(() => events.map((e) => new URL(e.url).pathname)).toEqual(["/", "/docs", "/"]);
});

test("sends custom events with props", async ({ page }) => {
  const events = await site(page);
  await page.goto("https://site.test/");
  await expect.poll(() => events).toHaveLength(1);

  await page.evaluate(() => window.wholestory?.("Signup", { props: { plan: "pro" } }));

  await expect.poll(() => events).toHaveLength(2);
  expect(events[1]).toMatchObject({ name: "Signup", props: { plan: "pro" } });
});

test("flushes custom events queued before the script loaded", async ({ page }) => {
  const events = await site(page, {
    head: `${QUEUE_SNIPPET}<script>wholestory("Early",{props:{a:"1"}})</script>`,
  });

  await page.goto("https://site.test/");

  await expect.poll(() => events.map((e) => e.name).sort()).toEqual(["Early", "pageview"]);
});

test("does not count localhost", async ({ page }) => {
  const events = await site(page, { origin: "http://localhost:3000" });

  await page.goto("http://localhost:3000/");
  await page.evaluate(() => window.wholestory?.("Signup"));
  await page.waitForTimeout(500);

  expect(events).toHaveLength(0);
});

test("stores nothing in the browser", async ({ page, context }) => {
  const events = await site(page);
  await page.goto("https://site.test/");
  await expect.poll(() => events).toHaveLength(1);

  expect(await context.cookies()).toHaveLength(0);
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
});
