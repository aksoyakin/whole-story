import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { removeGoal } from "@/app/(dashboard)/sites/[siteId]/goals/actions";
import { AddGoalForm } from "@/components/goals/add-goal-form";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { requireUser } from "@/lib/auth";
import { GOAL_TYPE_LABELS, type GoalType } from "@/lib/goal-types";
import { goalsOf } from "@/lib/goals";
import { sitesOf } from "@/lib/sites";

export const metadata: Metadata = { title: "Goals · Whole Story" };

type Props = { params: Promise<{ siteId: string }> };

/**
 * Goals are managed here rather than on the dashboard, which stays read-only: the same page is what a public
 * share link will render, and an add-and-remove form has no business on it.
 */
export default async function GoalsPage({ params }: Props) {
  const user = await requireUser();
  const { siteId } = await params;

  const sites = await sitesOf(user.organizationId);
  const site = sites.find((candidate) => candidate.siteId === siteId);
  if (!site) notFound();

  const goals = await goalsOf(siteId);

  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-6 px-6 py-10">
      <header className="flex flex-col gap-1">
        <h1 className="font-semibold text-2xl tracking-tight">Goals</h1>
        <p className="text-muted-foreground text-sm">
          What counts as a conversion on {site.domain}.{" "}
          <Link href={`/sites/${siteId}`} className="underline underline-offset-4">
            Back to the dashboard
          </Link>
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="font-normal text-muted-foreground text-sm">Add a goal</CardTitle>
        </CardHeader>
        <CardContent>
          <AddGoalForm siteId={siteId} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="font-normal text-muted-foreground text-sm">
            {goals.length === 0 ? "No goals yet" : `${goals.length} ${goals.length === 1 ? "goal" : "goals"}`}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {goals.length === 0 ? (
            <p className="text-muted-foreground text-sm">
              A goal is a question asked of the events already collected, so one added today reports on last month as
              well.
            </p>
          ) : (
            <ul className="flex flex-col gap-3">
              {goals.map((goal) => (
                <li key={goal.goalId} className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex flex-col">
                    <code className="font-mono text-sm">{goal.target}</code>
                    <span className="text-muted-foreground text-xs">
                      {GOAL_TYPE_LABELS[goal.type as GoalType] ?? goal.type}
                    </span>
                  </div>
                  <form action={removeGoal}>
                    <input type="hidden" name="siteId" value={siteId} />
                    <input type="hidden" name="goalId" value={goal.goalId} />
                    <Button type="submit" variant="destructive" size="sm">
                      Remove
                    </Button>
                  </form>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </main>
  );
}
