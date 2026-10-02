"use client";

import { useActionState, useState } from "react";
import { type DefineGoalState, defineGoal } from "@/app/(dashboard)/sites/[siteId]/goals/actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { GOAL_TYPE_LABELS, type GoalType } from "@/lib/goal-types";
import { cn } from "@/lib/utils";

const initial: DefineGoalState = {};

const TYPES = Object.keys(GOAL_TYPE_LABELS) as GoalType[];

const HINTS: Record<GoalType, { placeholder: string; hint: string }> = {
  EVENT: {
    placeholder: "Signup",
    hint: "The name your site passes to wholestory(), exactly as it is written there.",
  },
  PAGEVIEW: {
    placeholder: "/thanks",
    hint: "A path beginning with '/'. Use * for any part of it: /blog/* counts every post.",
  },
};

/**
 * The type is chosen with buttons and submitted through a hidden field rather than with radio inputs.
 * React resets a form once its action returns, which puts a radio's DOM state back to its default while this
 * component's state stays where the reader left it: the selection then disagrees with the field label beside it,
 * and the next submission carries a type nobody chose. Nothing here reads the DOM, so the two cannot drift.
 */
export function AddGoalForm({ siteId }: { siteId: string }) {
  const [state, submit, pending] = useActionState(defineGoal, initial);
  const [type, setType] = useState<GoalType>("EVENT");

  return (
    <form action={submit} className="flex flex-col gap-4">
      <input type="hidden" name="siteId" value={siteId} />
      <input type="hidden" name="type" value={type} />

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 font-medium text-sm">What counts as a conversion?</legend>
        <div className="flex w-fit gap-1 rounded-lg bg-muted p-1">
          {TYPES.map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => setType(option)}
              aria-pressed={type === option}
              className={cn(
                "rounded-md px-3 py-1.5 text-sm transition-colors",
                type === option ? "bg-background font-medium shadow-sm" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {GOAL_TYPE_LABELS[option]}
            </button>
          ))}
        </div>
      </fieldset>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="target">{type === "EVENT" ? "Event name" : "Path"}</Label>
        <div className="flex flex-wrap items-center gap-2">
          <Input
            id="target"
            name="target"
            required
            maxLength={1024}
            placeholder={HINTS[type].placeholder}
            className="max-w-xs"
          />
          <Button type="submit" disabled={pending}>
            {pending ? "Adding…" : "Add goal"}
          </Button>
        </div>
        <p className="text-muted-foreground text-xs">{HINTS[type].hint}</p>
      </div>

      {state.error && (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      )}
    </form>
  );
}
