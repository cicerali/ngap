package tr.com.cicerali.aper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public class BitStreamWriter {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private byte currentByte = 0;
    private int currentBitIndex = 0; // 0 to 7
    private int totalBytesWritten = 0;

    /**
     * Write a single bit to the stream.
     */
    public void writeBit(boolean bit) {
        if (bit) {
            currentByte |= (1 << (7 - currentBitIndex));
        }
        currentBitIndex++;
        if (currentBitIndex == 8) {
            flushByte();
        }
    }

    /**
     * Write multiple bits from a long value.
     */
    public void writeBits(long value, int numBits) {
        if (numBits < 0 || numBits > 64) {
            throw new IllegalArgumentException("numBits must be between 0 and 64");
        }

        for (int i = numBits - 1; i >= 0; i--) {
            writeBit(((value >> i) & 1) == 1);
        }
    }

    private void flushByte() {
        buffer.write(currentByte);
        currentByte = 0;
        currentBitIndex = 0;
        totalBytesWritten++;
    }

    public void flush() {
        if (currentBitIndex > 0) {
            buffer.write(currentByte);
            currentByte = 0;
            currentBitIndex = 0;
            totalBytesWritten++;
        }
    }

    public byte[] toByteArray() {
        flush();
        return buffer.toByteArray();
    }

    /**
     * Write an unconstrained integer.
     */
    public void writeInteger(long value) {
        int len;
        if (value >= -128 && value <= 127) len = 1;
        else if (value >= -32768 && value <= 32767) len = 2;
        else if (value >= -8388608 && value <= 8388607) len = 3;
        else if (value >= -2147483648L && value <= 2147483647L) len = 4;
        else if (value >= -549755813888L && value <= 549755813887L) len = 5;
        else if (value >= -140737488355328L && value <= 140737488355327L) len = 6;
        else if (value >= -36028797018963968L && value <= 36028797018963967L) len = 7;
        else len = 8;

        writeLength(len);
        align();
        writeBits(value, len * 8);
    }

    /**
     * Write a constrained integer with min/max value range.
     */
    public void writeInteger(long value, long minValue, long maxValue) {
        writeInteger(value, minValue, maxValue, false);
    }

    public void writeInteger(long value, long minValue, long maxValue, boolean isExtensible) {
        if (isExtensible) {
            if (value >= minValue && value <= maxValue) {
                writeBit(false); // Not extended
            } else {
                writeBit(true); // Extended
                writeInteger(value); // Write as unconstrained
                return;
            }
        }

        if (minValue > maxValue) {
            throw new IllegalArgumentException("minValue cannot be greater than maxValue");
        }

        long range = maxValue - minValue;
        long delta = maxValue - minValue;

        if (range < 0) { // Large range (unsigned long > Long.MAX_VALUE)
             // Treat as large range
             // For now, assume it fits in 8 bytes max for length determinant logic
             // Or fallback to unconstrained-like logic but with offset
             // If delta < 0, it means range is huge.
             // Let's assume we use the "large range" logic below.
        }

        if (delta >= 0 && delta < 255) {
            int bitLen = getMinBitLength(delta);
            long encoded = value - minValue;
            writeBits(encoded, bitLen);
        } else if (delta == 255) {
            align();
            long encoded = value - minValue;
            writeBits(encoded, 8);
        } else if (delta >= 0 && delta <= 65535) {
            align();
            long encoded = value - minValue;
            writeBits(encoded, 16);
        } else {
            // Larger range
            int minBitLen = getMinBitLength(delta);
            int maxBytes = (minBitLen + 7) / 8;
            if (delta < 0) maxBytes = 8; // Full range
            
            // Calculate actual bytes needed for value
            long val = value - minValue;
            int len = 1;
            // Simple check for length
            if (val < 0) { // Should not happen if value >= minValue
                 // If val < 0, it means overflow or logic error.
                 // But wait, if value and minValue are both large positive, val is small positive.
                 // If value is large positive and minValue is large negative, val is huge positive (might overflow long).
                 // We assume standard long usage here.
            }
            
            // Determine length of 'val' in bytes
            if (val > 0xFFFFFFFFFFFFFFL) len = 8;
            else if (val > 0xFFFFFFFFFFFFL) len = 7;
            else if (val > 0xFFFFFFFFFFL) len = 6;
            else if (val > 0xFFFFFFFFL) len = 5;
            else if (val > 0xFFFFFF) len = 4;
            else if (val > 0xFFFF) len = 3;
            else if (val > 0xFF) len = 2;
            
            // Write length determinant (constrained)
            long lenRange = maxBytes - 1;
            int lenBits = getMinBitLength(lenRange);
            writeBits(len - 1, lenBits);
            
            align();
            writeBits(val, len * 8);
        }
    }

    /**
     * Writes a normally small non-negative whole number.
     * Used for extension additions in ENUMERATED and CHOICE types.
     */
    public void writeNormallySmallNonNegativeWholeNumber(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative");
        }
        if (value <= 63) {
            writeBit(false); // '0' bit indicates small number
            writeBits(value, 6);
        } else {
            writeBit(true); // '1' bit indicates large number
            writeInteger(value); // Encoded as semi-constrained integer (min=0)
        }
    }

    private int getMinBitLength(long range) {
        if (range < 0) return 64;
        if (range == 0) return 0;
        return 64 - Long.numberOfLeadingZeros(range);
    }

    /**
     * Write a double value in binary encoding format.
     */
    public void writeDouble(double value) {
        long bits = Double.doubleToLongBits(value);

        if (Double.isInfinite(value)) {
            writeLength(1);
            align();
            writeBits(value > 0 ? 0x40 : 0x41, 8);
            return;
        }

        long exponent = ((0x7ff0000000000000L & bits) >> 52) - 1023;
        long mantissa = 0x000fffffffffffffL & bits;
        mantissa |= 0x10000000000000L;

        while ((mantissa & 0xFF) == 0) {
            mantissa >>= 8;
            exponent += 8;
        }
        while ((mantissa & 1) == 0) {
            mantissa >>= 1;
            exponent++;
        }

        exponent -= 52;

        int expLen = 1;
        if (exponent < -128 || exponent > 127) expLen = 2;

        int mantLen = 0;
        long m = mantissa;
        while (m > 0) {
            mantLen++;
            m >>= 8;
        }

        int totalLen = 1 + expLen + mantLen;
        writeLength(totalLen);
        align();

        int header = 0x80;
        if (value < 0) header |= 0x40;
        header |= (expLen - 1);
        writeBits(header, 8);

        writeBits(exponent, expLen * 8);
        writeBits(mantissa, mantLen * 8);
    }

    /**
     * Write a boolean value.
     */
    public void writeBoolean(boolean value) {
        writeBit(value);
    }

    /**
     * Write an unconstrained string.
     */
    public void writeString(String value) {
        writeString(value, null, null, false);
    }

    /**
     * Write a string with SIZE constraint.
     */
    public void writeString(String value, Integer minSize, Integer maxSize) {
        writeString(value, minSize, maxSize, false);
    }

    /**
     * Write a string with optional extensibility and SIZE constraint.
     */
    public void writeString(String value, Integer minSize, Integer maxSize, boolean isExtensible) {
        byte[] bytes = value.getBytes();
        int length = bytes.length;

        if (isExtensible) {
            if (minSize != null && maxSize != null && length >= minSize && length <= maxSize) {
                writeBit(false); // Not extended
            } else {
                writeBit(true); // Extended
                // Write length as semi-constrained (or unconstrained for simplicity here)
                // Assuming unconstrained for now as fallback
                writeLength(length);
                align();
                for (byte b : bytes) writeBits(b, 8);
                return;
            }
        }

        if (minSize == null || maxSize == null) {
            writeLength(length);
            align();
            for (byte b : bytes) writeBits(b, 8);
            return;
        }

        if (!minSize.equals(maxSize)) {
            long range = (long) maxSize - minSize;
            if (maxSize < 65536) {
                if (range < 255) {
                    int bitLen = getMinBitLength(range);
                    writeBits((long) length - minSize, bitLen);
                } else if (range == 255) {
                    align();
                    writeBits((long) length - minSize, 8);
                } else {
                    align();
                    writeBits((long) length - minSize, 16);
                }
            } else {
                writeLength(length);
            }
        }

        align();
        for (byte b : bytes) writeBits(b, 8);
    }

    /**
     * Write an unconstrained UTF8String.
     */
    public void writeUTF8String(String value) {
        writeUTF8String(value, null, null, false);
    }

    /**
     * Write a UTF8String with SIZE constraint.
     */
    public void writeUTF8String(String value, Integer minSize, Integer maxSize) {
        writeUTF8String(value, minSize, maxSize, false);
    }

    /**
     * Write a UTF8String with optional extensibility and SIZE constraint.
     */
    public void writeUTF8String(String value, Integer minSize, Integer maxSize, boolean isExtensible) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;

        if (isExtensible) {
            if (minSize != null && maxSize != null && length >= minSize && length <= maxSize) {
                writeBit(false); // Not extended
            } else {
                writeBit(true); // Extended
                writeLength(length);
                align();
                for (byte b : bytes) writeBits(b, 8);
                return;
            }
        }

        if (minSize == null || maxSize == null) {
            writeLength(length);
            align();
            for (byte b : bytes) writeBits(b, 8);
            return;
        }

        if (!minSize.equals(maxSize)) {
            long range = (long) maxSize - minSize;
            if (maxSize < 65536) {
                if (range < 255) {
                    int bitLen = getMinBitLength(range);
                    writeBits((long) length - minSize, bitLen);
                } else if (range == 255) {
                    align();
                    writeBits((long) length - minSize, 8);
                } else {
                    align();
                    writeBits((long) length - minSize, 16);
                }
            } else {
                writeLength(length);
            }
        }

        align();
        for (byte b : bytes) writeBits(b, 8);
    }

    /**
     * Write a BitSet (bit string).
     */
    public void writeBitString(BitSet bitSet) {
        writeBitString(bitSet, bitSet.length(), null, null, false);
    }

    /**
     * Write a BitSet with SIZE constraint.
     */
    public void writeBitString(BitSet bitSet, Integer minSize, Integer maxSize) {
        writeBitString(bitSet, bitSet.length(), minSize, maxSize, false);
    }

    /**
     * Write a BitSet with SIZE constraint.
     */
    public void writeBitString(BitSet bitSet, Integer minSize, Integer maxSize, boolean isExtensible) {
        writeBitString(bitSet, bitSet.length(), minSize, maxSize, isExtensible);
    }

    public void writeBitString(BitSet bitSet, int length, Integer minSize, Integer maxSize, boolean isExtensible) {
        Objects.requireNonNull(bitSet, "bitSet cannot be null");

        if (minSize != null && length < minSize) {
            length = minSize;
        }

        if (isExtensible) {
            if (minSize != null && maxSize != null && length >= minSize && length <= maxSize) {
                writeBit(false); // Not extended
            } else {
                writeBit(true); // Extended
                // Write length as semi-constrained (or unconstrained for simplicity here)
                writeLength(length);
                align();
                for (int i = length - 1; i >= 0; i--) writeBit(bitSet.get(i));
                return;
            }
        }

        if (minSize == null || maxSize == null) {
            writeLength(length);
            align();
            for (int i = length - 1; i >= 0; i--) writeBit(bitSet.get(i));
            return;
        }

        if (minSize.equals(maxSize)) {
            if (minSize > 16) align();
            for (int i = minSize - 1; i >= 0; i--) writeBit(bitSet.get(i));
        } else {
            long range = (long) maxSize - minSize;
            if (maxSize < 65536) {
                if (range < 255) {
                    int bitLen = getMinBitLength(range);
                    writeBits((long) length - minSize, bitLen);
                } else if (range == 255) {
                    align();
                    writeBits((long) length - minSize, 8);
                } else {
                    align();
                    writeBits((long) length - minSize, 16);
                }
            } else {
                writeLength(length);
            }

            // For variable length bit string, value is octet-aligned
            align();
            for (int i = length - 1; i >= 0; i--) writeBit(bitSet.get(i));
        }
    }

    /**
     * Write an unconstrained octet string (hex string).
     */
    public void writeOctetString(String hexString) {
        writeOctetString(hexString, null, null, false);
    }

    /**
     * Write an octet string with SIZE constraint.
     */
    public void writeOctetString(String hexString, Integer minSize, Integer maxSize) {
        writeOctetString(hexString, minSize, maxSize, false);
    }

    public void writeOctetString(String hexString, Integer minSize, Integer maxSize, boolean isExtensible) {
        Objects.requireNonNull(hexString, "hexString cannot be null");

        // Convert hex string to byte array
        byte[] data = HexFormat.of().parseHex(hexString);

        int length = data.length;

        if (isExtensible) {
            if (minSize != null && maxSize != null && length >= minSize && length <= maxSize) {
                writeBit(false); // Not extended
            } else {
                writeBit(true); // Extended
                // Write length as semi-constrained (or unconstrained for simplicity here)
                writeLength(length);
                align();
                for (byte b : data) writeBits(b, 8);
                return;
            }
        }

        if (minSize == null || maxSize == null) {
            writeLength(length);
            align();
            for (byte b : data) writeBits(b, 8);
            return;
        }

        if (!minSize.equals(maxSize)) {
            long range = (long) maxSize - minSize;
            if (maxSize < 65536) {
                if (range < 255) {
                    int bitLen = getMinBitLength(range);
                    writeBits((long) length - minSize, bitLen);
                } else if (range == 255) {
                    align();
                    writeBits((long) length - minSize, 8);
                } else {
                    align();
                    writeBits((long) length - minSize, 16);
                }
            } else {
                writeLength(length);
            }
            align(); // Variable size is octet aligned
        } else {
            // Fixed size
            if (minSize > 2) {
                align();
            }
        }

        for (byte b : data) writeBits(b, 8);
    }

    public void writeObjectIdentifier(String oid) {
        String[] parts = oid.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid OID: " + oid);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // First byte
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        baos.write((byte)(first * 40 + second));

        for (int i = 2; i < parts.length; i++) {
            long val = Long.parseLong(parts[i]);
            writeBase128(baos, val);
        }

        byte[] bytes = baos.toByteArray();
        writeLength(bytes.length);
        align();
        for (byte b : bytes) writeBits(b, 8);
    }

    private void writeBase128(ByteArrayOutputStream baos, long val) {
        if (val == 0) {
            baos.write(0);
            return;
        }

        // Calculate number of bytes
        int len = 0;
        long v = val;
        while (v > 0) {
            len++;
            v >>= 7;
        }

        for (int i = len - 1; i >= 0; i--) {
            int b = (int)((val >> (i * 7)) & 0x7F);
            if (i > 0) b |= 0x80;
            baos.write(b);
        }
    }

    /**
     * Write a sequence length with SIZE constraint.
     */
    public void writeSequenceOf(List<?> list, Integer minSize, Integer maxSize) {
        Objects.requireNonNull(list, "list cannot be null");

        int length = list.size();
        if (minSize == null || maxSize == null) {
            writeLength(length);
            align();
            return;
        }

        if (!minSize.equals(maxSize)) {
            long range = (long) maxSize - minSize;
            if (maxSize < 65536) {
                if (range < 255) {
                    int bitLen = getMinBitLength(range);
                    writeBits((long) length - minSize, bitLen);
                } else if (range == 255) {
                    align();
                    writeBits((long) length - minSize, 8);
                } else {
                    align();
                    writeBits((long) length - minSize, 16);
                }
            } else {
                writeLength(length);
            }
        }
    }

    /**
     * Write length determinant for unconstrained values.
     */
    public void writeLength(int length) {
        align();
        if (length < 128) {
            writeBits(length, 8);
        } else if (length < 16384) {
            writeBits(0x8000 | length, 16);
        } else {
            throw new UnsupportedOperationException("Large lengths (>= 16k) not supported yet");
        }
    }

    /**
     * Align to next byte boundary if not already aligned.
     */
    public void align() {
        if (currentBitIndex > 0) {
            flushByte();
        }
    }
}
