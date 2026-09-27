import { mkdirSync, readFileSync } from "node:fs";
import { gzipSync } from "node:zlib";
import { build } from "esbuild";

/** Hard budget from the product promise: the tracker must stay under 1 KB gzipped (D-050). */
const BUDGET_BYTES = 1024;
const outfile = "dist/ws.js";

mkdirSync("dist", { recursive: true });
await build({
  entryPoints: ["src/ws.ts"],
  outfile,
  bundle: true,
  minify: true,
  format: "iife",
  target: "es2019",
  legalComments: "none",
});

const raw = readFileSync(outfile);
const gzipped = gzipSync(raw, { level: 9 }).length;
console.log(`${outfile}: ${raw.length} B minified, ${gzipped} B gzipped (budget ${BUDGET_BYTES} B)`);
if (gzipped > BUDGET_BYTES) {
  console.error(`Tracker exceeds its size budget by ${gzipped - BUDGET_BYTES} B`);
  process.exit(1);
}
