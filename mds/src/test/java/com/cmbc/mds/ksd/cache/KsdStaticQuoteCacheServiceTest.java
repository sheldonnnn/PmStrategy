package com.cmbc.mds.ksd.cache;

import com.cmbc.mds.forex.quotes.dto.DimpleKsdQuoteEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KsdStaticQuoteCacheServiceTest {

    private final KsdStaticQuoteCacheService service = new KsdStaticQuoteCacheService();

    @Test
    @DisplayName("KSD-STATIC-CACHE-01 Dimple static prices are truncated to 4 decimal places")
    void truncatesStaticPricesToFourDecimalPlaces() {
        DimpleKsdQuoteEvent event = new DimpleKsdQuoteEvent();
        event.setSymbol("USD/CNY");
        event.setOpenPrice(7.12349);
        event.setPreClosePrice(7.23459);
        event.setUpperLimitPrice(7.34569);
        event.setLowerLimitPrice(7.45679);

        service.updateFromQuoteEvent(event);

        KsdStaticQuoteInfo info = service.getByInstrumentId("USD/CNY");
        assertThat(info).isNotNull();
        assertThat(info.getOpenPrice()).isEqualTo(new BigDecimal("7.1234"));
        assertThat(info.getPreClosePrice()).isEqualTo(new BigDecimal("7.2345"));
        assertThat(info.getUpperLimitPrice()).isEqualTo(new BigDecimal("7.3456"));
        assertThat(info.getLowerLimitPrice()).isEqualTo(new BigDecimal("7.4567"));
    }
}
