import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { register } from "@/app/(auth)/actions";
import { AuthForm } from "@/components/auth/auth-form";
import { currentUser } from "@/lib/auth";

export const metadata: Metadata = { title: "Create an account · Whole Story" };

export default async function RegisterPage() {
  // Asked here rather than in the proxy, which cannot tell a valid cookie from a stale one.
  if (await currentUser()) {
    redirect("/sites");
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="font-semibold text-2xl tracking-tight">Create an account</h1>
        <p className="text-muted-foreground text-sm">No cookies, no personal data, no IP addresses stored.</p>
      </div>
      <AuthForm action={register} submitLabel="Create account" withName />
      <p className="text-muted-foreground text-sm">
        Already have an account?{" "}
        <Link href="/login" className="text-foreground underline underline-offset-4">
          Sign in
        </Link>
      </p>
    </div>
  );
}
