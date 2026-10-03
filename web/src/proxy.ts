import { type NextRequest, NextResponse } from "next/server";

const SESSION_COOKIE = "SESSION";
const SIGNED_OUT_PAGES = new Set(["/login", "/register", "/forgot-password", "/reset-password"]);

/**
 * Two jobs, in this order (Next 16 calls this file convention a proxy).
 *
 * **Which host serves what.** The marketing site and the public dashboards live on the apex domain, the
 * account and its dashboards on `app.`. Until now both hosts served everything, which cost two things: the
 * landing page existed at two addresses, and the session cookie is host-only (D-079), so someone signed in on
 * `app.` who followed a link to the apex fell through to `/login` and could open a second session there.
 * Sending each path to the host that owns it removes both. Permanent redirects, because the mapping is not
 * going to change and search engines should be told which address is the real one.
 *
 * **Who may see the dashboard.** This can only tell whether a session cookie exists, which says nothing about
 * whether the session behind it is still valid, so it only ever denies: no cookie, no dashboard. Deciding that
 * someone *is* signed in is left to the pages, which ask api. Trusting the cookie in both directions caused a
 * redirect loop: a stale cookie got past this gate, the page asked api, api said 401, the page redirected to
 * `/login`, and this gate sent it straight back.
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const host = request.headers.get("host");
  const marketingHost = process.env.MARKETING_HOST;
  const appHost = process.env.APP_HOST;

  // Development serves both halves from one address; without both names there is nothing to route between.
  if (marketingHost && appHost && host) {
    if (host === appHost && pathname === "/") {
      // Somebody who typed the dashboard host wants their sites, not the sales pitch.
      return NextResponse.redirect(new URL(`/sites${search}`, request.url), 308);
    }
    if (host === appHost && isPublic(pathname)) {
      return redirectToHost(marketingHost, pathname, search);
    }
    if (host === marketingHost && isAccount(pathname)) {
      return redirectToHost(appHost, pathname, search);
    }
  }

  if (!SIGNED_OUT_PAGES.has(pathname) && !isPublic(pathname) && !request.cookies.has(SESSION_COOKIE)) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

/** Readable by anybody: the landing page, and the dashboards their owners chose to share. */
function isPublic(pathname: string): boolean {
  return pathname === "/" || pathname.startsWith("/share/");
}

/** Behind a session, or on the way into one. */
function isAccount(pathname: string): boolean {
  return (
    SIGNED_OUT_PAGES.has(pathname) ||
    pathname === "/sites" ||
    pathname.startsWith("/sites/") ||
    pathname.startsWith("/account/")
  );
}

function redirectToHost(host: string, pathname: string, search: string) {
  return NextResponse.redirect(new URL(`${pathname}${search}`, `https://${host}`), 308);
}

export const config = {
  matcher: [
    "/",
    "/login",
    "/register",
    "/forgot-password",
    "/reset-password",
    "/sites",
    "/sites/:path*",
    "/account/:path*",
    "/share/:path*",
  ],
};
