"use client";

import { useActionState } from "react";
import type { AuthFormState } from "@/app/(auth)/actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type Props = {
  action: (state: AuthFormState, form: FormData) => Promise<AuthFormState>;
  submitLabel: string;
  /** Registration also asks for a name; signing in does not. */
  withName?: boolean;
};

export function AuthForm({ action, submitLabel, withName = false }: Props) {
  const [state, submit, pending] = useActionState(action, {});

  return (
    <form action={submit} className="flex flex-col gap-4">
      {withName && (
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="name">Name</Label>
          <Input id="name" name="name" autoComplete="name" required maxLength={120} />
        </div>
      )}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="email">E-mail</Label>
        <Input id="email" name="email" type="email" autoComplete="email" required maxLength={254} />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="password">Password</Label>
        <Input
          id="password"
          name="password"
          type="password"
          autoComplete={withName ? "new-password" : "current-password"}
          required
          minLength={8}
          maxLength={200}
        />
        {withName && <p className="text-muted-foreground text-xs">At least 8 characters. Length is all we ask for.</p>}
      </div>
      {state.error && (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      )}
      <Button type="submit" size="lg" disabled={pending}>
        {pending ? "One moment…" : submitLabel}
      </Button>
    </form>
  );
}
