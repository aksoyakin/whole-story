import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Self-contained server for the Docker image; traced from the workspace root (pnpm monorepo).
  output: "standalone",
  outputFileTracingRoot: path.join(import.meta.dirname, ".."),
};

export default nextConfig;
