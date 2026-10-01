"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";

/** What the customer pastes into their own pages. Nothing else is needed: no account id, no cookie banner. */
export function TrackingSnippet({ domain, src }: { domain: string; src: string }) {
  const [copied, setCopied] = useState(false);
  const snippet = `<script defer data-domain="${domain}" src="${src}"></script>`;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(snippet);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard access can be refused; the snippet is on screen either way.
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="overflow-x-auto rounded-md bg-muted p-3">
        <code className="whitespace-pre font-mono text-xs">{snippet}</code>
      </div>
      <Button type="button" variant="outline" size="sm" className="self-start" onClick={copy}>
        {copied ? "Copied" : "Copy snippet"}
      </Button>
    </div>
  );
}
