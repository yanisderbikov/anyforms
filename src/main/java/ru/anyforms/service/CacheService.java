package ru.anyforms.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CacheService {
    private final Cache<Long, Boolean> leadCache;

    public CacheService() {
        this.leadCache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build();
    }

    public boolean containsLead(Long leadId) {
        return leadCache.getIfPresent(leadId) != null;
    }

    public void addLead(Long leadId) {
        leadCache.put(leadId, true);
    }
}



