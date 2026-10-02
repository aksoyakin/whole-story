import type { Metadata } from "next";
import Link from "next/link";
import { ResetPasswordForm } from "@/components/auth/reset-password-form";

export const metadata: Metadata = { title: "Choose a new password · Whole Story" };

type Props = { searchParams: Promise<{ token?: string | string[] }> };

export default async function ResetPasswordPage({ searchParams }: Props) {
  const { token } = await searchParams;
  // The token travels in the link, so it is read here and posted back; it never becomes part of this page's state.
  const value = Array.isArray(token) ? token[0] : token;

  if (!value) {
    return (
      <div className="flex flex-col gap-4">
        <h1 className="font-semibold text-2xl tracking-tight">This link is incomplete</h1>
        <p className="text-muted-foreground text-sm">
          Open the link from the mail again, or{" "}
          <Link href="/forgot-password" className="text-foreground underline underline-offset-4">
            ask for a new one
          </Link>
          .
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="font-semibold text-2xl tracking-tight">Choose a new password</h1>
        <p className="text-muted-foreground text-sm">Everywhere you are currently signed in will be signed out.</p>
      </div>
      <ResetPasswordForm token={value} />
    </div>
  );
}
