import type { Metadata } from "next";
import Link from "next/link";
import { ForgotPasswordForm } from "@/components/auth/forgot-password-form";

export const metadata: Metadata = { title: "Forgotten password · Whole Story" };

export default function ForgotPasswordPage() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="font-semibold text-2xl tracking-tight">Forgotten password</h1>
        <p className="text-muted-foreground text-sm">We will mail you a link to choose a new one.</p>
      </div>
      <ForgotPasswordForm />
      <p className="text-muted-foreground text-sm">
        <Link href="/login" className="text-foreground underline underline-offset-4">
          Back to signing in
        </Link>
      </p>
    </div>
  );
}
