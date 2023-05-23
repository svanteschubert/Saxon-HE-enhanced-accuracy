////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.ContextOriginator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Actor;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.UType;

import java.util.Map;

/**
 * Corresponds to a single xsl:key declaration.
 */

public class KeyDefinition extends Actor implements ContextOriginator {

    private SymbolicName symbolicName;
    private Pattern match;          // the match pattern
    private BuiltInAtomicType useType;    // the type of the values returned by the atomized use expression
    private StringCollator collation;     // the collating sequence, when type=string
    private String collationName;         // the collation URI
    private boolean backwardsCompatible = false;
    private boolean strictComparison = false;
    private boolean convertUntypedToOther = false;
    private boolean rangeKey = false;
    private boolean composite = false;

    /**
     * Constructor to create a key definition
     *
     * @param match         the pattern in the xsl:key match attribute
     * @param use           the expression in the xsl:key use attribute, or the expression that results from compiling
     *                      the xsl:key contained instructions.
     * @param collationName the name of the collation being used
     * @param collation     the actual collation. This must be one that supports generation of collation keys.
     */

    public KeyDefinition(SymbolicName symbolicName, Pattern match, Expression use, String collationName, StringCollator collation) {
        this.symbolicName = symbolicName;
        this.match = match;
        setBody(use);
        this.collation = collation;
        this.collationName = collationName;
    }

    /**
     * Get the symbolic name of the component
     *
     * @return the symbolic name
     */
    @Override
    public SymbolicName getSymbolicName() {
        return symbolicName;
    }

    /**
     * Say whether this key is a range key, that is, a key capable of returning
     * (a) the values in a selected range, and (b) the keys in order
     *
     * @param rangeKey true if this is a range key; false if not
     */

    public void setRangeKey(boolean rangeKey) {
        this.rangeKey = rangeKey;
    }

    /**
     * Ask whether this key is a range key, that is, a key capable of returning
     * (a) the values in a selected range, and (b) the keys in order
     *
     * @return true if this is a range key; false if not
     */

    public boolean isRangeKey() {
        return rangeKey;
    }

    /**
     * Say whether this is a composite key. (If so, a sequence of atomic values in the use expression
     * is taken as a single key value, rather than as a set of independent key values)
     * @param composite true if this is a composite key
     */

    public void setComposite(boolean composite) {
        this.composite = composite;
    }

    /**
     * Ask whether this is a composite key
     * @return true if the key is composite
     */

    public boolean isComposite() {
        return composite;
    }

    /**
     * Set the primitive item type of the values returned by the use expression
     *
     * @param itemType the primitive type of the indexed values
     */

    public void setIndexedItemType(BuiltInAtomicType itemType) {
        useType = itemType;
    }

    /**
     * Get the primitive item type of the values returned by the use expression
     *
     * @return the primitive item type of the indexed values
     */

    public BuiltInAtomicType getIndexedItemType() {
        if (useType == null) {
            return BuiltInAtomicType.ANY_ATOMIC;
        } else {
            return useType;
        }
    }

    /**
     * Set backwards compatibility mode. The key definition is backwards compatible if ANY of the xsl:key
     * declarations has version="1.0" in scope.
     *
     * @param bc set to true if running in XSLT 2.0 backwards compatibility mode
     */

    public void setBackwardsCompatible(boolean bc) {
        backwardsCompatible = bc;
    }

    /**
     * Test backwards compatibility mode
     *
     * @return true if running in XSLT backwards compatibility mode
     */

    public boolean isBackwardsCompatible() {
        return backwardsCompatible;
    }

    /**
     * Set whether strict comparison is needed. Strict comparison treats non-comparable values as an
     * error rather than a no-match. This is used for internal keys that support value comparisons in
     * Saxon-EE, it is not used for user-defined XSLT keys.
     *
     * @param strict true if strict comparison is needed
     */

    public void setStrictComparison(boolean strict) {
        strictComparison = strict;
    }

    /**
     * Ask whether strict comparison is needed. Strict comparison treats non-comparable values as an
     * error rather than a no-match. This is used for internal keys that support value comparisons in
     * Saxon-EE, it is not used for user-defined XSLT keys.
     *
     * @return true if strict comparison is needed.
     */

