"use client";

import { useActionState, useState } from "react";
import { type DefineGoalState, defineGoal } from "@/app/(dashboard)/sites/[siteId]/goals/actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { GOAL_TYPE_LABELS, type GoalType } from "@/lib/goals";

const initial: DefineGoalState = {};

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

export function AddGoalForm({ siteId }: { siteId: string }) {
  const [state, submit, pending] = useActionState(defineGoal, initial);
  // Held here so the field can say what it expects; the value is submitted as a radio either way.
  const [type, setType] = useState<GoalType>("EVENT");

  return (
    <form action={submit} className="flex flex-col gap-4">
      <input type="hidden" name="siteId" value={siteId} />

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 font-medium text-sm">What counts as a conversion?</legend>
        {(Object.keys(GOAL_TYPE_LABELS) as GoalType[]).map((option) => (
          <label key={option} className="flex items-center gap-2 text-sm">
            <input
              type="radio"
              name="type"
              value={option}
              checked={type === option}
              onChange={() => setType(option)}
              className="size-4 accent-primary"
            />
            {GOAL_TYPE_LABELS[option]}
          </label>
        ))}
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
