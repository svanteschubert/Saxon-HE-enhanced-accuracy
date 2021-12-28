////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.StringValue;


/**
 * Implementation of the exslt-common function library. This is available in all Saxon versions.
 */
public class ExsltCommonFunctionSet extends BuiltInFunctionSet {

    private static ExsltCommonFunctionSet THE_INSTANCE = new ExsltCommonFunctionSet();

    public static ExsltCommonFunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private ExsltCommonFunctionSet() {
        init();
    }

    private void init() {

        register("node-set", 1, NodeSetFn.class, AnyItemType.getInstance(), OPT, 0)
                .arg(0, AnyItemType.getInstance(), OPT, EMPTY);

        register("object-type", 1, ObjectTypeFn.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, AnyItemType.getInstance(), ONE, null);

    }

    @Override
    public String getNamespace() {
        return NamespaceConstant.EXSLT_COMMON;
    }

    @Override
    public String getConventionalPrefix() {
        return "exsltCommon";
    }

    /**
     * Implement exslt:node-set
     */

    public static class NodeSetFn extends SystemFunction {
        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            return arguments[0];
        }
    }

    /**
     * Implement exslt:object-type
     */

    public static class ObjectTypeFn extends SystemFunction {
        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            final TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
            Item value = arguments[0].head();
            ItemType type = SequenceTool.getItemType(value, th);
            if (th.isSubType(type, AnyNodeTest.getInstance())) {
                return new StringValue("node-set");
            } else if (th.isSubType(type, BuiltInAtomicType.STRING)) {
                return new StringValue("string");
            } else if (NumericType.isNumericType(type)) {
                return new StringValue("number");
            } else if (th.isSubType(type, BuiltInAtomicType.BOOLEAN)) {
                return new StringValue("boolean");
            } else {
                return new StringValue(type.toString());
            }
        }
    }



}

// Copyright (c) 2018-2020 Saxonica Limited
