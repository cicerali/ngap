package tr.com.cicerali.aper;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.Objects;

public class BitStreamReader {
    private final byte[] data;
    private int byteIndex = 0;
    private int bitIndex = 0; // 0 to 7
    private int lastReadBitStringLength = 0;

    public BitStreamReader(byte[] data) {
        this.data = Objects.requireNonNull(data, "data cannot be null");
    }

    public int getLastReadBitStringLength() {
        return lastReadBitStringLength;
    }

    public boolean readBit() {
        if (byteIndex >= data.length) {
            throw new IndexOutOfBoundsException("End of stream");
        }
        boolean bit = ((data[byteIndex] >> (7 - bitIndex)) & 1) == 1;
        // System.out.println("Read bit: " + (bit ? 1 : 0) + " at " + byteIndex + ":" + bitIndex);
        bitIndex++;
        if (bitIndex == 8) {
            bitIndex = 0;
            byteIndex++;
        }
        return bit;
    }

    public long readBits(int numBits) {
        if (numBits < 0 || numBits > 64) {
            throw new IllegalArgumentException("numBits must be between 0 and 64");
        }

        long value = 0;
        for (int i = 0; i < numBits; i++) {
            value = (value << 1) | (readBit() ? 1 : 0);
        }
        // System.out.println("Read bits (" + numBits + "): " + value);
        return value;
    }

    /**
     * Read an unconstrained integer.
     */
    public long readInteger() {
        int len = readLength();
        align();
        long val = readBits(len * 8);
        // Sign extension if needed (assuming 64-bit long return)
        if (len < 8) {
            int shift = 64 - (len * 8);
            return (val << shift) >> shift;
        }
        return val;
    }

    /**
     * Read constrained integer with min/max value range
     */
    public long readInteger(Long minValue, Long maxValue) {
        return readInteger(minValue, maxValue, false);
    }

    public long readInteger(Long minValue, Long maxValue, boolean isExtensible) {
        if (isExtensible) {
            if (readBit()) {
                // Extended value
                return readInteger();
            }
        }

        // If constraints are null, read unconstrained
        if (minValue == null || maxValue == null) {
            return readInteger();
        }

        if (minValue > maxValue) {
            throw new IllegalArgumentException("minValue cannot be greater than maxValue");
        }

        long delta = maxValue - minValue;

        if (delta < 0) { // Overflow (unsigned long range > Long.MAX_VALUE)
             // Fallback to large integer handling if needed, but for now assume it fits in long logic or treat as large range
             // If delta is negative, it means the range is larger than Long.MAX_VALUE (e.g. 0 to MAX_LONG is fine, but MIN_LONG to MAX_LONG is -1 in signed arithmetic if not careful)
             // But here delta is calculated as signed long.
             // If range exceeds 64-bit signed, we have an issue. But Java Long is 64-bit signed.
             // ASN.1 INTEGER can be larger.
             // For this task, we assume it fits in Java Long (64-bit).
             // If delta < 0, it might be because of overflow in calculation?
             // No, if min=-10 and max=10, delta=20.
             // If min=MIN_VALUE and max=MAX_VALUE, delta = -1.
             // We should use unsigned arithmetic or BigInteger for full correctness, but let's stick to Long for now as requested.
             // If delta < 0, treat as "large range".
        }

        if (delta >= 0 && delta < 255) {
            // Read bit-field of minimum size
            int bitLen = getMinBitLength(delta);
            long encoded = readBits(bitLen);
            return encoded + minValue;
        } else if (delta == 255) {
            align();
            long encoded = readBits(8);
            return encoded + minValue;
        } else if (delta >= 0 && delta <= 65535) {
            align();
            long encoded = readBits(16);
            return encoded + minValue;
        } else {
            int minBitLen = getMinBitLength(delta); // This might be wrong if delta < 0
            int maxBytes = (minBitLen + 7) / 8;
            if (delta < 0) maxBytes = 8; // Full range

            long lenRange = maxBytes - 1;
            int lenBits = getMinBitLength(lenRange);

            long len = readBits(lenBits) + 1;

            align();
            long val = readBits((int) len * 8);
            return val + minValue;
        }
    }

    /**
     * Reads a normally small non-negative whole number.
     * Used for extension additions in ENUMERATED and CHOICE types.
     */
    public long readNormallySmallNonNegativeWholeNumber() {
        boolean isLarge = readBit();
        if (!isLarge) {
            // '0' bit indicates small number (<= 63)
            return readBits(6);
        } else {
            // '1' bit indicates large number, encoded as semi-constrained integer (min=0)
            // Since we don't have a direct semi-constrained read method that takes just min,
            // we can use readInteger() which handles unconstrained, or implement semi-constrained logic.
            // For normally small numbers, the large form is usually an unconstrained integer (length determinant + value).
            // But strictly it's semi-constrained (0..MAX).
            // Let's assume it's encoded as a length-prefixed integer (unconstrained-like).
            return readInteger();
        }
    }

    private int getMinBitLength(long range) {
        if (range < 0) return 64; // Full range
        if (range == 0) return 0;
        return 64 - Long.numberOfLeadingZeros(range);
    }

