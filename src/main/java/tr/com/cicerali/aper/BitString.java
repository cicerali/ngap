package tr.com.cicerali.aper;

import java.util.BitSet;
import java.util.Objects;

public class BitString {
    private final BitSet value;
    private final int length;

    public BitString(BitSet value, int length) {
        this.value = value;
        this.length = length;
    }

    public BitSet getValue() {
        return value;
    }

    public int getLength() {
        return length;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BitString bitString)) return false;
        return length == bitString.length && Objects.equals(value, bitString.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, length);
    }


    @Override
    public String toString() {
        if (length <= 0) return "";
        StringBuilder sb = new StringBuilder(length);
        for (int i = length - 1; i >= 0; i--) {
            sb.append(value.get(i) ? '1' : '0');
        }
        return sb.toString();
    }
}
