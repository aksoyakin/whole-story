import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { proxy } from "./proxy";

const MARKETING = "wholestory.world";
const APP = "app.wholestory.world";

function request(path: string, { cookie, host }: { cookie?: string; host?: string } = {}): NextRequest {
  const headers = new Headers();
  if (cookie) headers.set("cookie", cookie);
  if (host) headers.set("host", host);
  return new NextRequest(new URL(path, `http://${host ?? "localhost:3000"}`), { headers });
}

function splitHosts() {
  vi.stubEnv("MARKETING_HOST", MARKETING);
  vi.stubEnv("APP_HOST", APP);
}

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("the dashboard gate", () => {
  it("sends a visitor without a session to the sign-in page", () => {
    const response = proxy(request("/sites"));

    expect(response.headers.get("location")).toBe("http://localhost:3000/login");
  });

  it("lets the sign-in page through for a visitor without a session", () => {
    expect(proxy(request("/login")).headers.get("location")).toBeNull();
  });

  /** The loop this prevents: a stale cookie passes the gate, the page gets a 401 and redirects to /login,
      and a gate that treated the cookie as proof of a session would send it straight back. */
  it("never decides that a cookie means someone is signed in", () => {
    expect(proxy(request("/login", { cookie: "SESSION=stale" })).headers.get("location")).toBeNull();
    expect(proxy(request("/register", { cookie: "SESSION=stale" })).headers.get("location")).toBeNull();
    expect(proxy(request("/sites", { cookie: "SESSION=stale" })).headers.get("location")).toBeNull();
  });

  /** Whoever is following a reset link has no session by definition, and the link is the only way in. */
  it("lets the password reset pages through without a session", () => {
    expect(proxy(request("/forgot-password")).headers.get("location")).toBeNull();
    expect(proxy(request("/reset-password?token=abc")).headers.get("location")).toBeNull();
  });

  /** A shared dashboard is read by people who have no account at all. */
  it("never asks a public page for a session", () => {
    expect(proxy(request("/")).headers.get("location")).toBeNull();
    expect(proxy(request("/share/example.com")).headers.get("location")).toBeNull();
  });
});

describe("host routing", () => {
  it("does nothing when the two hosts are not configured", () => {
    // Development serves everything from one address; routing by host there would break it.
    expect(proxy(request("/login", { host: "localhost:3000" })).headers.get("location")).toBeNull();
    expect(proxy(request("/", { host: "localhost:3000" })).headers.get("location")).toBeNull();
  });

  it("moves the account pages off the marketing host", () => {
    splitHosts();

    // The cookie is there so that only the host rule can be what redirects.
    const response = proxy(request("/sites", { host: MARKETING, cookie: "SESSION=live" }));

    expect(response.headers.get("location")).toBe(`https://${APP}/sites`);
    expect(response.status).toBe(308);
  });

  it("moves the sign-in pages off the marketing host", () => {
    splitHosts();

    expect(proxy(request("/login", { host: MARKETING })).headers.get("location")).toBe(`https://${APP}/login`);
    expect(proxy(request("/reset-password", { host: MARKETING })).headers.get("location")).toBe(
      `https://${APP}/reset-password`,
    );
  });

  /** A reset link carries its token in the query string, so losing it would make the link useless. */
  it("keeps the query string when it moves a request", () => {
    splitHosts();

    expect(proxy(request("/reset-password?token=abc123", { host: MARKETING })).headers.get("location")).toBe(
      `https://${APP}/reset-password?token=abc123`,
    );
  });

  /** The landing page existed at two addresses until now, which is duplicate content. */
  it("moves a shared dashboard off the app host", () => {
    splitHosts();

    expect(proxy(request("/share/example.com", { host: APP })).headers.get("location")).toBe(
      `https://${MARKETING}/share/example.com`,
    );
  });

  it("sends the app host's root to the sites list rather than to the sales pitch", () => {
    splitHosts();

    const response = proxy(request("/", { host: APP, cookie: "SESSION=live" }));

    expect(response.headers.get("location")).toBe(`http://${APP}/sites`);
  });

  it("leaves each host alone on the paths it owns", () => {
    splitHosts();

    expect(proxy(request("/sites", { host: APP, cookie: "SESSION=live" })).headers.get("location")).toBeNull();
    expect(proxy(request("/", { host: MARKETING })).headers.get("location")).toBeNull();
    expect(proxy(request("/share/example.com", { host: MARKETING })).headers.get("location")).toBeNull();
  });
});
