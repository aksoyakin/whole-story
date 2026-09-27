import "server-only";
import createClient from "openapi-fetch";
import type { paths } from "./schema";

/**
 * Typed client for the internal api service. Server-side only: the browser never talks to the api (BFF, D-048).
 */
export const api = createClient<paths>({
  baseUrl: process.env.API_URL ?? "http://localhost:8080",
});
