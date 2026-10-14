package world.wholestory.api.analytics.application;

/**
 * Which number a time series carries.
 * <p>
 * It is a parameter rather than two columns of one answer because the dashboard draws one measure at a time
 * (ADR 0020): visitors and pageviews are different scales, and two y-axes would let the scaling decide what
 * the picture says. The chart toggles between them and the server-rendered table beside it prints whichever
 * is drawn, so nothing has ever shown both at once — while the endpoint computed both every time.
 * <p>
 * The two cost very different amounts, which is why this matters beyond tidiness. Pageviews are summed from
 * an hourly rollup. Visitors are a distinct count per bucket over the sessions, and over a long range that is
 * the whole price of the endpoint — paid, until now, even by someone looking at a pageview chart.
 * <p>
 * Called a measure rather than a metric because this project already uses "metric" for what Micrometer
 * publishes (ADR 0024), and because that is the word ADR 0020 used for it.
 */
public enum Measure {
    VISITORS,
    PAGEVIEWS
}
