package com.ntg.CitizenLink.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtBlocklist {

    private final ConcurrentHashMap<String, Date> blocklist = new ConcurrentHashMap<>();

    public void block(String jti, Date expiry) {
        blocklist.put(jti, expiry);
    }

    public boolean isBlocked(String jti) {
        Date expiry = blocklist.get(jti);
        if (expiry == null) return false;
        if (expiry.before(new Date())) {
            blocklist.remove(jti);
            return false;
        }
        return true;
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanup() {
        Date now = new Date();
        blocklist.entrySet().removeIf(entry -> entry.getValue().before(now));
    }
}
