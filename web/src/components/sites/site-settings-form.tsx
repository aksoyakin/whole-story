"use client";

import { useActionState } from "react";
import { type SiteSettingsState, updateSiteSettings } from "@/app/(dashboard)/sites/[siteId]/settings/actions";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";

const initial: SiteSettingsState = {};

type Props = {
  siteId: string;
  timezone: string;
  publicDashboard: boolean;
  /** Every zone the server knows, resolved there so the list cannot differ from the one rendered here. */
  timezones: string[];
};

/**
 * Both fields are uncontrolled, and both are keyed on the value that was last saved.
 * <p>
 * React resets a form once its action returns (D-129), and a reset restores each field to its *attribute* —
 * which React writes only when the field mounts. Leaving it at that was measured and was wrong: after saving
 * Asia/Tokyo the database held Asia/Tokyo while the select had snapped back to the zone the page was opened
 * with, so the page stated a setting the site no longer had. Keying each field on the saved value remounts it
 * when the revalidated page sends the new one, so the attribute and the stored value cannot drift apart.
 * <p>
 * The key stays off the form itself: remounting that would restart `useActionState` and discard the very
 * message the save had just produced.
 */
export function SiteSettingsForm({ siteId, timezone, publicDashboard, timezones }: Props) {
  const [state, submit, pending] = useActionState(updateSiteSettings, initial);

  return (
    <form action={submit} className="flex flex-col gap-6">
      <input type="hidden" name="siteId" value={siteId} />

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="timezone">Timezone</Label>
        <select
          key={timezone}
          id="timezone"
          name="timezone"
          defaultValue={timezone}
          className="h-8 w-full max-w-xs rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          {timezones.map((zone) => (
            <option key={zone} value={zone}>
              {zone}
            </option>
          ))}
        </select>
        <p className="text-muted-foreground text-xs">
          Where this site&apos;s day starts and ends. Changing it re-cuts every period on the dashboard, including past
          ones — nothing already collected is altered or lost.
        </p>
      </div>

      <div className="flex flex-col gap-1.5">
        <div className="flex items-center gap-2">
          <input
            key={String(publicDashboard)}
            id="publicDashboard"
            name="publicDashboard"
            type="checkbox"
            defaultChecked={publicDashboard}
            className="size-4 rounded border-input accent-primary"
          />
          <Label htmlFor="publicDashboard">Public dashboard</Label>
        </div>
        <p className="text-muted-foreground text-xs">
          Lets anyone holding the link read this site&apos;s dashboard. The shareable link itself is still being built;
          until then this only records the choice.
        </p>
      </div>

      {state.error && (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      )}
      {state.saved && !state.error && (
        <p role="status" className="text-muted-foreground text-sm">
          Saved.
        </p>
      )}

      <Button type="submit" className="self-start" disabled={pending}>
        {pending ? "Saving…" : "Save settings"}
      </Button>
    </form>
  );
}
