////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.functions.*;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Type;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XSLT 3.0.
 * This includes the functions defined in XPath 3.1 by reference. It does not include higher-order
 * functions, and it does not include functions in the math/map/array namespaces.
 */

public class XSLT30FunctionSet extends BuiltInFunctionSet {

    private static XSLT30FunctionSet THE_INSTANCE = new XSLT30FunctionSet();

    public static XSLT30FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private XSLT30FunctionSet() {
        init();
    }

    private void init() {

        importFunctionSet(XPath31FunctionSet.getInstance());

        register("accumulator-after", 1, AccumulatorFn.AccumulatorAfter.class, AnyItemType.getInstance(),
                 STAR, LATE | CITEM)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("accumulator-before", 1, AccumulatorFn.AccumulatorBefore.class, AnyItemType.getInstance(),
                 STAR, LATE | CITEM)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("available-system-properties", 0, AvailableSystemProperties.class, BuiltInAtomicType.QNAME,
                 STAR, LATE);

        register("current", 0, Current.class, Type.ITEM_TYPE, ONE, LATE);

        register("current-group", 0, CurrentGroup.class, Type.ITEM_TYPE, STAR, LATE);

        register("current-grouping-key", 0, CurrentGroupingKey.class, BuiltInAtomicType.ANY_ATOMIC, STAR, LATE);

        register("current-merge-group", 0, CurrentMergeGroup.class, AnyItemType.getInstance(),
                 STAR, LATE);

        register("current-merge-group", 1, CurrentMergeGroup.class, AnyItemType.getInstance(),
                 STAR, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("current-merge-key", 0, CurrentMergeKey.class, BuiltInAtomicType.ANY_ATOMIC,
                 STAR, LATE);

        register("current-output-uri", 0, CurrentOutputUri.class, BuiltInAtomicType.ANY_URI, OPT, LATE);

        register("document", 1, DocumentFn.class, Type.NODE_TYPE, STAR, BASE | LATE | UO)
                .arg(0, Type.ITEM_TYPE, STAR, null);

        register("document", 2, DocumentFn.class, Type.NODE_TYPE, STAR, BASE | LATE | UO)
                .arg(0, Type.ITEM_TYPE, STAR, null)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("element-available", 1, ElementAvailable.class, BuiltInAtomicType.BOOLEAN, ONE, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("function-available", 1, FunctionAvailable.class, BuiltInAtomicType.BOOLEAN, ONE, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("function-available", 2, FunctionAvailable.class, BuiltInAtomicType.BOOLEAN, ONE, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("key", 2, KeyFn.class, Type.NODE_TYPE, STAR, CDOC | NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);

        register("key", 3, KeyFn.class, Type.NODE_TYPE, STAR, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(2, Type.NODE_TYPE, ONE, null);

        register("regex-group", 1, RegexGroup.class, BuiltInAtomicType.STRING, ONE, LATE | SIDE)
                .arg(0, BuiltInAtomicType.INTEGER, ONE, null);
        // Mark it as having side-effects to prevent loop-lifting

        register("stream-available", 1, StreamAvailable.class, BuiltInAtomicType.BOOLEAN,
                 ONE, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("system-property", 1, SystemProperty.class, BuiltInAtomicType.STRING, ONE, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("type-available", 1, TypeAvailable.class, BuiltInAtomicType.BOOLEAN, ONE, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-entity-public-id", 1, UnparsedEntity.UnparsedEntityPublicId.class, BuiltInAtomicType.STRING, ONE, CDOC | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-entity-public-id", 2, UnparsedEntity.UnparsedEntityPublicId.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("unparsed-entity-uri", 1, UnparsedEntity.UnparsedEntityUri.class, BuiltInAtomicType.ANY_URI, ONE, CDOC | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-entity-uri", 2, UnparsedEntity.UnparsedEntityUri.class, BuiltInAtomicType.ANY_URI, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, Type.NODE_TYPE, ONE, null);


    }


}

