import { type NextRequest, NextResponse } from "next/server";

const SESSION_COOKIE = "SESSION";
const SIGNED_OUT_PAGES = new Set(["/login", "/register", "/forgot-password", "/reset-password"]);

/**
 * A cheap gate in front of the dashboard (Next 16 calls this file convention a proxy). It can only see whether a
 * session cookie exists, which says nothing about whether the session behind it is still valid — so it only ever
 * denies: no cookie, no dashboard. Deciding that someone *is* signed in is left to the pages, which ask api.
 *
 * Trusting the cookie in both directions caused a redirect loop: a stale cookie got past this gate, the page
 * asked api, api said 401, the page redirected to /login, and this gate sent it straight back to the dashboard.
 *
 * Host-based routing — marketing on the apex domain, dashboard on `app.` — arrives with the dashboard shell.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (!SIGNED_OUT_PAGES.has(pathname) && !request.cookies.has(SESSION_COOKIE)) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/login", "/register", "/forgot-password", "/reset-password", "/sites", "/sites/:path*", "/account/:path*"],
};
