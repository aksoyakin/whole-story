"use server";

import { revalidatePath } from "next/cache";
import { api } from "@/lib/api/client";
import { sessionHeaders } from "@/lib/auth";

export type SiteSettingsState = { error?: string; saved?: boolean };

export async function updateSiteSettings(_previous: SiteSettingsState, form: FormData): Promise<SiteSettingsState> {
  const siteId = String(form.get("siteId") ?? "");
  const { response, error } = await api.PUT("/api/sites/{siteId}/settings", {
    params: { path: { siteId } },
    body: {
      timezone: String(form.get("timezone") ?? ""),
      // An unticked checkbox is simply absent from the form data, which is what "off" means in HTML. api
      // insists on the flag being present, so this adapter is the only place allowed to read absence as false.
      publicDashboard: form.get("publicDashboard") === "on",
    },
    headers: await sessionHeaders(),
  });
  if (error || !response.ok) {
    return { error: messageFor(response.status) };
  }
  // The timezone decides where every day on the dashboard is cut, so the reports have to be read again too.
  revalidatePath(`/sites/${siteId}/settings`);
  revalidatePath(`/sites/${siteId}`);
  revalidatePath("/sites");
  return { saved: true };
}

function messageFor(status: number): string {
  switch (status) {
    case 404:
      return "That site is not yours to change.";
    case 400:
      return "Choose a timezone from the list.";
    default:
      return "Could not save the settings. Try again.";
  }
}