    public double readDouble() {
        int len = readLength();
        align();

        if (len == 0) return 0.0;

        // Read header byte
        int header = (int) readBits(8);

        // Check for special values
        if (header == 0x40) return Double.POSITIVE_INFINITY;
        if (header == 0x41) return Double.NEGATIVE_INFINITY;

        if ((header & 0x80) == 0x80) {
            // Binary encoding
            boolean negative = (header & 0x40) != 0;
            // Base is bits 6-5 (00=2, 01=8, 10=16). Assuming base 2 for now as per writer.
            // Scale is bits 4-3. Assuming 0.
            int exponentLen = (header & 0x03) + 1;

            long exponent = readBits(exponentLen * 8);
            // Sign extend exponent
            int shift = 64 - (exponentLen * 8);
            exponent = (exponent << shift) >> shift;

            int mantissaLen = len - 1 - exponentLen;
            long mantissa = readBits(mantissaLen * 8);

            // Reconstruct double: Value = M * 2^E
            double val = mantissa * Math.pow(2, exponent);
            return negative ? -val : val;
        }

        // Fallback for unknown encoding or Decimal encoding (not implemented)
        return 0.0;
    }

    /**
     * Read a boolean value.
     */
    public boolean readBoolean() {
        return readBit();
    }

    /**
     * Read an unconstrained string.
     */
    public String readString() {
        return readString(null, null, false);
    }

    /**
     * Read string with SIZE constraint
     */
    public String readString(Integer minSize, Integer maxSize) {
        return readString(minSize, maxSize, false);
    }

    /**
     * Read a string with optional extensibility and SIZE constraint.
     */
    public String readString(Integer minSize, Integer maxSize, boolean isExtensible) {
        if (isExtensible && readBit()) {
            // Extended encoding: read as unconstrained
            int len = readLength();
            align();
            byte[] bytes = readBytes(len);
            return new String(bytes);
        }

        int len;

        // If constraints are null, read unconstrained
        if (minSize == null || maxSize == null) {
            len = readLength();
        } else if (minSize.equals(maxSize)) {
            // Fixed size, no length determinant
            len = minSize;
        } else {
            // Variable size within range
            long range = (long) maxSize - minSize;
            if (maxSize < 65536) {
                if (range < 255) {
                    int bitLen = getMinBitLength(range);
                    len = (int) readBits(bitLen) + minSize;
                } else if (range == 255) {
                    align();
                    len = (int) readBits(8) + minSize;
                } else {
                    align();
                    len = (int) readBits(16) + minSize;
                }
            } else {
                len = readLength();
            }
        }

        align(); // Strings are octet-aligned in ALIGNED variant

        byte[] bytes = readBytes(len);
        return new String(bytes);
    }

    /**
     * Read an unconstrained UTF8String.
     */
    public String readUTF8String() {
        return readUTF8String(null, null, false);
    }

    /**
     * Read UTF8String with SIZE constraint
     */
    public String readUTF8String(Integer minSize, Integer maxSize) {
        return readUTF8String(minSize, maxSize, false);
    }

