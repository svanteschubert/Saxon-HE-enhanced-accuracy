////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.type.Type;

/**
 * Enumeration class defining the seven kinds of node defined in the XDM model
 */
public enum XdmNodeKind {
    DOCUMENT(Type.DOCUMENT),
    ELEMENT(Type.ELEMENT),
    ATTRIBUTE(Type.ATTRIBUTE),
    TEXT(Type.TEXT),
    COMMENT(Type.COMMENT),
    PROCESSING_INSTRUCTION(Type.PROCESSING_INSTRUCTION),
    NAMESPACE(Type.NAMESPACE);

    private int number;

    private XdmNodeKind(int number) {
        this.number = number;
    }

    protected int getNumber() {
        return number;
    }

    /**
     * Get the node kind corresponding to one of the integer constants in class
     * {@link net.sf.saxon.type.Type}, for example {@link Type#ELEMENT}. These integer
     * constants are chosen to be consistent with those used in DOM, though the semantics
     * of the different node kinds are slightly different.
     * @param type the integer type code, for example {@link Type#ELEMENT} or {@link Type#TEXT}
     * @return the corresponding {@link XdmNodeKind};
     * @since 10.0
     */
    public static XdmNodeKind forType(int type) {
        switch (type) {
            case Type.DOCUMENT:
                return DOCUMENT;
            case Type.ELEMENT:
                return ELEMENT;
            case Type.ATTRIBUTE:
                return ATTRIBUTE;
            case Type.TEXT:
                return TEXT;
            case Type.COMMENT:
                return COMMENT;
            case Type.PROCESSING_INSTRUCTION:
                return PROCESSING_INSTRUCTION;
            case Type.NAMESPACE:
                return NAMESPACE;
            default:
                throw new IllegalArgumentException();
        }
    }
}

