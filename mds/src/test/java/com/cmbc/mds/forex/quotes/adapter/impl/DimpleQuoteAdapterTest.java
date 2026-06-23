package com.cmbc.mds.forex.quotes.adapter.impl;

import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.DimpleKsdQuoteEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DimpleQuoteAdapterTest {

    private final TestableDimpleQuoteAdapter adapter = new TestableDimpleQuoteAdapter();

    @Test
    @DisplayName("DIMPLE-ADAPTER-01 prices are truncated to 4 decimal places")
    void truncatesPricesToFourDecimalPlaces() {
        DimpleKsdQuoteEvent payload = new DimpleKsdQuoteEvent();
        payload.setKsdSeqNo(1L);
        payload.setSymbol("USD/CNY");
        payload.setBidPrices(List.of(7.12349));
        payload.setBidVolumes(List.of(1000000));
        payload.setAskPrices(List.of(7.23459));
        payload.setAskVolumes(List.of(2000000));

        Depth depth = adapter.convert(payload, "DIMPLE", "DIMPLE");

        assertThat(depth).isNotNull();
        assertThat(depth.getBidPrices()).containsExactly(new BigDecimal("7.1234"));
        assertThat(depth.getAskPrices()).containsExactly(new BigDecimal("7.2345"));
        assertThat(depth.getBidQuantities()).containsExactly(new BigDecimal("1000000"));
        assertThat(depth.getAskQuantities()).containsExactly(new BigDecimal("2000000"));
    }

    @Test
    @DisplayName("DIMPLE-ADAPTER-02 ten-level all-zero quote is converted to empty depth")
    void allowsTenLevelAllZeroQuoteAsEmptyDepth() {
        DimpleKsdQuoteEvent payload = new DimpleKsdQuoteEvent();
        payload.setKsdSeqNo(2L);
        payload.setSymbol("USD/CNY");
        payload.setBidPrices(Collections.nCopies(10, 0.0D));
        payload.setBidVolumes(Collections.nCopies(10, 0));
        payload.setAskPrices(Collections.nCopies(10, 0.0D));
        payload.setAskVolumes(Collections.nCopies(10, 0));

        Depth depth = adapter.convert(payload, "DIMPLE", "DIMPLE");

        assertThat(depth).isNotNull();
        assertThat(depth.getBidPrices()).isEmpty();
        assertThat(depth.getBidQuantities()).isEmpty();
        assertThat(depth.getAskPrices()).isEmpty();
        assertThat(depth.getAskQuantities()).isEmpty();
    }

    @Test
    @DisplayName("DIMPLE-ADAPTER-03 invalid empty converted quote is filtered")
    void filtersInvalidQuoteConvertedToEmptyDepth() {
        DimpleKsdQuoteEvent payload = new DimpleKsdQuoteEvent();
        payload.setKsdSeqNo(3L);
        payload.setSymbol("USD/CNY");
        payload.setBidPrices(List.of(7.1234D));
        payload.setBidVolumes(List.of(0));
        payload.setAskPrices(List.of(7.2345D));
        payload.setAskVolumes(List.of(0));

        Depth depth = adapter.convert(payload, "DIMPLE", "DIMPLE");

        assertThat(depth).isNull();
    }

    private static class TestableDimpleQuoteAdapter extends DimpleQuoteAdapter {
        private Depth convert(DimpleKsdQuoteEvent payload, String source, String provider) {
            return super.convertToDepth(payload, source, provider);
        }
    }
}
