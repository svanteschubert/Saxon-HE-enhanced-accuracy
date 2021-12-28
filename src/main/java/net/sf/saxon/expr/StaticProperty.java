////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.Cardinality;

/**
 * This class contains constants identifying dependencies that an XPath expression
 * might have on its context.
 */

public abstract class StaticProperty {

    // TODO: use an EnumSet

    /**
     * Bit setting: Expression depends on current() item
     */

    public static final int DEPENDS_ON_CURRENT_ITEM = 1;

    /**
     * Bit setting: Expression depends on context item
     */

    public static final int DEPENDS_ON_CONTEXT_ITEM = 1 << 1;

    /**
     * Bit setting: Expression depends on position()
     */

    public static final int DEPENDS_ON_POSITION = 1 << 2;

    /**
     * Bit setting: Expression depends on last()
     */

    public static final int DEPENDS_ON_LAST = 1 << 3;

    /**
     * Bit setting: Expression depends on the document containing the context node
     */

    public static final int DEPENDS_ON_CONTEXT_DOCUMENT = 1 << 4;

    /**
     * Bit setting: Expression depends on current-group() and/or current-grouping-key()
     */

    public static final int DEPENDS_ON_CURRENT_GROUP = 1 << 5;

    /**
     * Bit setting: Expression depends on regex-group()
     */

    public static final int DEPENDS_ON_REGEX_GROUP = 1 << 6;


    /**
     * Bit setting: Expression depends on local variables
     */

    public static final int DEPENDS_ON_LOCAL_VARIABLES = 1 << 7;

    /**
     * Bit setting: Expression depends on user-defined functions
     */

    public static final int DEPENDS_ON_USER_FUNCTIONS = 1 << 8;

    /**
     * Bit setting: Expression depends on assignable global variables
     */

    public static final int DEPENDS_ON_ASSIGNABLE_GLOBALS = 1 << 9;

    /**
     * Bit setting: Expression can't be evaluated at compile time for reasons other than the above
     */

    public static final int DEPENDS_ON_RUNTIME_ENVIRONMENT = 1 << 10;

    /**
     * Bit setting: Expression depends on the static context, specifically, a part of the static
     * context that can vary from one expression in a query/stylesheet to another; the main
     * examples of this are the static base URI and the default collation
     */

    public static final int DEPENDS_ON_STATIC_CONTEXT = 1 << 11;

    /**
     * Combination of bits representing dependencies on the XSLT context
     */

    public static final int DEPENDS_ON_XSLT_CONTEXT =
            DEPENDS_ON_CURRENT_ITEM |
                    DEPENDS_ON_CURRENT_GROUP |
                    DEPENDS_ON_REGEX_GROUP |
                    DEPENDS_ON_ASSIGNABLE_GLOBALS;

    /**
     * Combination of bits representing dependencies on the focus
     */

    public static final int DEPENDS_ON_FOCUS =
            DEPENDS_ON_CONTEXT_ITEM |
                    DEPENDS_ON_POSITION |
                    DEPENDS_ON_LAST |
                    DEPENDS_ON_CONTEXT_DOCUMENT;

    /**
     * Combination of bits representing dependencies on the focus, but excluding dependencies
     * on the current document
     */

    public static final int DEPENDS_ON_NON_DOCUMENT_FOCUS =
            DEPENDS_ON_CONTEXT_ITEM |
                    DEPENDS_ON_POSITION |
                    DEPENDS_ON_LAST;

    /*
    * Bit set if an empty sequence is allowed
    */

    public static final int ALLOWS_ZERO = 1 << 13;

    /**
     * Bit set if a single value is allowed
     */

    public static final int ALLOWS_ONE = 1 << 14;

    /**
     * Bit set if multiple values are allowed
     */

    public static final int ALLOWS_MANY = 1 << 15;

    /**
     * Mask for all cardinality bits
     */

    public static final int CARDINALITY_MASK =
            ALLOWS_ZERO | ALLOWS_ONE | ALLOWS_MANY;

    /**
     * Occurence indicator for "one or more" (+)
     */

    public static final int ALLOWS_ONE_OR_MORE =
            ALLOWS_ONE | ALLOWS_MANY;

    /**
     * Occurence indicator for "zero or more" (*)
     */

    public static final int ALLOWS_ZERO_OR_MORE =
            ALLOWS_ZERO | ALLOWS_ONE | ALLOWS_MANY;

