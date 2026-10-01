import "server-only";
import { api } from "@/lib/api/client";
import { sessionHeaders } from "@/lib/auth";

export type Site = {
  siteId: string;
  domain: string;
  timezone: string;
  publicDashboard: boolean;
  createdAt: string;
};

/** The snippet a customer pastes into their site. The tracker is served from the public host, not the dashboard. */
export const TRACKER_SRC = process.env.TRACKER_URL ?? "http://localhost:3000/js/ws.js";

export async function sitesOf(organizationId: string): Promise<Site[]> {
  const { data } = await api.GET("/api/sites", {
    params: { query: { organizationId } },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  return data ?? [];
}
