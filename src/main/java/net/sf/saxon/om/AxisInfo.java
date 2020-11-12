////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.PrimitiveUType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;
import net.sf.saxon.z.IntHashMap;

import java.util.Set;

/**
 * An axis, that is a direction of navigation in the document structure.
 */

public final class AxisInfo {

    /**
     * Constant representing the ancestor axis
     */

    public static final int ANCESTOR = 0;
    /**
     * Constant representing the ancestor-or-self axis
     */
    public static final int ANCESTOR_OR_SELF = 1;
    /**
     * Constant representing the attribute axis
     */
    public static final int ATTRIBUTE = 2;
    /**
     * Constant representing the child axis
     */
    public static final int CHILD = 3;
    /**
     * Constant representing the descendant axis
     */
    public static final int DESCENDANT = 4;
    /**
     * Constant representing the descendant-or-self axis
     */
    public static final int DESCENDANT_OR_SELF = 5;
    /**
     * Constant representing the following axis
     */
    public static final int FOLLOWING = 6;
    /**
     * Constant representing the following-sibling axis
     */
    public static final int FOLLOWING_SIBLING = 7;
    /**
     * Constant representing the namespace axis
     */
    public static final int NAMESPACE = 8;
    /**
     * Constant representing the parent axis
     */
    public static final int PARENT = 9;
    /**
     * Constant representing the preceding axis
     */
    public static final int PRECEDING = 10;
    /**
     * Constant representing the preceding-sibling axis
     */
    public static final int PRECEDING_SIBLING = 11;
    /**
     * Constant representing the self axis
     */
    public static final int SELF = 12;

    // preceding-or-ancestor axis gives all preceding nodes including ancestors,
    // in reverse document order

    /**
     * Constant representing the preceding-or-ancestor axis. This axis is used internally by the xsl:number implementation, it returns the union of the preceding axis and the ancestor axis.
     */
    public static final int PRECEDING_OR_ANCESTOR = 13;

    /**
     * Table indicating the principal node type of each axis
     */

    public static final short[] principalNodeType =
            {
                    Type.ELEMENT,       // ANCESTOR
                    Type.ELEMENT,       // ANCESTOR_OR_SELF;
                    Type.ATTRIBUTE,     // ATTRIBUTE;
                    Type.ELEMENT,       // CHILD;
                    Type.ELEMENT,       // DESCENDANT;
                    Type.ELEMENT,       // DESCENDANT_OR_SELF;
                    Type.ELEMENT,       // FOLLOWING;
                    Type.ELEMENT,       // FOLLOWING_SIBLING;
                    Type.NAMESPACE,     // NAMESPACE;
                    Type.ELEMENT,       // PARENT;
                    Type.ELEMENT,       // PRECEDING;
                    Type.ELEMENT,       // PRECEDING_SIBLING;
                    Type.ELEMENT,       // SELF;
                    Type.ELEMENT,       // PRECEDING_OR_ANCESTOR;
            };

    public static final UType[] principalNodeUType =
            {
                    UType.ELEMENT,       // ANCESTOR
                    UType.ELEMENT,       // ANCESTOR_OR_SELF;
                    UType.ATTRIBUTE,     // ATTRIBUTE;
                    UType.ELEMENT,       // CHILD;
                    UType.ELEMENT,       // DESCENDANT;
                    UType.ELEMENT,       // DESCENDANT_OR_SELF;
                    UType.ELEMENT,       // FOLLOWING;
                    UType.ELEMENT,       // FOLLOWING_SIBLING;
                    UType.NAMESPACE,     // NAMESPACE;
                    UType.ELEMENT,       // PARENT;
                    UType.ELEMENT,       // PRECEDING;
                    UType.ELEMENT,       // PRECEDING_SIBLING;
                    UType.ELEMENT,       // SELF;
                    UType.ELEMENT,       // PRECEDING_OR_ANCESTOR;
            };

    /**
     * Table indicating for each axis whether it is in forwards document order
     */

    public static final boolean[] isForwards =
            {
                    false,          // ANCESTOR
                    false,          // ANCESTOR_OR_SELF;
                    true,           // ATTRIBUTE;
                    true,           // CHILD;
                    true,           // DESCENDANT;
                    true,           // DESCENDANT_OR_SELF;
                    true,           // FOLLOWING;
                    true,           // FOLLOWING_SIBLING;
                    true,           // NAMESPACE;
                    true,           // PARENT;
                    false,          // PRECEDING;
                    false,          // PRECEDING_SIBLING;
                    true,           // SELF;
                    false,          // PRECEDING_OR_ANCESTOR;
            };