    /**
     * Read a UTF8String with optional extensibility and SIZE constraint.
     */
    public String readUTF8String(Integer minSize, Integer maxSize, boolean isExtensible) {
        if (isExtensible && readBit()) {
            // Extended encoding: read as unconstrained
            int len = readLength();
            align();
            byte[] bytes = readBytes(len);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        int len;

        // If constraints are null, read unconstrained
        if (minSize == null || maxSize == null) {
            len = readLength();
        } else if (minSize.equals(maxSize)) {
            // Fixed size, no length determinant
            len = minSize;
        } else {
            // Variable size within range
            long range = (long) maxSize - minSize;
            if (maxSize < 65536) {
                if (range < 255) {
                    int bitLen = getMinBitLength(range);
                    len = (int) readBits(bitLen) + minSize;
                } else if (range == 255) {
                    align();
                    len = (int) readBits(8) + minSize;
                } else {
                    align();
                    len = (int) readBits(16) + minSize;
                }
            } else {
                len = readLength();
            }
        }

        align(); // Strings are octet-aligned in ALIGNED variant

        byte[] bytes = readBytes(len);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Read a BitSet (bit string).
     */
    public BitSet readBitString() {
        return readBitString(null, null);
    }

    /**
     * Read a BitSet with SIZE constraint.
     */
    public BitSet readBitString(Integer minSize, Integer maxSize) {
        return readBitString(minSize, maxSize, false);
    }

    public BitSet readBitString(Integer minSize, Integer maxSize, boolean isExtensible) {
        int length;
        boolean align = true;

        if (isExtensible && readBit()) {
             // Extended
             length = readLength();
             align();
             lastReadBitStringLength = length;
             BitSet bitSet = new BitSet(length);
             for (int i = length - 1; i >= 0; i--) {
                 if (readBit()) {
                     bitSet.set(i);
                 }
             }
             return bitSet;
        }

        if (minSize != null && minSize.equals(maxSize)) {
            length = minSize;
            if (length <= 16) align = false;
        } else if (minSize != null && maxSize != null) {
            long range = (long) maxSize - minSize;
            if (maxSize < 65536) {
                if (range < 255) {
                    int bitLen = getMinBitLength(range);
                    length = (int) readBits(bitLen) + minSize;
                } else if (range == 255) {
                    align();
                    length = (int) readBits(8) + minSize;
                } else {
                    align();
                    length = (int) readBits(16) + minSize;
                }
            } else {
                length = readLength();
            }
        } else {
            length = readLength();
        }

        if (align) align();

        lastReadBitStringLength = length;
        BitSet bitSet = new BitSet(length);
        for (int i = length - 1; i >= 0; i--) {
            if (readBit()) {
                bitSet.set(i);
            }
        }
        return bitSet;
    }

    /**
     * Read an unconstrained octet string (byte array as hex string).
     */
    public String readOctetString() {
        return readOctetString(null, null);
    }

    /**
     * Read an octet string with SIZE constraint.
     */
    public String readOctetString(Integer minSize, Integer maxSize) {
        return readOctetString(minSize, maxSize, false);
    }

    public String readOctetString(Integer minSize, Integer maxSize, boolean isExtensible) {
        if (isExtensible && readBit()) {
             // Extended
             int len = readLength();
             align();
             byte[] bytes = readBytes(len);
             return HexFormat.of().withUpperCase().formatHex(bytes);
        }

        int len;
        boolean align = true;

        // If constraints are null, read unconstrained
        if (minSize == null || maxSize == null) {
            len = readLength();
        } else if (minSize.equals(maxSize)) {
            // Fixed size, no length determinant
            len = minSize;
            if (len <= 2) align = false;
        } else {
            // Variable size within range
            long range = (long) maxSize - minSize;
            if (maxSize < 65536) {
                if (range < 255) {
                    int bitLen = getMinBitLength(range);
                    len = (int) readBits(bitLen) + minSize;
                    // Variable size value is octet aligned
                } else if (range == 255) {
                    align();
                    len = (int) readBits(8) + minSize;
                } else {
                    align();
                    len = (int) readBits(16) + minSize;
                }
            } else {
                len = readLength();
            }
        }

        if (align) align();

        byte[] bytes = readBytes(len);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    public String readObjectIdentifier() {
        int len = readLength();
        align();
        byte[] bytes = readBytes(len);

        StringBuilder sb = new StringBuilder();
        int index = 0;

        // First byte
        if (len > 0) {
            int b = bytes[index++] & 0xFF;
            int first = b / 40;
            int second = b % 40;
            sb.append(first).append('.').append(second);
        }

        while (index < len) {
            long val = 0;
            int b;
            do {
                b = bytes[index++] & 0xFF;
                val = (val << 7) | (b & 0x7F);
            } while ((b & 0x80) != 0);
            sb.append('.').append(val);
        }

        return sb.toString();
    }

    /**
     * Read sequence length with SIZE constraint
     */
    public int readSequenceLength(Integer minSize, Integer maxSize) {
        if (minSize == null || maxSize == null) {
            return readLength();
        }

        if (minSize.equals(maxSize)) {
            return minSize;
        }

        int length;
        // Variable size within range
        long range = (long) maxSize - minSize;
        if (maxSize < 65536) {
            if (range < 255) {
                int bitLen = getMinBitLength(range);
                length = (int) readBits(bitLen) + minSize;
            } else if (range == 255) {
                align();
                length = (int) readBits(8) + minSize;
            } else {
                align();
                length = (int) readBits(16) + minSize;
            }
        } else {
            length = readLength();
        }

        return length;
    }

    /**
     * Read length determinant for unconstrained values.
     */
    public int readLength() {
        align(); // Length determinants are often aligned
        // Check first bit
        if (!peekBit()) {
            // 0xxxxxxx -> length is 0 to 127
            return (int) readBits(8);
        } else {
            // 1xxxxxxx
            if (!peekBit(1)) {
                // 10xxxxxx xxxxxxxx -> length 0 to 16k
                return (int) (readBits(16) & 0x3FFF);
            } else {
                throw new UnsupportedOperationException("Large lengths (>= 16k) not supported yet");
            }
        }
    }

    /**
     * Align to next byte boundary if not already aligned.
     */
    public void align() {
        if (bitIndex > 0) {
            bitIndex = 0;
            byteIndex++;
        }
    }

    /**
     * Peek at a single bit without advancing.
     */
    private boolean peekBit() {
        if (byteIndex >= data.length) return false;
        return ((data[byteIndex] >> (7 - bitIndex)) & 1) == 1;
    }

    /**
     * Peek at a bit with offset without advancing.
     */
    private boolean peekBit(int offset) {
        // Simplified peek for local check
        int totalBitIndex = byteIndex * 8 + bitIndex + offset;
        int bIndex = totalBitIndex / 8;
        int bitIdx = totalBitIndex % 8;
        if (bIndex >= data.length) return false;
        return ((data[bIndex] >> (7 - bitIdx)) & 1) == 1;
    }

    /**
     * Read specified number of bytes into array.
     */
    private byte[] readBytes(int len) {
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = (byte) readBits(8);
        }
        return bytes;
    }
}
