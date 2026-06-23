package com.cmbc.mds.ksd.controller;

import com.cmbc.mds.ksd.cache.KsdStaticQuoteCacheService;
import com.cmbc.mds.ksd.cache.KsdStaticQuoteInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ksd/cache/static")
public class KsdStaticQuoteController {

    @Autowired
    private KsdStaticQuoteCacheService ksdStaticQuoteCacheService;

    @GetMapping("/{instrumentId}")
    public ResponseEntity<KsdStaticQuoteInfo> getByInstrumentId(@PathVariable String instrumentId) {
        KsdStaticQuoteInfo info = ksdStaticQuoteCacheService.getByInstrumentId(instrumentId);
        if (info != null) {
            return ResponseEntity.ok(info);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/all")
    public ResponseEntity<Map<String, KsdStaticQuoteInfo>> getAllSnapshot() {
        return ResponseEntity.ok(ksdStaticQuoteCacheService.getAllSnapshot());
    }

    @DeleteMapping
    public ResponseEntity<String> clearCache() {
        ksdStaticQuoteCacheService.clearCache();
        return ResponseEntity.ok("Cache cleared successfully");
    }
}
