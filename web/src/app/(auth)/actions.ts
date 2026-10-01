"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { api } from "@/lib/api/client";
import { SESSION_COOKIE } from "@/lib/auth";
import { parseSetCookie } from "@/lib/set-cookie";

export type AuthFormState = { error?: string };

const AFTER_SIGN_IN = "/sites";

export async function register(_previous: AuthFormState, form: FormData): Promise<AuthFormState> {
  const { response, error } = await api.POST("/api/auth/register", {
    body: {
      email: String(form.get("email") ?? ""),
      name: String(form.get("name") ?? ""),
      password: String(form.get("password") ?? ""),
    },
  });
  if (error || !response.ok) {
    return {
      error:
        response.status === 409
          ? "An account with this e-mail address already exists."
          : "Check the form and try again.",
    };
  }
  await adoptSession(response);
  redirect(AFTER_SIGN_IN);
}

export async function signIn(_previous: AuthFormState, form: FormData): Promise<AuthFormState> {
  const { response, error } = await api.POST("/api/auth/login", {
    body: {
      email: String(form.get("email") ?? ""),
      password: String(form.get("password") ?? ""),
    },
  });
  if (error || !response.ok) {
    // One message for a wrong password and for an address that was never registered, so the form cannot be
    // used to find out which e-mail addresses have accounts.
    return { error: "Wrong e-mail address or password." };
  }
  await adoptSession(response);
  redirect(AFTER_SIGN_IN);
}

export async function signOut(): Promise<void> {
  const store = await cookies();
  const session = store.get(SESSION_COOKIE);
  if (session) {
    await api.POST("/api/auth/logout", { headers: { cookie: `${SESSION_COOKIE}=${session.value}` } });
    store.delete(SESSION_COOKIE);
  }
  redirect("/login");
}

/**
 * Hands api's session cookie to the browser unchanged. The cookie is api's: this app forwards it rather than
 * keeping a session of its own, so that every authorization rule stays in api (ADR 0018).
 */
async function adoptSession(response: Response): Promise<void> {
  const store = await cookies();
  for (const header of response.headers.getSetCookie()) {
    const parsed = parseSetCookie(header);
    if (parsed) {
      store.set(parsed.name, parsed.value, parsed.options);
    }
  }
}
