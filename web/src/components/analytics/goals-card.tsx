import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { GoalConversion } from "@/lib/goal-types";

const numbers = new Intl.NumberFormat("en-US");
const percent = new Intl.NumberFormat("en-US", { style: "percent", maximumFractionDigits: 1 });

/**
 * Conversions, one row per goal.
 * <p>
 * The bar is scaled by the rate rather than by the largest row, because a rate is already a share of something:
 * a goal nobody completed should look empty, not merely shorter than its neighbour.
 */
export function GoalsCard({ conversions, manageHref }: { conversions: GoalConversion[]; manageHref: string }) {
  return (
    <Card>
      <CardHeader className="flex flex-wrap items-center justify-between gap-3">
        <CardTitle className="font-normal text-muted-foreground text-sm">Goals</CardTitle>
        <Link href={manageHref} className="text-muted-foreground text-sm underline underline-offset-4">
          {conversions.length === 0 ? "Add a goal" : "Manage goals"}
        </Link>
      </CardHeader>
      <CardContent>
        {conversions.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            Nothing is counted as a conversion yet. A goal is a custom event your site reports, or a page being reached.
          </p>
        ) : (
          <ol className="flex flex-col gap-2">
            {conversions.map((goal) => (
              <li key={goal.goalId} className="relative flex items-center justify-between gap-4 text-sm">
                <div
                  aria-hidden="true"
                  className="absolute inset-y-0 left-0 rounded-r-[4px] bg-muted"
                  style={{ width: `${Math.min(100, goal.rate * 100)}%` }}
                />
                <span className="relative truncate py-1 pl-2">{goal.target}</span>
                <span className="relative flex shrink-0 items-baseline gap-3 py-1 pr-2">
                  <span className="text-muted-foreground text-xs tabular-nums">
                    {numbers.format(goal.visitors)} {goal.visitors === 1 ? "visitor" : "visitors"} ·{" "}
                    {numbers.format(goal.completions)} total
                  </span>
                  <span className="font-medium tabular-nums">{percent.format(goal.rate)}</span>
                </span>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  );
}
