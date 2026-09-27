/**
 * Public pages track themselves with the same snippet customers install (dogfooding).
 * The dashboard is not tracked.
 */
export default function MarketingLayout({ children }: LayoutProps<"/">) {
  return (
    <>
      {children}
      <script defer data-domain="wholestory.world" src="/js/ws.js" />
    </>
  );
}
