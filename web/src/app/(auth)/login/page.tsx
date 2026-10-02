import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { signIn } from "@/app/(auth)/actions";
import { AuthForm } from "@/components/auth/auth-form";
import { currentUser } from "@/lib/auth";

export const metadata: Metadata = { title: "Sign in · Whole Story" };

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ reset?: string }> }) {
  // Asked here rather than in the proxy, which cannot tell a valid cookie from a stale one.
  if (await currentUser()) {
    redirect("/sites");
  }
  // A reset signs every session out, so whoever just set a password lands here and should be told why.
  const { reset } = await searchParams;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="font-semibold text-2xl tracking-tight">Sign in</h1>
        <p className="text-muted-foreground text-sm">
          {reset === "done" ? "Your password has been changed. Sign in with it." : "Your dashboards are waiting."}
        </p>
      </div>
      <AuthForm action={signIn} submitLabel="Sign in" />
      <div className="flex flex-col gap-1 text-muted-foreground text-sm">
        <p>
          <Link href="/forgot-password" className="text-foreground underline underline-offset-4">
            Forgotten your password?
          </Link>
        </p>
        <p>
          No account yet?{" "}
          <Link href="/register" className="text-foreground underline underline-offset-4">
            Create one
          </Link>
        </p>
      </div>
    </div>
  );
}
