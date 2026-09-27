package world.wholestory.processor.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.processor.sessionization.SessionizedEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static world.wholestory.processor.persistence.MultiRowInsert.utc;
import static world.wholestory.processor.persistence.MultiRowInsert.values;

@Component
@RequiredArgsConstructor
class SessionWriter {

    private static final String UPSERT = """
            insert into analytics.sessions (
                session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events, entry_page, exit_page,
                referrer, referrer_source, utm_source, utm_medium, utm_campaign, utm_content, utm_term,
                country_code, subdivision_code, city_geoname_id,
                browser, browser_version, os, os_version, device_type)
            values %s
            on conflict (session_id, started_at) do update set
                ended_at  = greatest(sessions.ended_at, excluded.ended_at),
                exit_page = case when excluded.ended_at >= sessions.ended_at
                                 then excluded.exit_page else sessions.exit_page end,
                pageviews = sessions.pageviews + excluded.pageviews,
                events    = sessions.events + excluded.events
            """;
    private static final String ROW = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcClient jdbc;

    /** {@code events} must contain only newly inserted events, in arrival order. */
    void upsert(List<SessionizedEvent> events) {
        Map<UUID, List<SessionizedEvent>> bySession = new LinkedHashMap<>();
        for (SessionizedEvent event : events) {
            bySession.computeIfAbsent(event.sessionId(), id -> new ArrayList<>()).add(event);
        }

        List<Object> params = new ArrayList<>(bySession.size() * 24);
        for (List<SessionizedEvent> sessionEvents : bySession.values()) {
            SessionizedEvent first = sessionEvents.getFirst();
            RawEventV1 entry = first.event();
            RawEventV1 last = sessionEvents.getLast().event();
            long pageviews = sessionEvents.stream().filter(e -> e.event().representsPageview()).count();

            params.add(first.sessionId());
            params.add(utc(first.sessionStartedAt()));
            params.add(entry.siteId());
            params.add(entry.visitorHash());
            params.add(utc(last.timestamp()));
            params.add((int) pageviews);
            params.add(sessionEvents.size());
            params.add(entry.pathname());
            params.add(last.pathname());
            params.add(entry.referrer());
            params.add(null); // referrer_source: M2
            params.add(entry.utmSource());
            params.add(entry.utmMedium());
            params.add(entry.utmCampaign());
            params.add(entry.utmContent());
            params.add(entry.utmTerm());
            params.add(entry.countryCode());
            params.add(entry.subdivisionCode());
            params.add(entry.cityGeonameId());
            params.add(null); // browser: M2
            params.add(null); // browser_version: M2
            params.add(null); // os: M2
            params.add(null); // os_version: M2
            params.add(null); // device_type: M2
        }
        jdbc.sql(UPSERT.formatted(values(ROW, bySession.size()))).params(params).update();
    }
}