    public boolean isStrictComparison() {
        return strictComparison;
    }

    /**
     * Indicate that untypedAtomic values should be converted to the type of the other operand,
     * rather than to strings. This is used for indexes constructed internally by Saxon-EE to
     * support filter expressions that use the "=" operator, as distinct from "eq".
     *
     * @param convertToOther true if comparisons follow the semantics of the "=" operator rather than
     *                       the "eq" operator
     */

    public void setConvertUntypedToOther(boolean convertToOther) {
        convertUntypedToOther = convertToOther;
    }

    /**
     * Determine whether untypedAtomic values are converted to the type of the other operand.
     *
     * @return true if comparisons follow the semantics of the "=" operator rather than
     *         the "eq" operator
     */

    public boolean isConvertUntypedToOther() {
        return convertUntypedToOther;
    }

    /**
     * Set the map of local variables needed while evaluating the "use" expression
     */

    @Override
    public void setStackFrameMap(SlotManager map) {
        if (map != null /*&& map.getNumberOfVariables() > 0 */) {
            super.setStackFrameMap(map);
        }
    }

    @Override
    public void allocateAllBindingSlots(StylesheetPackage pack) {
        super.allocateAllBindingSlots(pack);
        allocateBindingSlotsRecursive(pack, this, match, getDeclaringComponent().getComponentBindings());
    }

    /**
     * Set the system Id and line number of the source xsl:key definition
     *
     * @param systemId   the URI of the module containing the key definition
     * @param lineNumber the line number of the key definition
     * @param columnNumber the column number of the key definition
     */

    public void setLocation(String systemId, int lineNumber, int columnNumber) {
        setSystemId(systemId);
        setLineNumber(lineNumber);
        setColumnNumber(columnNumber);
    }

    /**
     * Get the match pattern for the key definition
     *
     * @return the pattern specified in the "match" attribute of the xsl:key declaration
     */

    public Pattern getMatch() {
        return match;
    }

    /**
     * Get the use expression for the key definition
     *
     * @return the expression specified in the "use" attribute of the xsl:key declaration
     */

    public Expression getUse() {
        return getBody();
    }

    /**
     * Get the collation name for this key definition.
     *
     * @return the collation name (the collation URI)
     */

    public String getCollationName() {
        return collationName;
    }

    /**
     * Get the collation.
     *
     * @return the collation
     */

    public StringCollator getCollation() {
        return collation;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     */

    /*@Nullable*/
    public StructuredQName getObjectName() {
        return symbolicName.getComponentName();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param out the expression presenter used to display the structure
     */

    public void export(ExpressionPresenter out, boolean reusable, Map<Component, Integer> componentIdMap) throws XPathException {
        out.startElement("key");
        out.emitAttribute("name", getObjectName());
        if (!NamespaceConstant.CODEPOINT_COLLATION_URI.equals(collationName)) {
            out.emitAttribute("collation", collationName);
        }
        out.emitAttribute("line", getLineNumber() + "");
        out.emitAttribute("module", getSystemId());
        if (getStackFrameMap() != null && getStackFrameMap().getNumberOfVariables() != 0) {
            out.emitAttribute("slots", getStackFrameMap().getNumberOfVariables() + "");
        }
        if (componentIdMap != null) {
            out.emitAttribute("binds", "" + getDeclaringComponent().listComponentReferences(componentIdMap));
        }
        String flags = "";
        if (backwardsCompatible) {
            flags += "b";
        }
        if (isRangeKey()) {
            flags += "r";
            out.emitAttribute("range", "1");
        }
        if (match.getUType().overlaps(UType.ATTRIBUTE)) {
            flags += "a";
        }
        if (match.getUType().overlaps(UType.NAMESPACE)) {
            flags += "n";
        }
        if (composite) {
            flags += "c";
        }
        if (convertUntypedToOther) {
            flags += "v";
        }
        if (strictComparison) {
            flags += "s";
        }
        if (reusable) {
            flags += "u";
        }
        if (!"".equals(flags)) {
            out.emitAttribute("flags", flags);
        }
        getMatch().export(out);
        getBody().export(out);
        out.endElement();
    }

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        throw new UnsupportedOperationException();
    }
}

