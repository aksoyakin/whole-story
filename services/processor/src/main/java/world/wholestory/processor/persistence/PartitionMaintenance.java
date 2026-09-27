package world.wholestory.processor.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Keeps monthly partitions of events and sessions ahead of time (D-033). There is no DEFAULT partition,
 * so an insert into a missing month fails: partitions for the previous, current and next two months always exist.
 * Runs once on startup (after Flyway, before Kafka listeners start) and daily afterwards.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PartitionMaintenance implements InitializingBean {

    private static final String[] PARTITIONED_TABLES = {"events", "sessions"};
    private static final int MONTHS_AHEAD = 2;

    private final JdbcClient jdbc;
    private final Clock clock;

    @Override
    public void afterPropertiesSet() {
        ensurePartitions();
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    void ensurePartitions() {
        YearMonth current = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        for (YearMonth month = current.minusMonths(1); !month.isAfter(current.plusMonths(MONTHS_AHEAD)); month = month.plusMonths(1)) {
            for (String table : PARTITIONED_TABLES) {
                createPartition(table, month);
            }
        }
    }

    private void createPartition(String table, YearMonth month) {
        String partition = "%s_%d_%02d".formatted(table, month.getYear(), month.getMonthValue());
        jdbc.sql("""
                create table if not exists analytics.%s partition of analytics.%s
                for values from ('%s-01 00:00:00+00') to ('%s-01 00:00:00+00')
                """.formatted(partition, table, month, month.plusMonths(1)))
                .update();
        log.debug("Partition analytics.{} ensured", partition);
    }
}
