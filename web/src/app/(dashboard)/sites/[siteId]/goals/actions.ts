"use server";

import { revalidatePath } from "next/cache";
import { api } from "@/lib/api/client";
import { sessionHeaders } from "@/lib/auth";
import type { GoalType } from "@/lib/goal-types";

export type DefineGoalState = { error?: string };

export async function defineGoal(_previous: DefineGoalState, form: FormData): Promise<DefineGoalState> {
  const siteId = String(form.get("siteId") ?? "");
  const { response, error } = await api.POST("/api/sites/{siteId}/goals", {
    params: { path: { siteId } },
    body: {
      type: String(form.get("type") ?? "EVENT") as GoalType,
      target: String(form.get("target") ?? ""),
    },
    headers: await sessionHeaders(),
  });
  if (error || !response.ok) {
    return { error: messageFor(response.status) };
  }
  revalidatePath(`/sites/${siteId}/goals`);
  revalidatePath(`/sites/${siteId}`);
  return {};
}

export async function removeGoal(form: FormData): Promise<void> {
  const siteId = String(form.get("siteId") ?? "");
  await api.DELETE("/api/sites/{siteId}/goals/{goalId}", {
    params: { path: { siteId, goalId: String(form.get("goalId") ?? "") } },
    headers: await sessionHeaders(),
  });
  revalidatePath(`/sites/${siteId}/goals`);
  revalidatePath(`/sites/${siteId}`);
}

function messageFor(status: number): string {
  switch (status) {
    case 409:
      return "This site already counts that as a conversion.";
    case 404:
      return "That site is not yours to change.";
    case 400:
      // api refuses a target that could never match: a path without its leading slash, one carrying a query
      // string, an empty event name. Its own message is more specific, but it is not translated.
      return "Enter an event name, or a path beginning with '/' such as /thanks or /blog/*.";
    default:
      return "Could not add the goal. Try again.";
  }
}
