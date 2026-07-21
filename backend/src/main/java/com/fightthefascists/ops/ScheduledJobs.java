package com.fightthefascists.ops;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ScheduledJobs {
    private final DatabaseClient db;

    public ScheduledJobs(DatabaseClient db) {
        this.db = db;
    }

    @Scheduled(fixedRate = 60_000)
    public void expireNeeds() {
        db.sql("""
                UPDATE needs SET state = 'EXPIRED', resolved_at = now()
                WHERE state IN ('OPEN','CLAIMED') AND expires_at < now()
                """)
                .fetch().rowsUpdated()
                .onErrorComplete()
                .subscribe();
    }

    @Scheduled(fixedRate = 60_000)
    public void lapseClaims() {
        db.sql("""
                UPDATE claims SET state = 'LAPSED', resolved_at = now()
                WHERE state = 'ACTIVE' AND lapse_at < now()
                """)
                .fetch().rowsUpdated()
                .onErrorComplete()
                .subscribe();
    }

    @Scheduled(fixedRate = 900_000)
    public void staleAidStatus() {
        // F5.E2
        db.sql("UPDATE aid_points SET status = 'UNKNOWN' WHERE status_at < now() - interval '4 hours' AND status != 'UNKNOWN'")
                .fetch().rowsUpdated().onErrorComplete().subscribe();
    }
}