    /**
     * Table indicating for each axis whether it is a peer axis. An axis is a peer
     * axis if no node on the axis is an ancestor of another node on the axis.
     */

    public static final boolean[] isPeerAxis =
            {
                    false,          // ANCESTOR
                    false,          // ANCESTOR_OR_SELF;
                    true,           // ATTRIBUTE;
                    true,           // CHILD;
                    false,          // DESCENDANT;
                    false,          // DESCENDANT_OR_SELF;
                    false,          // FOLLOWING;
                    true,           // FOLLOWING_SIBLING;
                    true,           // NAMESPACE;
                    true,           // PARENT;
                    false,          // PRECEDING;
                    true,           // PRECEDING_SIBLING;
                    true,           // SELF;
                    false,          // PRECEDING_OR_ANCESTOR;
            };

    /**
     * Table indicating for each axis whether it is contained within the subtree
     * rooted at the origin node.
     */

    public static final boolean[] isSubtreeAxis =
            {
                    false,          // ANCESTOR
                    false,          // ANCESTOR_OR_SELF;
                    true,           // ATTRIBUTE;
                    true,           // CHILD;
                    true,           // DESCENDANT;
                    true,           // DESCENDANT_OR_SELF;
                    false,          // FOLLOWING;
                    false,          // FOLLOWING_SIBLING;
                    true,           // NAMESPACE;
                    false,          // PARENT;
                    false,          // PRECEDING;
                    false,          // PRECEDING_SIBLING;
                    true,           // SELF;
                    false,          // PRECEDING_OR_ANCESTOR;
            };

    /**
     * Table giving the name of each axis as used in XPath, for example "ancestor-or-self"
     */

    public static final String[] axisName =
            {
                    "ancestor",             // ANCESTOR
                    "ancestor-or-self",     // ANCESTOR_OR_SELF;
                    "attribute",            // ATTRIBUTE;
                    "child",                // CHILD;
                    "descendant",           // DESCENDANT;
                    "descendant-or-self",   // DESCENDANT_OR_SELF;
                    "following",            // FOLLOWING;
                    "following-sibling",    // FOLLOWING_SIBLING;
                    "namespace",            // NAMESPACE;
                    "parent",               // PARENT;
                    "preceding",            // PRECEDING;
                    "preceding-sibling",    // PRECEDING_SIBLING;
                    "self",                 // SELF;
                    "preceding-or-ancestor",// PRECEDING_OR_ANCESTOR;
            };

    /**
     * The class is never instantiated
     */

    private AxisInfo() {
    }

    /**
     * Resolve an axis name into a symbolic constant representing the axis
     *
     * @param name the name of the axis
     * @return integer value representing the named axis
     * @throws XPathException if the axis name is not one of the defined axes
     */

    public static int getAxisNumber(String name) throws XPathException {
        switch (name) {
            case "ancestor":
                return ANCESTOR;
            case "ancestor-or-self":
                return ANCESTOR_OR_SELF;
            case "attribute":
                return ATTRIBUTE;
            case "child":
                return CHILD;
            case "descendant":
                return DESCENDANT;
            case "descendant-or-self":
                return DESCENDANT_OR_SELF;
            case "following":
                return FOLLOWING;
            case "following-sibling":
                return FOLLOWING_SIBLING;
            case "namespace":
                return NAMESPACE;
            case "parent":
                return PARENT;
            case "preceding":
                return PRECEDING;
            case "preceding-sibling":
                return PRECEDING_SIBLING;
            case "self":
                return SELF;
            case "preceding-or-ancestor":
                return PRECEDING_OR_ANCESTOR;
                // preceding-or-ancestor cannot be used in an XPath expression
            default:
                throw new XPathException("Unknown axis name: " + name);
        }

    }

    /**
     * The following table indicates the combinations of axis and origin node-kind that always
     * return an empty result.
     */

    private static final int DOC = 1 << Type.DOCUMENT;
    private static final int ELE = 1 << Type.ELEMENT;
    private static final int ATT = 1 << Type.ATTRIBUTE;
    private static final int TEX = 1 << Type.TEXT;
    private static final int PIN = 1 << Type.PROCESSING_INSTRUCTION;
    private static final int COM = 1 << Type.COMMENT;
    private static final int NAM = 1 << Type.NAMESPACE;

