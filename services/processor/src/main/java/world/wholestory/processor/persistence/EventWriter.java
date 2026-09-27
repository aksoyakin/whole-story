package world.wholestory.processor.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.processor.sessionization.SessionizedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static world.wholestory.processor.persistence.MultiRowInsert.utc;
import static world.wholestory.processor.persistence.MultiRowInsert.values;

@Component
@RequiredArgsConstructor
class EventWriter {

    private static final String INSERT = """
            insert into analytics.events (
                event_id, timestamp, site_id, session_id, visitor_hash, name, hostname, pathname,
                referrer, referrer_source, utm_source, utm_medium, utm_campaign, utm_content, utm_term,
                country_code, subdivision_code, city_geoname_id,
                browser, browser_version, os, os_version, device_type, props)
            values %s
            on conflict (event_id, timestamp) do nothing
            returning event_id
            """;
    private static final String ROW = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)";

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    /** @return ids of events that were not stored before; replays return an empty set (D-018, D-032) */
    Set<UUID> insert(List<SessionizedEvent> events) {
        List<Object> params = new ArrayList<>(events.size() * 24);
        for (SessionizedEvent sessionized : events) {
            RawEventV1 e = sessionized.event();
            params.add(e.eventId());
            params.add(utc(e.timestamp()));
            params.add(e.siteId());
            params.add(sessionized.sessionId());
            params.add(e.visitorHash());
            params.add(e.name());
            params.add(e.hostname());
            params.add(e.pathname());
            params.add(e.referrer());
            params.add(null); // referrer_source: M2
            params.add(e.utmSource());
            params.add(e.utmMedium());
            params.add(e.utmCampaign());
            params.add(e.utmContent());
            params.add(e.utmTerm());
            params.add(e.countryCode());
            params.add(e.subdivisionCode());
            params.add(e.cityGeonameId());
            params.add(null); // browser: M2
            params.add(null); // browser_version: M2
            params.add(null); // os: M2
            params.add(null); // os_version: M2
            params.add(null); // device_type: M2
            params.add(e.props() == null ? null : jsonMapper.writeValueAsString(e.props()));
        }
        return new HashSet<>(jdbc.sql(INSERT.formatted(values(ROW, events.size())))
                .params(params)
                .query(UUID.class)
                .list());
    }
}
