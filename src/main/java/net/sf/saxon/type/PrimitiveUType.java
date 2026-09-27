////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;


public enum PrimitiveUType {
    
    DOCUMENT (0),
    ELEMENT (1),
    ATTRIBUTE (2),
    TEXT (3),
    COMMENT (4),
    PI (5),
    NAMESPACE (6),

    JNODE (7),

    FUNCTION (8),

    STRING (9),
    BOOLEAN (10),
    DECIMAL (11),
    FLOAT (12),
    DOUBLE (13),
    DURATION (14),
    DATE_TIME (15),
    TIME (16),
    DATE (17),
    G_YEAR_MONTH (18),
    G_YEAR (19),
    G_MONTH_DAY (20),
    G_DAY (21),
    G_MONTH (22),
    HEX_BINARY (23),
    BASE64_BINARY (24),
    ANY_URI (25),
    QNAME (26),
    NOTATION (27),

    UNTYPED_ATOMIC (28),

    EXTENSION (30);

    private final int bit;

    PrimitiveUType(int bit) {
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }

    public static PrimitiveUType forBit(int bitValue) {
        return values()[bitValue];
    }

    public String toString() {
        return switch (this) {
            case DOCUMENT -> "document";
            case ELEMENT -> "element";
            case ATTRIBUTE -> "attribute";
            case TEXT -> "text";
            case COMMENT -> "comment";
            case PI -> "processing-instruction";
            case NAMESPACE -> "namespace";
            case JNODE -> "JNode";
            case FUNCTION -> "function";
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            case DECIMAL -> "decimal";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case DURATION -> "duration";
            case DATE_TIME -> "dateTime";
            case TIME -> "time";
            case DATE -> "date";
            case G_YEAR_MONTH -> "gYearMonth";
            case G_YEAR -> "gYear";
            case G_MONTH_DAY -> "gMonthDay";
            case G_DAY -> "gDay";
            case G_MONTH -> "gMonth";
            case HEX_BINARY -> "hexBinary";
            case BASE64_BINARY -> "base64Binary";
            case ANY_URI -> "anyURI";
            case QNAME -> "QName";
            case NOTATION -> "NOTATION";
            case UNTYPED_ATOMIC -> "untypedAtomic";
            case EXTENSION -> "external object";
            default -> "???";
        };
    }

}