    private static int[] voidAxisTable = {
            DOC,                                // ANCESTOR
            0,                                  // ANCESTOR_OR_SELF;
            DOC | ATT | TEX | PIN | COM | NAM,  // ATTRIBUTE;
            ATT | TEX | PIN | COM | NAM,        // CHILD;
            ATT | TEX | PIN | COM | NAM,        // DESCENDANT;
            0,                                  // DESCENDANT_OR_SELF;
            DOC,                                // FOLLOWING;
            DOC | ATT | NAM,                    // FOLLOWING_SIBLING;
            DOC | ATT | TEX | PIN | COM | NAM,  // NAMESPACE;
            DOC,                                // PARENT;
            DOC,                                // PRECEDING;
            DOC | ATT | NAM,                    // PRECEDING_SIBLING;
            0,                                  // SELF;
    };

    /**
     * Ask whether a given axis can contain any nodes when starting at the specified node kind.
     * For example, the attribute axis when starting at an attribute node will always be empty
     *
     * @param axis     the axis, for example {@link AxisInfo#ATTRIBUTE}
     * @param nodeKind the node kind of the origin node, for example {@link Type#ATTRIBUTE}
     * @return true if no nodes will ever appear on the specified axis when starting at the specified
     *         node kind.
     */

    public static boolean isAlwaysEmpty(int axis, int nodeKind) {
        return (voidAxisTable[axis] & (1 << nodeKind)) != 0;
    }

    /**
     * The following table indicates the kinds of node found on each axis
     */

    private static int[] nodeKindTable = {
            DOC | ELE,                                 // ANCESTOR
            DOC | ELE | ATT | TEX | PIN | COM | NAM,   // ANCESTOR_OR_SELF;
            ATT,                                       // ATTRIBUTE;
            ELE | TEX | PIN | COM,                     // CHILD;
            ELE | TEX | PIN | COM,                     // DESCENDANT;
            DOC | ELE | ATT | TEX | PIN | COM | NAM,   // DESCENDANT_OR_SELF;
            ELE | TEX | PIN | COM,                     // FOLLOWING;
            ELE | TEX | PIN | COM,                     // FOLLOWING_SIBLING;
            NAM,                                       // NAMESPACE;
            DOC | ELE,                                 // PARENT;
            ELE | TEX | PIN | COM,                     // PRECEDING;
            ELE | TEX | PIN | COM,                     // PRECEDING_SIBLING;
            DOC | ELE | ATT | TEX | PIN | COM | NAM,   // SELF;
    };

    /**
     * Determine whether a given kind of node can be found on a given axis. For example,
     * the attribute axis will never contain any element nodes.
     *
     * @param axis     the axis, for example {@link AxisInfo#ATTRIBUTE}
     * @param nodeKind the node kind of the origin node, for example {@link Type#ELEMENT}
     * @return true if the given kind of node can appear on the specified axis
     */

    public static boolean containsNodeKind(int axis, int nodeKind) {
        return nodeKind == Type.NODE || (nodeKindTable[axis] & (1 << nodeKind)) != 0;
    }

    /**
     * For each axis, determine the inverse axis, in the sense that if A is on axis X starting at B,
     * the B is on the axis inverseAxis[X] starting at A. This doesn't quite work for the PARENT axis,
     * which has no simple inverse: this table gives the inverse as CHILD
     */

    public static int[] inverseAxis = {
            DESCENDANT,             //        ANCESTOR
            DESCENDANT_OR_SELF,     //        ANCESTOR_OR_SELF;
            PARENT,                 //        ATTRIBUTE;
            PARENT,                 //        CHILD;
            ANCESTOR,               //        DESCENDANT;
            ANCESTOR_OR_SELF,       //        DESCENDANT_OR_SELF;
            PRECEDING,              //        FOLLOWING;
            PRECEDING_SIBLING,      //        FOLLOWING_SIBLING;
            PARENT,                 //        NAMESPACE;
            CHILD,                  //        PARENT;
            FOLLOWING,              //        PRECEDING;
            FOLLOWING_SIBLING,      //        PRECEDING_SIBLING;
            SELF                    //        SELF;
    };

    /**
     * Give the corresponding axis if the self node is excluded. (Doesn't really
     * work for the self axis itself).
     */

    public static int[] excludeSelfAxis = {
            ANCESTOR,              //        ANCESTOR
            ANCESTOR,              //        ANCESTOR_OR_SELF;
            ATTRIBUTE,             //        ATTRIBUTE;
            CHILD,                 //        CHILD;
            DESCENDANT,            //        DESCENDANT;
            DESCENDANT,            //        DESCENDANT_OR_SELF;
            FOLLOWING,             //        FOLLOWING;
            FOLLOWING_SIBLING,     //        FOLLOWING_SIBLING;
            NAMESPACE,             //        NAMESPACE;
            PARENT,                //        PARENT;
            PRECEDING,             //        PRECEDING;
            PRECEDING_SIBLING,     //        PRECEDING_SIBLING;
            SELF                   //        SELF;
    };


