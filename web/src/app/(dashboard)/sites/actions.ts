"use server";

import { revalidatePath } from "next/cache";
import { api } from "@/lib/api/client";
import { sessionHeaders } from "@/lib/auth";

export type AddSiteState = { error?: string };

export async function addSite(_previous: AddSiteState, form: FormData): Promise<AddSiteState> {
  const { response, error } = await api.POST("/api/sites", {
    body: {
      organizationId: String(form.get("organizationId") ?? ""),
      domain: String(form.get("domain") ?? ""),
      // Blank means UTC; api decides that rather than the form guessing on its behalf.
      timezone: String(form.get("timezone") ?? ""),
    },
    headers: await sessionHeaders(),
  });
  if (error || !response.ok) {
    return { error: messageFor(response.status) };
  }
  revalidatePath("/sites");
  return {};
}

export async function removeSite(form: FormData): Promise<void> {
  await api.DELETE("/api/sites/{siteId}", {
    params: { path: { siteId: String(form.get("siteId") ?? "") } },
    headers: await sessionHeaders(),
  });
  revalidatePath("/sites");
}

function messageFor(status: number): string {
  switch (status) {
    case 409:
      return "That domain is already being tracked.";
    case 403:
      return "You cannot add a site to this organization.";
    case 400:
      return "Enter a plain hostname, like example.com.";
    default:
      return "Could not add the site. Try again.";
  }
}
