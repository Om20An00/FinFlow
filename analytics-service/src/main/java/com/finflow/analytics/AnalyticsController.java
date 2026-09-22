package com.finflow.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    public record DayPoint(String date, BigDecimal volume, long count) {
    }

    public record TopSender(String name, BigDecimal volume) {
    }

    public record Summary(long created, long completed, long failed, BigDecimal volume, BigDecimal averageTicket,
                          double successRatePercent, List<DayPoint> last7Days, List<TopSender> topSenders) {
    }

    private final StringRedisTemplate redis;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MERCHANT')")
    public Summary summary() {
        long created = num("analytics:created");
        long completed = num("analytics:completed");
        long failed = num("analytics:failed");
        BigDecimal volume = cents(num("analytics:volume_cents"));
        BigDecimal avg = completed == 0 ? BigDecimal.ZERO : volume.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP);
        long settled = completed + failed;
        double rate = settled == 0 ? 100.0 : Math.round(completed * 1000.0 / settled) / 10.0;

        List<DayPoint> days = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 6; i >= 0; i--) {
            String d = today.minusDays(i).toString();
            days.add(new DayPoint(d, cents(num("analytics:daily:volume:" + d)), num("analytics:daily:count:" + d)));
        }

        List<TopSender> top = new ArrayList<>();
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores("analytics:top_senders", 0, 4);
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                top.add(new TopSender(t.getValue(), BigDecimal.valueOf(t.getScore() == null ? 0 : t.getScore()).setScale(2, RoundingMode.HALF_UP)));
            }
        }
        return new Summary(created, completed, failed, volume, avg, rate, days, top);
    }

    private long num(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? 0L : Long.parseLong(v);
    }

    private static BigDecimal cents(long c) {
        return BigDecimal.valueOf(c, 2);
    }
}