    /**
     * Occurence indicator for "zero or one" (?)
     */

    public static final int ALLOWS_ZERO_OR_ONE =
            ALLOWS_ZERO | ALLOWS_ONE;

    /**
     * Occurence indicator for "exactly one" (default occurrence indicator)
     */

    public static final int EXACTLY_ONE = ALLOWS_ONE;

    /**
     * Occurence indicator when an empty sequence is required
     */

    public static final int EMPTY = ALLOWS_ZERO;

    /**
     * Reduce the cardinality value to an integer in the range 0-7
     *
     * @param cardinality the result of calling getCardinality() on an expression
     * @return the cardinality code
     */

    public static int getCardinalityCode(int cardinality) {
        return (cardinality & CARDINALITY_MASK) >> 13;
    }

    /**
     * Expression property: this bit is set by getProperties() in the case of
     * an expression whose item type is node, when the nodes in the result are
     * guaranteed all to be in the same document as the context node. For
     * expressions that return values other than nodes, the setting is undefined.
     */

    public static final int CONTEXT_DOCUMENT_NODESET = 1 << 16;

    /**
     * Expression property: this bit is set by getProperties() in the case of
     * an expression whose item type is node, when the nodes in the result are
     * in document order.
     */

    public static final int ORDERED_NODESET = 1 << 17;

    /**
     * Expression property: this bit is set by getProperties() in the case of
     * an expression that delivers items in the reverse of the correct order, when unordered
     * retrieval is requested.
     */

    public static final int REVERSE_DOCUMENT_ORDER = 1 << 18;

    /**
     * Expression property: this bit is set by getProperties() in the case of
     * an expression that delivers a set of nodes with the guarantee that no node in the
     * set will be an ancestor of any other. This property is useful in deciding whether the
     * results of a path expression are pre-sorted. The property is only used in the case where
     * the ORDERED_NODESET property is true, so there is no point in setting it in other cases.
     */

    public static final int PEER_NODESET = 1 << 19;

    /**
     * Expression property: this bit is set by getProperties() in the case of
     * an expression that delivers a set of nodes with the guarantee that every node in the
     * result will be a descendant or self, or attribute or namespace, of the context node
     */

    public static final int SUBTREE_NODESET = 1 << 20;

    /**
     * Expression property: this bit is set by getProperties() in the case of
     * an expression that delivers a set of nodes with the guarantee that every node in the
     * result will be an attribute or namespace of the context node
     */

    public static final int ATTRIBUTE_NS_NODESET = 1 << 21;

    /**
     * Expression property: this bit indicates that the expression will always create new
     * nodes: more specifically, any node in the result of the expression is guaranteed
     * to be a newly created node.
     */

    public static final int ALL_NODES_NEWLY_CREATED = 1 << 22;

    /**
     * Expression property: this bit is set in the case of an expression that will
     * never return newly created nodes, nor a value that depends on the identity
     * of newly created nodes (for example generate-id(new-node())). Expressions
     * that are capable of creating new nodes cannot be moved out of loops as this could cause
     * too few nodes to be created: for example if f() creates a new node, then
     * count(for $i in 1 to 5 return f()) must be 5.
     */

    public static final int NO_NODES_NEWLY_CREATED = 1 << 23;

    /**
     * Expression property: this bit is set in the case of an expression that delivers
     * a set of nodes that are all in the same document (not necessarily the same
     * document as the context node).
     */

    public static final int SINGLE_DOCUMENT_NODESET = 1 << 24;

    /**
     * Expression property: this bit indicates that an expression has (or might have)
     * side-effects. This property is applied to calls on extension functions and to
     * certain instructions such as xsl:result-document and xsl:message.
     */

    public static final int HAS_SIDE_EFFECTS = 1 << 25;

    /**
     * Expression property: this bit indicates that although the static type of the expression
     * permits untyped atomic values, it is known that the value will not be untyped atomic.
     */

    public static final int NOT_UNTYPED_ATOMIC = 1 << 26;

    /**
     * Expression property: this bit indicates that in the result of an expression,
     * any element and attribute nodes that are present will have type annotation xs:untyped or
     * xs:untypedAtomic respectively, and that any document nodes that are present will have
     * no element children whose type annotation is anything other than xs:untyped
     */

    public static final int ALL_NODES_UNTYPED = 1 << 27;


    /**
     * Mask to select all the dependency bits
     */

