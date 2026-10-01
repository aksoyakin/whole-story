import "server-only";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { api } from "@/lib/api/client";

/** Name of the session cookie api issues; the browser only ever sees it through this app (ADR 0018). */
export const SESSION_COOKIE = "SESSION";

export type SignedInUser = {
  userId: string;
  email: string;
  name: string;
  organizationId: string;
  role: string;
};

/** The session cookie as a header for api, or nothing when the visitor has none. */
async function sessionHeaders(): Promise<Record<string, string>> {
  const session = (await cookies()).get(SESSION_COOKIE);
  return session ? { cookie: `${SESSION_COOKIE}=${session.value}` } : {};
}

/**
 * Asks api who is signed in. This is the real check: middleware only sees whether a cookie exists, which says
 * nothing about whether the session behind it is still valid.
 */
export async function currentUser(): Promise<SignedInUser | null> {
  const { data } = await api.GET("/api/auth/me", {
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? null;
}

export async function requireUser(): Promise<SignedInUser> {
  const user = await currentUser();
  if (!user) {
    redirect("/login");
  }
  return user;
}
