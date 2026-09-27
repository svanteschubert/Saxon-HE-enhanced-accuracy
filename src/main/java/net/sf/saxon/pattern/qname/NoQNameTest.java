// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern.qname;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.type.Affinity;

public class NoQNameTest implements QNameTest {

    private final static NoQNameTest INSTANCE = new NoQNameTest();

    public static NoQNameTest getInstance() {
        return INSTANCE;
    }

    private NoQNameTest(){};

    @Override
    public boolean matches(StructuredQName qname) {
        return false;
    }

    public Affinity relationship(QNameTest other) {
        if (other instanceof NoQNameTest) {
            return Affinity.SAME_TYPE;
        } else {
            return Affinity.SUBSUMED_BY;
        }
    }


    @Override
    public String exportQNameTest() {
        return "xs:error";
    }
}

