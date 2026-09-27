/**
 * Whole Story tracker. Cookieless: no storage, no identifiers, no fingerprinting on the client.
 *
 *   <script defer data-domain="example.com" src="https://wholestory.world/js/ws.js"></script>
 *
 * Custom events:  wholestory("Signup", { props: { plan: "pro" } })
 * Calls made before the script loads are queued by the snippet:
 *   <script>window.wholestory = window.wholestory || function () { (wholestory.q = wholestory.q || []).push(arguments) }</script>
 */

type Props = Record<string, string>;
type Options = { props?: Props };
type Track = ((name: string, options?: Options) => void) & { q?: IArguments[] };

interface TrackerWindow extends Window {
  wholestory?: Track;
}

((w: TrackerWindow, d: Document, l: Location, h: History) => {
  const script = d.currentScript as HTMLScriptElement | null;
  if (!script) return;

  const endpoint = script.getAttribute("data-api") || `${new URL(script.src).origin}/api/event`;
  const domain = script.getAttribute("data-domain") || l.hostname;
  let lastPath: string | undefined;

  const send = (name: string, options?: Options) => {
    // Local development and file:// pages are never counted.
    if (/^localhost$|^127(\.\d+){3}$|^\[::1\]$/.test(l.hostname) || l.protocol === "file:") return;

    const body = JSON.stringify({
      name,
      url: l.href,
      domain,
      referrer: d.referrer || null,
      props: options?.props,
    });
    // text/plain keeps the request "simple": no CORS preflight.
    if (!navigator.sendBeacon?.(endpoint, body)) {
      fetch(endpoint, { method: "POST", body, keepalive: true, headers: { "Content-Type": "text/plain" } });
    }
  };

  const pageview = () => {
    // Hash changes and repeated pushState calls to the same path are not new pageviews.
    if (lastPath === l.pathname) return;
    lastPath = l.pathname;
    send("pageview");
  };

  // Single-page apps: count client-side navigations.
  const pushState = h.pushState;
  h.pushState = function (...args) {
    pushState.apply(this, args);
    pageview();
  };
  w.addEventListener("popstate", pageview);

  const queued = w.wholestory?.q || [];
  w.wholestory = (name, options) => send(name, options);
  for (const args of queued) send(args[0], args[1]);

  pageview();
})(window, document, location, history);
