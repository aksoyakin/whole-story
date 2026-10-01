"use client";

import { useActionState } from "react";
import { type AddSiteState, addSite } from "@/app/(dashboard)/sites/actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const initial: AddSiteState = {};

export function AddSiteForm({ organizationId }: { organizationId: string }) {
  const [state, submit, pending] = useActionState(addSite, initial);
  // The visitor's own zone is the best guess for where their day starts; it is editable in settings later.
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <form action={submit} className="flex flex-col gap-3">
      <input type="hidden" name="organizationId" value={organizationId} />
      <input type="hidden" name="timezone" value={timezone} />
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="domain">Domain</Label>
        <div className="flex flex-wrap items-center gap-2">
          <Input id="domain" name="domain" placeholder="example.com" required maxLength={253} className="max-w-xs" />
          <Button type="submit" disabled={pending}>
            {pending ? "Adding…" : "Add site"}
          </Button>
        </div>
        <p className="text-muted-foreground text-xs">
          Just the hostname. Reporting uses {timezone} to decide where a day starts.
        </p>
      </div>
      {state.error && (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      )}
    </form>
  );
}
