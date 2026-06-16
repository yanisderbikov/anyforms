package ru.anyforms.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CacheService {
    private final Cache<Long, Boolean> leadCache;
    private final Cache<Long, Boolean> syncOrderLeadCache;

    public CacheService() {
        this.leadCache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build();
        this.syncOrderLeadCache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    public boolean containsLead(Long leadId) {
        return leadCache.getIfPresent(leadId) != null;
    }

    public void addLead(Long leadId) {
        leadCache.put(leadId, true);
    }

    public boolean containsSyncOrderLead(Long leadId) {
        return syncOrderLeadCache.getIfPresent(leadId) != null;
    }

    public void addSyncOrderLead(Long leadId) {
        syncOrderLeadCache.put(leadId, true);
    }
}



