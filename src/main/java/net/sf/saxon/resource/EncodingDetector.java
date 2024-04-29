////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.lib.Logger;
import net.sf.saxon.query.InputStreamMarker;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class to detect the encoding of a stream by examining the initial bytes
 */

public class EncodingDetector {
    /**
     * Try to detect the encoding from the start of the input stream
     *
     * @param is  the input stream
     * @param defaultEncoding the fallback encoding, normally UTF-8
     * @param err logger to be used for diagnostics, or null
     * @return the inferred encoding, defaulting to UTF-8
     * @throws IOException if it isn't possible to mark the current position on the input stream and read ahead
     */

    public static String inferStreamEncoding(InputStream is, String defaultEncoding, Logger err) throws IOException {
        InputStreamMarker marker = new InputStreamMarker(is);
        marker.mark(100);
        byte[] start = new byte[100];
        int read = is.read(start, 0, 100);
        marker.reset();
        return inferEncoding(start, read, defaultEncoding, err);
    }

    /**
     * Infer the encoding of a file by reading the first few bytes of the file
     *
     * @param start  the first few bytes of the file
     * @param read   the number of bytes that have been read
     * @param defaultEncoding the fallback encoding, normally UTF-8
     * @param logger Logger to receive diagnostic messages, or null
     * @return the inferred encoding
     */

    private static String inferEncoding(byte[] start, int read, String defaultEncoding, Logger logger) {
        boolean debug = logger != null;
        if (read >= 2) {
            if (ch(start[0]) == 0xFE && ch(start[1]) == 0xFF) {
                if (debug) {
                    logger.info("unparsed-text(): found UTF-16 byte order mark");
                }
                return "UTF-16";
            } else if (ch(start[0]) == 0xFF && ch(start[1]) == 0xFE) {
                if (debug) {
                    logger.info("unparsed-text(): found UTF-16LE byte order mark");
                }
                return "UTF-16LE";
            }
        }
        if (read >= 3) {
            if (ch(start[0]) == 0xEF && ch(start[1]) == 0xBB && ch(start[2]) == 0xBF) {
                if (debug) {
                    logger.info("unparsed-text(): found UTF-8 byte order mark");
                }
                return "UTF-8";
            }
        }
        if (read >= 4) {
            if (ch(start[0]) == '<' && ch(start[1]) == '?' &&
                    ch(start[2]) == 'x' && ch(start[3]) == 'm' && ch(start[4]) == 'l') {
                if (debug) {
                    logger.info("unparsed-text(): found XML declaration");
                }
                StringBuilder sb = new StringBuilder(read);
                for (int b = 0; b < read; b++) {
                    sb.append((char) start[b]);
                }
                String p = sb.toString();
                int v = p.indexOf("encoding");
                if (v >= 0) {
                    v += 8;
                    while (v < p.length() && " \n\r\t=\"'".indexOf(p.charAt(v)) >= 0) {
                        v++;
                    }
                    sb.setLength(0);
                    while (v < p.length() && p.charAt(v) != '"' && p.charAt(v) != '\'') {
                        sb.append(p.charAt(v++));
                    }
                    if (debug) {
                        logger.info("unparsed-text(): encoding in XML declaration = " + sb.toString());
                    }
                    return sb.toString();
                }
                if (debug) {
                    logger.info("unparsed-text(): no encoding found in XML declaration");
                }
            }
        } else if (read > 0 && start[0] == 0 && start[2] == 0 && start[4] == 0 && start[6] == 0) {
            if (debug) {
                logger.info("unparsed-text(): even-numbered bytes are zero, inferring UTF-16");
            }
            return "UTF-16";
        } else if (read > 1 && start[1] == 0 && start[3] == 0 && start[5] == 0 && start[7] == 0) {
            if (debug) {
                logger.info("unparsed-text(): odd-numbered bytes are zero, inferring UTF-16LE");
            }
            return "UTF-16LE";
        }
        // If all else fails, assume UTF-8
        if (debug) {
            logger.info("unparsed-text(): assuming fallback encoding (UTF-8)");
        }
        return defaultEncoding;
    }

    private static int ch(byte b) {
        return (int)b & 0xff;
    }
}

