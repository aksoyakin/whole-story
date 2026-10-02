"use client";

import { useActionState } from "react";
import type { ForgotPasswordState } from "@/app/(auth)/actions";
import { requestPasswordReset } from "@/app/(auth)/actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const initial: ForgotPasswordState = {};

export function ForgotPasswordForm() {
  const [state, submit, pending] = useActionState(requestPasswordReset, initial);

  // The same words whether or not the address has an account: this form must not answer who is registered.
  if (state.sent) {
    return (
      <p role="status" className="text-muted-foreground text-sm">
        If that address has an account, a link is on its way. It works for one hour.
      </p>
    );
  }

  return (
    <form action={submit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="email">E-mail</Label>
        <Input id="email" name="email" type="email" autoComplete="email" required maxLength={254} />
      </div>
      {state.error && (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      )}
      <Button type="submit" size="lg" disabled={pending}>
        {pending ? "One moment…" : "Send the link"}
      </Button>
    </form>
  );
}
