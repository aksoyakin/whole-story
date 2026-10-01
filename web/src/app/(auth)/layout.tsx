import Link from "next/link";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="mx-auto flex min-h-svh w-full max-w-sm flex-col justify-center gap-8 px-6 py-10">
      <Link href="/" className="font-semibold text-lg tracking-tight">
        Whole Story
      </Link>
      {children}
    </main>
  );
}
