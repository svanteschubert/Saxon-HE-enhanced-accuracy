////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;


import net.sf.saxon.pattern.NodeKindTest;

public enum PrimitiveUType {

    DOCUMENT (0),
    ELEMENT (1),
    ATTRIBUTE (2),
    TEXT (3),
    COMMENT (4),
    PI (5),
    NAMESPACE (6),

    FUNCTION (7),

    STRING (8),
    BOOLEAN (9),
    DECIMAL (10),
    FLOAT (11),
    DOUBLE (12),
    DURATION (13),
    DATE_TIME (14),
    TIME (15),
    DATE (16),
    G_YEAR_MONTH (17),
    G_YEAR (18),
    G_MONTH_DAY (19),
    G_DAY (20),
    G_MONTH (21),
    HEX_BINARY (22),
    BASE64_BINARY (23),
    ANY_URI (24),
    QNAME (25),
    NOTATION (26),

    UNTYPED_ATOMIC (27),

    EXTENSION (30);


    private final int bit;

    private PrimitiveUType(int bit) {
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }

    public UType toUType() {
        return new UType(1<<bit);
    }

    public static PrimitiveUType forBit(int bit) {
        return values()[bit];
    }

    public String toString() {
        switch (this) {
            case DOCUMENT:
                return "document";
            case ELEMENT:
                return "element";
            case ATTRIBUTE:
                return "attribute";
            case TEXT:
                return "text";
            case COMMENT:
                return "comment";
            case PI:
                return "processing-instruction";
            case NAMESPACE:
                return "namespace";

            case FUNCTION:
                return "function";

            case STRING:
                return "string";
            case BOOLEAN:
                return "boolean";
            case DECIMAL:
                return "decimal";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case DURATION:
                return "duration";
            case DATE_TIME:
                return "dateTime";
            case TIME:
                return "time";
            case DATE:
                return "date";
            case G_YEAR_MONTH:
                return "gYearMonth";
            case G_YEAR:
                return "gYear";
            case G_MONTH_DAY:
                return "gMonthDay";
            case G_DAY:
                return "gDay";
            case G_MONTH:
                return "gMoonth";
            case HEX_BINARY:
                return "hexBinary";
            case BASE64_BINARY:
                return "base64Binary";
            case ANY_URI:
                return "anyURI";
            case QNAME:
                return "QName";
            case NOTATION:
                return "NOTATION";
            case UNTYPED_ATOMIC:
                return "untypedAtomic";

            case EXTENSION:
                return "external object";
            default:
                return "???";

        }
    }

    public ItemType toItemType() {
        switch (this) {
            case DOCUMENT:
                return NodeKindTest.DOCUMENT;
            case ELEMENT:
                return NodeKindTest.ELEMENT;
            case ATTRIBUTE:
                return NodeKindTest.ATTRIBUTE;
            case TEXT:
                return NodeKindTest.TEXT;
            case COMMENT:
                return NodeKindTest.COMMENT;
            case PI:
                return NodeKindTest.PROCESSING_INSTRUCTION;
            case NAMESPACE:
                return NodeKindTest.NAMESPACE;

            case FUNCTION:
                return AnyFunctionType.getInstance();

            case STRING:
                return BuiltInAtomicType.STRING;
            case BOOLEAN:
                return BuiltInAtomicType.BOOLEAN;
            case DECIMAL:
                return BuiltInAtomicType.DECIMAL;
            case FLOAT:
                return BuiltInAtomicType.FLOAT;
            case DOUBLE:
                return BuiltInAtomicType.DOUBLE;
            case DURATION:
                return BuiltInAtomicType.DURATION;
            case DATE_TIME:
                return BuiltInAtomicType.DATE_TIME;
            case TIME:
                return BuiltInAtomicType.TIME;
            case DATE:
                return BuiltInAtomicType.DATE;
            case G_YEAR_MONTH:
                return BuiltInAtomicType.G_YEAR_MONTH;
            case G_YEAR:
                return BuiltInAtomicType.G_YEAR;
            case G_MONTH_DAY:
                return BuiltInAtomicType.G_MONTH_DAY;
            case G_DAY:
                return BuiltInAtomicType.G_DAY;
            case G_MONTH:
                return BuiltInAtomicType.G_MONTH;
            case HEX_BINARY:
                return BuiltInAtomicType.HEX_BINARY;
            case BASE64_BINARY:
                return BuiltInAtomicType.BASE64_BINARY;
            case ANY_URI:
                return BuiltInAtomicType.ANY_URI;
            case QNAME:
                return BuiltInAtomicType.QNAME;
            case NOTATION:
                return BuiltInAtomicType.NOTATION;
            case UNTYPED_ATOMIC:
                return BuiltInAtomicType.UNTYPED_ATOMIC;

            case EXTENSION:
                //return JavaExternalObjectType.EXTERNAL_OBJECT_TYPE;
                return AnyItemType.getInstance();
            default:
                throw new IllegalArgumentException();

        }
    }
}

