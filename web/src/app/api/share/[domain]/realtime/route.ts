import { NextResponse } from "next/server";
import { api } from "@/lib/api/client";

/**
 * The same number for a shared dashboard, which is read by people who have no account here at all.
 *
 * No cookie is forwarded, deliberately: whether this may be answered is the site's sharing flag, and api is
 * what knows it. A site that is not shared refuses exactly as one that does not exist.
 */
export async function GET(_request: Request, { params }: { params: Promise<{ domain: string }> }) {
  const { domain } = await params;
  const { data, response } = await api.GET("/api/public/sites/{domain}/stats/realtime", {
    params: { path: { domain: decodeURIComponent(domain) } },
    cache: "no-store",
  });
  if (!response.ok || !data) {
    return new NextResponse(null, { status: response.status });
  }
  return NextResponse.json(data);
}
