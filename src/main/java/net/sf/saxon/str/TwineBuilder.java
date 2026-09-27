// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.z.IntIterator;

import java.util.Arrays;

/**
 * A builder class for unicode strings. This is a simpler and more efficient
 * alternative to {@link UnicodeBuilder} for use when small strings (especially
 * strings consisting exclusively of 8-bit characters) are constructed one
 * character at a time.
 */

public abstract class TwineBuilder {

    /**
     * Create a builder for strings with an estimate of the string size
     * @param initialSize the expected size of the string, once built
     * @return a new builder
     */

    public static TwineBuilder make(int initialSize) {
        return new T8(Math.max(initialSize, 2));
    }

    /**
     * Append a codepoint to the Unicode string
     * @param codepoint the codepoint to be appended
     * @return a replacement builder. NOTE: although this
     * will often be the original builder, it will sometimes
     * be a new one, so it is important to use the returned
     * builder for future calls.
     */

    public abstract TwineBuilder append(int codepoint);

    /**
     * Append a string or other CharSequence
     * @param chars the string to be appended
     * @return the new TwineBuilder to be used for subsequent calls
     */
    public final TwineBuilder append(CharSequence chars) {
        TwineBuilder tb = this;
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            if (UTF16CharacterSet.isHighSurrogate(c)) {
                char d = chars.charAt(++i);
                tb = tb.append(UTF16CharacterSet.combinePair(c, d));
            } else {
                tb = tb.append(c);
            }
        }
        return tb;
    }

    /**
     * Append a unicode string
     *
     * @param chars the string to be appended
     * @return the new TwineBuilder to be used for subsequent calls
     */
    public TwineBuilder append(UnicodeString chars) {
        TwineBuilder tb = this;
        IntIterator codePoints = chars.codePoints();
        while (codePoints.hasNext()) {
            tb = tb.append(codePoints.next());
        }
        return tb;
    }

    /**
     * Get the length, in codepoints
     * @return the length in codepoints
     */

    public abstract int length();

    /**
     * Ask if the buffer is empty
     * @return true if it is empty
     */

    public boolean isEmpty() {
        return length() == 0;
    }

    /**
     * Get the completed Unicode string
     * @return the unicode string that has been built.
     */

    public abstract UnicodeString toUnicodeString();

    /**
     * Get the completed result as a Java string
     * @return the string that has been built.
     */

    @Override
    public final String toString() {
        return toUnicodeString().toString();
    }

    /**
     * Implementation class for strings of 8-bit characters
     */

    private static class T8 extends TwineBuilder {

        byte[] buffer;
        int used;
        protected T8(int initialSize) {
            buffer = new byte[initialSize];
            used = 0;
        }
        @Override
        public TwineBuilder append(int codepoint) {
            if (codepoint < 256) {
                if (used == buffer.length) {
                    buffer = Arrays.copyOf(buffer, used*2);
                }
                buffer[used++] = (byte)codepoint;
                return this;
            } else if (codepoint < 65536) {
                char[] buffer16 = new char[Math.max(used*2, buffer.length)];
                StringTool.copy8to16(buffer, 0, buffer16, 0, used);
                return new T16(buffer16, used).append(codepoint);
            } else {
                byte[] buffer24 = new byte[Math.max(used * 6, buffer.length*3)];
                StringTool.copy8to24(buffer, 0, buffer24, 0, used);
                return new T24(buffer24, used).append(codepoint);
            }
        }

        @Override
        public TwineBuilder append(UnicodeString chars) {
            if (chars instanceof Twine8) {
                // special-case this one to achieve a bulk move
                int len = chars.length32();
                if (used + len > buffer.length) {
                    buffer = Arrays.copyOf(buffer, Math.max(used * 2, used + len));
                }
                System.arraycopy(((Twine8)chars).getByteArray(), 0, buffer, used, len);
                used += len;
                return this;
            } else {
                return super.append(chars);
            }
        }

        @Override
        public int length() {
            return used;
        }

        @Override
        public UnicodeString toUnicodeString() {
            if (used == 0) {
                return EmptyUnicodeString.getInstance();
            }
            if (used == 1) {
                return new UnicodeChar(buffer[0] & 0xff);
            }
            return new Twine8(buffer, 0, used);
        }
    }

    /**
     * Implementation class for strings of 16-bit characters
     */

    private static class T16 extends TwineBuilder {

        char[] buffer;
        int used;

        protected T16(int initialSize) {
            buffer = new char[initialSize];
            used = 0;
        }

        protected T16(char[] buffer, int used) {
            this.buffer = buffer;
            this.used = used;
        }

        @Override
        public TwineBuilder append(int codepoint) {
            if (codepoint < 65536) {
                if (used == buffer.length) {
                    buffer = Arrays.copyOf(buffer, used * 2);
                }
                buffer[used++] = (char) codepoint;
                return this;
            } else  {
                byte[] buffer24 = new byte[Math.max(used * 6, buffer.length)];
                StringTool.copy16to24(buffer, 0, buffer24, 0, used);
                return new T24(buffer24, used).append(codepoint);
            }
        }

        @Override
        public int length() {
            return used;
        }

        @Override
        public UnicodeString toUnicodeString() {
            if (used == 0) {
                return EmptyUnicodeString.getInstance();
            }
            if (used == 1) {
                return new UnicodeChar(buffer[0]);
            }
            return new Twine16(buffer, 0, used);
        }
    }

    /**
     * Implementation class for strings of 24-bit characters
     */

    private static class T24 extends TwineBuilder {

        private byte[] buffer;
        private int used;    // number of bytes used (3 * the number of codepoints)

        protected T24(int initialSize) {
            buffer = new byte[initialSize*3];
            used = 0;
        }

        protected T24(byte[] buffer, int codepointsUsed) {
            this.buffer = buffer;
            this.used = codepointsUsed * 3;
        }

        @Override
        public TwineBuilder append(int codepoint) {
            if (used == buffer.length) {
                buffer = Arrays.copyOf(buffer, used * 2);
            }
            buffer[used++] = (byte) ((codepoint >> 16) & 0xff);
            buffer[used++] = (byte) ((codepoint >> 8) & 0xff);
            buffer[used++] = (byte) (codepoint & 0xff);
            return this;
        }

        @Override
        public int length() {
            return used / 3;
        }

        @Override
        public UnicodeString toUnicodeString() {
            return new Twine24(Arrays.copyOf(buffer, used));
        }
    }
}