    public static final int DEPENDENCY_MASK =
            DEPENDS_ON_CONTEXT_DOCUMENT |
                    DEPENDS_ON_CONTEXT_ITEM |
                    DEPENDS_ON_CURRENT_GROUP |
                    DEPENDS_ON_REGEX_GROUP |
                    DEPENDS_ON_CURRENT_ITEM |
                    DEPENDS_ON_FOCUS |
                    DEPENDS_ON_LOCAL_VARIABLES |
                    DEPENDS_ON_USER_FUNCTIONS |
                    DEPENDS_ON_ASSIGNABLE_GLOBALS |
                    DEPENDS_ON_RUNTIME_ENVIRONMENT |
                    DEPENDS_ON_STATIC_CONTEXT |
                    HAS_SIDE_EFFECTS;

    /**
     * Mask for "special properties": that is, all properties other than cardinality
     * and dependencies
     */

    public static final int SPECIAL_PROPERTY_MASK =
            CONTEXT_DOCUMENT_NODESET |
                    ORDERED_NODESET |
                    REVERSE_DOCUMENT_ORDER |
                    PEER_NODESET |
                    SUBTREE_NODESET |
                    ATTRIBUTE_NS_NODESET |
                    SINGLE_DOCUMENT_NODESET |
                    NO_NODES_NEWLY_CREATED |
                    HAS_SIDE_EFFECTS |
                    NOT_UNTYPED_ATOMIC |
                    ALL_NODES_UNTYPED |
                    ALL_NODES_NEWLY_CREATED;

    /**
     * Mask for nodeset-related properties
     */

    public static final int NODESET_PROPERTIES =
            CONTEXT_DOCUMENT_NODESET |
                    ORDERED_NODESET |
                    REVERSE_DOCUMENT_ORDER |
                    PEER_NODESET |
                    SUBTREE_NODESET |
                    ATTRIBUTE_NS_NODESET |
                    SINGLE_DOCUMENT_NODESET |
                    ALL_NODES_UNTYPED;

    // This class is not instantiated
    private StaticProperty() {
    }

    // For diagnostic display of static properties
    public static String display(int props) {
        FastStringBuffer s = new FastStringBuffer(128);
        s.append("D(");
        if ((props & DEPENDS_ON_CURRENT_ITEM) != 0) {
            s.append("U");
        }
        if ((props & DEPENDS_ON_CONTEXT_ITEM) != 0) {
            s.append("C");
        }
        if ((props & DEPENDS_ON_POSITION) != 0) {
            s.append("P");
        }
        if ((props & DEPENDS_ON_LAST) != 0) {
            s.append("L");
        }
        if ((props & DEPENDS_ON_CONTEXT_DOCUMENT) != 0) {
            s.append("D");
        }
        if ((props & DEPENDS_ON_LOCAL_VARIABLES) != 0) {
            s.append("V");
        }
        if ((props & DEPENDS_ON_ASSIGNABLE_GLOBALS) != 0) {
            s.append("A");
        }
        if ((props & DEPENDS_ON_REGEX_GROUP) != 0) {
            s.append("R");
        }
        if ((props & DEPENDS_ON_RUNTIME_ENVIRONMENT) != 0) {
            s.append("E");
        }
        if ((props & DEPENDS_ON_STATIC_CONTEXT) != 0) {
            s.append("S");
        }
        s.append(") C(");
        boolean m = Cardinality.allowsMany(props);
        boolean z = Cardinality.allowsZero(props);
        if (m && z) {
            s.append("*");
        } else if (m) {
            s.append("+");
        } else if (z)  {
            s.append("?");
        } else {
            s.append("1");
        }
        s.append(") S(");
        if ((props & HAS_SIDE_EFFECTS) != 0) {
            s.append("E");
        }
        if ((props & NO_NODES_NEWLY_CREATED) != 0) {
            s.append("N");
        }
        if ((props & NOT_UNTYPED_ATOMIC) != 0) {
            s.append("T");
        }
        if ((props & ORDERED_NODESET) != 0) {
            s.append("O");
        }
        if ((props & PEER_NODESET) != 0) {
            s.append("P");
        }
        if ((props & REVERSE_DOCUMENT_ORDER) != 0) {
            s.append("R");
        }
        if ((props & SINGLE_DOCUMENT_NODESET) != 0) {
            s.append("S");
        }
        if ((props & SUBTREE_NODESET) != 0) {
            s.append("D");
        }
        s.append(")");
        return s.toString();

    }
}
