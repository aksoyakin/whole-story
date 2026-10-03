import { NextResponse } from "next/server";
import { api } from "@/lib/api/client";
import { sessionHeaders } from "@/lib/auth";

/**
 * The visitors on a site right now, for the dashboard to poll.
 *
 * The first route handler in this app, and it exists for something the server components cannot do: this one
 * number has to change without the page being navigated, while everything else is rendered once per request.
 *
 * It forwards the session cookie and nothing else. api decides whether the caller may have the number, and its
 * refusal is passed on as it came — a browser that is told zero would keep asking and keep believing it.
 */
export async function GET(_request: Request, { params }: { params: Promise<{ siteId: string }> }) {
  const { siteId } = await params;
  const { data, response } = await api.GET("/api/sites/{siteId}/stats/realtime", {
    params: { path: { siteId } },
    headers: await sessionHeaders(),
    cache: "no-store",
  });
  if (!response.ok || !data) {
    return new NextResponse(null, { status: response.status });
  }
  return NextResponse.json(data);
}
