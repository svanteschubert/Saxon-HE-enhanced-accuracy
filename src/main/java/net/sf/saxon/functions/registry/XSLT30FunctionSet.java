////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

////import com.saxonica.functions.registry.XPath40FunctionSet;
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

    private static final XSLT30FunctionSet THE_INSTANCE = new XSLT30FunctionSet();

    public static XSLT30FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    protected XSLT30FunctionSet() {
        init();
    }

    protected BuiltInFunctionSet correspondingXPathFunctionSet() {
        return XPath31FunctionSet.getInstance();
    }

    private void init() {

        importFunctionSet(correspondingXPathFunctionSet());

        register("accumulator-after", 1, e -> {
            return e.populate(AccumulatorFn.AccumulatorAfter::new, AnyItemType.getInstance(),
                                  STAR, LATE | CITEM)
                    .arg(0, BuiltInAtomicType.STRING, ONE, null);
        });

        register("accumulator-before", 1, e -> {
            return e.populate(AccumulatorFn.AccumulatorBefore::new, AnyItemType.getInstance(),
                                  STAR, LATE | CITEM)
                    .arg(0, BuiltInAtomicType.STRING, ONE, null);
        });

        register("available-system-properties", 0, e -> e.populate( AvailableSystemProperties::new, BuiltInAtomicType.QNAME,
                 STAR, LATE));

        register("current", 0, e -> e.populate( Current::new, Type.ITEM_TYPE, ONE, LATE));

        register("current-group", 0, e -> e.populate( CurrentGroup::new, Type.ITEM_TYPE, STAR, LATE));

        register("current-grouping-key", 0, e -> e.populate( CurrentGroupingKey::new, BuiltInAtomicType.ANY_ATOMIC, STAR, LATE));

        register("current-merge-group", 0, e -> {
            return e.populate(CurrentMergeGroup::new, AnyItemType.getInstance(),
                                  STAR, LATE);
        });

        register("current-merge-group", 1, e -> {
            return e.populate(CurrentMergeGroup::new, AnyItemType.getInstance(),
                                  STAR, LATE)
                    .arg(0, BuiltInAtomicType.STRING, ONE, null);
        });

        register("current-merge-key", 0, e -> e.populate( CurrentMergeKey::new, BuiltInAtomicType.ANY_ATOMIC,
                 STAR, LATE));

        register("current-output-uri", 0, e -> e.populate( CurrentOutputUri::new, BuiltInAtomicType.ANY_URI, OPT, LATE));

        register("document", 1, e -> e.populate(DocumentFn::new, Type.XNODE_TYPE, STAR, BASE | LATE | UO)
                .arg(0, Type.ITEM_TYPE, STAR, null));

        register("document", 2, e -> e.populate(DocumentFn::new, Type.XNODE_TYPE, STAR, BASE | LATE | UO)
                .arg(0, Type.ITEM_TYPE, STAR, null)
                .arg(1, Type.XNODE_TYPE, ONE, null));

        register("element-available", 1, e -> e.populate( ElementAvailable::new, BuiltInAtomicType.BOOLEAN, ONE, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        register("function-available", 1, e -> e.populate( FunctionAvailable::new, BuiltInAtomicType.BOOLEAN, ONE, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        register("function-available", 2, e -> e.populate( FunctionAvailable::new, BuiltInAtomicType.BOOLEAN, ONE, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null));

        register("key", 2, e -> e.populate(KeyFn::new, Type.XNODE_TYPE, STAR, CDOC | NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY));

        register("key", 3, e -> e.populate(KeyFn::new, Type.XNODE_TYPE, STAR, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(2, Type.XNODE_TYPE, ONE, null));

        register("regex-group", 1, e -> e.populate( RegexGroup::new, BuiltInAtomicType.STRING, ONE, LATE | SIDE)
                .arg(0, BuiltInAtomicType.INTEGER, ONE, null));
        // Mark it as having side-effects to prevent loop-lifting

        register("stream-available", 1, e -> e.populate( StreamAvailable::new, BuiltInAtomicType.BOOLEAN,
                 ONE, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));

        register("system-property", 1, e -> e.populate( SystemProperty::new, BuiltInAtomicType.STRING, ONE, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        register("type-available", 1, e -> e.populate( TypeAvailable::new, BuiltInAtomicType.BOOLEAN, ONE, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        register("unparsed-entity-public-id", 1, e -> e.populate( UnparsedEntity.UnparsedEntityPublicId::new, BuiltInAtomicType.STRING, ONE, CDOC | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        register("unparsed-entity-public-id", 2, e -> e.populate( UnparsedEntity.UnparsedEntityPublicId::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, Type.XNODE_TYPE, ONE, null));

        register("unparsed-entity-uri", 1, e -> e.populate( UnparsedEntity.UnparsedEntityUri::new, BuiltInAtomicType.ANY_URI, ONE, CDOC | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        register("unparsed-entity-uri", 2, e -> e.populate( UnparsedEntity.UnparsedEntityUri::new, BuiltInAtomicType.ANY_URI, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, Type.XNODE_TYPE, ONE, null));


    }


}

