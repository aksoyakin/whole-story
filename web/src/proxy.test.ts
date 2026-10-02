import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { proxy } from "./proxy";

function request(path: string, cookie?: string): NextRequest {
  const headers = new Headers();
  if (cookie) {
    headers.set("cookie", cookie);
  }
  return new NextRequest(new URL(path, "http://localhost:3000"), { headers });
}

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
    expect(proxy(request("/login", "SESSION=stale")).headers.get("location")).toBeNull();
    expect(proxy(request("/register", "SESSION=stale")).headers.get("location")).toBeNull();
    expect(proxy(request("/sites", "SESSION=stale")).headers.get("location")).toBeNull();
  });

  /** Whoever is following a reset link has no session by definition, and the link is the only way in. */
  it("lets the password reset pages through without a session", () => {
    expect(proxy(request("/forgot-password")).headers.get("location")).toBeNull();
    expect(proxy(request("/reset-password?token=abc")).headers.get("location")).toBeNull();
  });
});