    private static IntHashMap<UType> axisTransitions = new IntHashMap<>(50);

    private static void e(PrimitiveUType origin, int axis, UType target) {
        axisTransitions.put(makeKey(origin, axis), target);
    }

    private static int makeKey(PrimitiveUType origin, int axis) {
        return origin.getBit()<<16 | axis;
    }

    /**
     * Given a context item type and an axis, determine the kinds of nodes that can be returned
     * @param origin the context item type, as a UType representing one or more node kinds
     * @param axis identifies the axis
     * @return the set of possible node kinds in the result of the axis expression, as a UType
     */

    public static UType getTargetUType(UType origin, int axis) {
        UType resultType = UType.VOID;
        Set<PrimitiveUType> origins = origin.intersection(UType.ANY_NODE).decompose();
        for (PrimitiveUType u : origins) {
            UType r = axisTransitions.get(makeKey(u, axis));
            if (r == null) {
                System.err.println("Unknown transitions for primitive type " + u.toString() + "::" + axis);
            }
            resultType = resultType.union(r);
        }
        return resultType;
    }

    static {

        // Declare as triples the relationships that can exist between nodes. The first argument is the type
        // of the origin node; the second is the axis; the third is the set of node kinds that can be found
        // using this axis, when starting from this origin.

        e(PrimitiveUType.DOCUMENT, ANCESTOR, UType.VOID);
        e(PrimitiveUType.DOCUMENT, ANCESTOR_OR_SELF, UType.DOCUMENT);
        e(PrimitiveUType.DOCUMENT, ATTRIBUTE, UType.VOID);
        e(PrimitiveUType.DOCUMENT, CHILD, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.DOCUMENT, DESCENDANT, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.DOCUMENT, DESCENDANT_OR_SELF, UType.DOCUMENT.union(UType.CHILD_NODE_KINDS));
        e(PrimitiveUType.DOCUMENT, FOLLOWING, UType.VOID);
        e(PrimitiveUType.DOCUMENT, FOLLOWING_SIBLING, UType.VOID);
        e(PrimitiveUType.DOCUMENT, NAMESPACE, UType.VOID);
        e(PrimitiveUType.DOCUMENT, PARENT, UType.VOID);
        e(PrimitiveUType.DOCUMENT, PRECEDING, UType.VOID);
        e(PrimitiveUType.DOCUMENT, PRECEDING_SIBLING, UType.VOID);
        e(PrimitiveUType.DOCUMENT, SELF, UType.DOCUMENT);

        e(PrimitiveUType.ELEMENT, ANCESTOR, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, ANCESTOR_OR_SELF, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, ATTRIBUTE, UType.ATTRIBUTE);
        e(PrimitiveUType.ELEMENT, CHILD, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, DESCENDANT, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, DESCENDANT_OR_SELF, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, FOLLOWING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, FOLLOWING_SIBLING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, NAMESPACE, UType.NAMESPACE);
        e(PrimitiveUType.ELEMENT, PARENT, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, PRECEDING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, PRECEDING_SIBLING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.ELEMENT, SELF, UType.ELEMENT);

        e(PrimitiveUType.ATTRIBUTE, ANCESTOR, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.ATTRIBUTE, ANCESTOR_OR_SELF, UType.ATTRIBUTE.union(UType.PARENT_NODE_KINDS));
        e(PrimitiveUType.ATTRIBUTE, ATTRIBUTE, UType.VOID);
        e(PrimitiveUType.ATTRIBUTE, CHILD, UType.VOID);
        e(PrimitiveUType.ATTRIBUTE, DESCENDANT, UType.VOID);
        e(PrimitiveUType.ATTRIBUTE, DESCENDANT_OR_SELF, UType.ATTRIBUTE);
        e(PrimitiveUType.ATTRIBUTE, FOLLOWING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.ATTRIBUTE, FOLLOWING_SIBLING, UType.VOID);
        e(PrimitiveUType.ATTRIBUTE, NAMESPACE, UType.VOID);
        e(PrimitiveUType.ATTRIBUTE, PARENT, UType.ELEMENT);
        e(PrimitiveUType.ATTRIBUTE, PRECEDING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.ATTRIBUTE, PRECEDING_SIBLING, UType.VOID);
        e(PrimitiveUType.ATTRIBUTE, SELF, UType.ATTRIBUTE);

        e(PrimitiveUType.TEXT, ANCESTOR, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.TEXT, ANCESTOR_OR_SELF, UType.TEXT.union(UType.PARENT_NODE_KINDS));
        e(PrimitiveUType.TEXT, ATTRIBUTE, UType.VOID);
        e(PrimitiveUType.TEXT, CHILD, UType.VOID);
        e(PrimitiveUType.TEXT, DESCENDANT, UType.VOID);
        e(PrimitiveUType.TEXT, DESCENDANT_OR_SELF, UType.TEXT);
        e(PrimitiveUType.TEXT, FOLLOWING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.TEXT, FOLLOWING_SIBLING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.TEXT, NAMESPACE, UType.VOID);
        e(PrimitiveUType.TEXT, PARENT, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.TEXT, PRECEDING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.TEXT, PRECEDING_SIBLING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.TEXT, SELF, UType.TEXT);

        e(PrimitiveUType.PI, ANCESTOR, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.PI, ANCESTOR_OR_SELF, UType.PI.union(UType.PARENT_NODE_KINDS));
        e(PrimitiveUType.PI, ATTRIBUTE, UType.VOID);
        e(PrimitiveUType.PI, CHILD, UType.VOID);
        e(PrimitiveUType.PI, DESCENDANT, UType.VOID);
        e(PrimitiveUType.PI, DESCENDANT_OR_SELF, UType.PI);
        e(PrimitiveUType.PI, FOLLOWING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.PI, FOLLOWING_SIBLING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.PI, NAMESPACE, UType.VOID);
        e(PrimitiveUType.PI, PARENT, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.PI, PRECEDING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.PI, PRECEDING_SIBLING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.PI, SELF, UType.PI);

        e(PrimitiveUType.COMMENT, ANCESTOR, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.COMMENT, ANCESTOR_OR_SELF, UType.COMMENT.union(UType.PARENT_NODE_KINDS));
        e(PrimitiveUType.COMMENT, ATTRIBUTE, UType.VOID);
        e(PrimitiveUType.COMMENT, CHILD, UType.VOID);
        e(PrimitiveUType.COMMENT, DESCENDANT, UType.VOID);
        e(PrimitiveUType.COMMENT, DESCENDANT_OR_SELF, UType.COMMENT);
        e(PrimitiveUType.COMMENT, FOLLOWING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.COMMENT, FOLLOWING_SIBLING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.COMMENT, NAMESPACE, UType.VOID);
        e(PrimitiveUType.COMMENT, PARENT, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.COMMENT, PRECEDING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.COMMENT, PRECEDING_SIBLING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.COMMENT, SELF, UType.COMMENT);

        e(PrimitiveUType.NAMESPACE, ANCESTOR, UType.PARENT_NODE_KINDS);
        e(PrimitiveUType.NAMESPACE, ANCESTOR_OR_SELF, UType.NAMESPACE.union(UType.PARENT_NODE_KINDS));
        e(PrimitiveUType.NAMESPACE, ATTRIBUTE, UType.VOID);
        e(PrimitiveUType.NAMESPACE, CHILD, UType.VOID);
        e(PrimitiveUType.NAMESPACE, DESCENDANT, UType.VOID);
        e(PrimitiveUType.NAMESPACE, DESCENDANT_OR_SELF, UType.NAMESPACE);
        e(PrimitiveUType.NAMESPACE, FOLLOWING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.NAMESPACE, FOLLOWING_SIBLING, UType.VOID);
        e(PrimitiveUType.NAMESPACE, NAMESPACE, UType.VOID);
        e(PrimitiveUType.NAMESPACE, PARENT, UType.ELEMENT);
        e(PrimitiveUType.NAMESPACE, PRECEDING, UType.CHILD_NODE_KINDS);
        e(PrimitiveUType.NAMESPACE, PRECEDING_SIBLING, UType.VOID);
        e(PrimitiveUType.NAMESPACE, SELF, UType.NAMESPACE);
    }

}

/*
    // a list for any future cut-and-pasting...
    ANCESTOR
    ANCESTOR_OR_SELF;
    ATTRIBUTE;
    CHILD;
    DESCENDANT;
    DESCENDANT_OR_SELF;
    FOLLOWING;
    FOLLOWING_SIBLING;
    NAMESPACE;
    PARENT;
    PRECEDING;
    PRECEDING_SIBLING;
    SELF;
*/
