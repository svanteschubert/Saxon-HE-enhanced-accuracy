// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern.qname;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.type.Affinity;

/**
 * A {@code QNameTest} that matches any QName
 */

public class AnyQNameTest implements QNameTest {

    private final static AnyQNameTest INSTANCE = new AnyQNameTest();

    public static AnyQNameTest getInstance() {
        return INSTANCE;
    }

    private AnyQNameTest(){};

    @Override
    public boolean matches(StructuredQName qname) {
        return true;
    }

    @Override
    public String exportQNameTest() {
        return "*";
    }

    @Override
    public String toString() {
        return "*";
    }

    @Override
    public Affinity relationship(QNameTest other) {
        return other instanceof AnyQNameTest ? Affinity.SAME_TYPE : Affinity.SUBSUMES;
    }

}

