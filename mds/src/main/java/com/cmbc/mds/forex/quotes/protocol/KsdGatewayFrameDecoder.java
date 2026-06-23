package com.cmbc.mds.forex.quotes.protocol;

import com.cmbc.mds.forex.quotes.dto.DimpleKsdQuoteEvent;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class KsdGatewayFrameDecoder {

    private KsdGatewayFrameDecoder() {
    }

    public static DecodedFrame decode(byte[] bytes) {
        ProtoReader reader = new ProtoReader(bytes);
        DecodedFrame frame = new DecodedFrame();
        while (reader.hasRemaining()) {
            int tag = (int) reader.readVarint();
            int field = tag >>> 3;
            int wireType = tag & 7;
            switch (field) {
                case 1 -> {
                    frame.rawType = (int) reader.readVarint();
                    frame.type = KsdGatewayFrameType.fromCode(frame.rawType);
                }
                case 2 -> frame.gatewayId = reader.readString();
                case 3 -> frame.gatewaySeqNo = reader.readVarint();
                case 4 -> frame.sentAt = reader.readVarint();
                case 10 -> frame.quote = decodeQuote(reader.readBytes());
                default -> reader.skip(wireType);
            }
        }
        return frame;
    }

    private static DimpleKsdQuoteEvent decodeQuote(byte[] bytes) {
        ProtoReader reader = new ProtoReader(bytes);
        DimpleKsdQuoteEvent quote = new DimpleKsdQuoteEvent();
        while (reader.hasRemaining()) {
            int tag = (int) reader.readVarint();
            int field = tag >>> 3;
            int wireType = tag & 7;
            switch (field) {
                case 1 -> quote.setSource(reader.readString());
                case 2 -> quote.setProvider(reader.readString());
                case 3 -> quote.setKsdSeqNo(reader.readVarint());
                case 4 -> quote.setLast(reader.readVarint() != 0);
                case 5 -> quote.setSymbol(reader.readString());
                case 6 -> quote.setTradingDay(reader.readString());
                case 7 -> quote.setUpdateTime(reader.readString());
                case 8 -> quote.setOpenPrice(reader.readDouble());
                case 9 -> quote.setPreClosePrice(reader.readDouble());
                case 10 -> quote.setUpperLimitPrice(reader.readDouble());
                case 11 -> quote.setLowerLimitPrice(reader.readDouble());
                case 12 -> quote.getBidPrices().add(reader.readDouble());
                case 13 -> quote.getBidVolumes().add((int) reader.readVarint());
                case 14 -> quote.getAskPrices().add(reader.readDouble());
                case 15 -> quote.getAskVolumes().add((int) reader.readVarint());
                case 16 -> quote.setGatewayReceiveTime(reader.readVarint());
                default -> reader.skip(wireType);
            }
        }
        return quote;
    }

    public static class DecodedFrame {
        private KsdGatewayFrameType type;
        private int rawType;
        private String gatewayId;
        private long gatewaySeqNo;
        private long sentAt;
        private DimpleKsdQuoteEvent quote;

        public KsdGatewayFrameType getType() {
            return type;
        }

        public int getRawType() {
            return rawType;
        }

        public String getGatewayId() {
            return gatewayId;
        }

        public long getGatewaySeqNo() {
            return gatewaySeqNo;
        }

        public long getSentAt() {
            return sentAt;
        }

        public DimpleKsdQuoteEvent getQuote() {
            return quote;
        }
    }

    static final class ProtoReader {
        private final byte[] data;
        private int index;

        ProtoReader(byte[] data) {
            this.data = data;
        }

        boolean hasRemaining() {
            return index < data.length;
        }

        long readVarint() {
            long result = 0;
            int shift = 0;
            while (index < data.length) {
                int b = data[index++] & 0xff;
                result |= (long) (b & 0x7f) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
            throw new IllegalArgumentException("Invalid protobuf varint");
        }

        double readDouble() {
            if (index + 8 > data.length) {
                throw new IllegalArgumentException("Invalid protobuf double");
            }
            long bits = 0;
            for (int i = 0; i < 8; i++) {
                bits |= (long) (data[index++] & 0xff) << (8 * i);
            }
            return Double.longBitsToDouble(bits);
        }

        String readString() {
            return new String(readBytes(), StandardCharsets.UTF_8);
        }

        byte[] readBytes() {
            int len = (int) readVarint();
            if (len < 0 || index + len > data.length) {
                throw new IllegalArgumentException("Invalid protobuf bytes length");
            }
            byte[] value = Arrays.copyOfRange(data, index, index + len);
            index += len;
            return value;
        }

        void skip(int wireType) {
            switch (wireType) {
                case 0 -> readVarint();
                case 1 -> index += 8;
                case 2 -> {
                    int len = (int) readVarint();
                    index += len;
                }
                case 5 -> index += 4;
                default -> throw new IllegalArgumentException("Unsupported protobuf wire type: " + wireType);
            }
            if (index > data.length) {
                throw new IllegalArgumentException("Invalid protobuf skip");
            }
        }
    }
}
