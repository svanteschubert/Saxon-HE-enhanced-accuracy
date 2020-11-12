////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.UntypedAtomicValue;

/**
 * Implementation of an internal function map:untyped-contains(Map, key) =&gt; boolean,
 * which is like map:contains except that if the supplied key is untyped atomic, it
 * is converted to all the possible types present in the map and returns true if the
 * key after conversion is present. In addition, if the supplied key is NaN then the
 * result is always false.
 */
public class MapUntypedContains extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ConversionRules rules = context.getConfiguration().getConversionRules();
            MapItem map = (MapItem) arguments[0].head();
            AtomicValue key = (AtomicValue) arguments[1].head();
            if (key instanceof UntypedAtomicValue) {
                for (PrimitiveUType prim : map.getKeyUType().decompose()) {
                    BuiltInAtomicType t = (BuiltInAtomicType)prim.toItemType();
                    StringConverter converter = t.getStringConverter(rules);
                    ConversionResult av = converter.convert(key);
                    if (av instanceof ValidationFailure) {
                        // in the case of xs:decimal, try conversion via xs:double
                        if (prim.equals(PrimitiveUType.DECIMAL)) {
                            converter = BuiltInAtomicType.DOUBLE.getStringConverter(rules);
                            av = converter.convert(key);
                            if (av instanceof AtomicValue) {
                                if (map.get(av.asAtomic()) != null) {
                                    return BooleanValue.TRUE;
                                }
                            }
                        }
                    } else if (map.get(av.asAtomic()) != null) {
                        return BooleanValue.TRUE;
                    }
                }
                return BooleanValue.FALSE;
            } else if (key.isNaN()) {
                return BooleanValue.FALSE;
            }
            boolean result = map.get(key) != null;
            return BooleanValue.get(result);
        }

}

// Copyright (c) 2018-2020 Saxonica Limited

