////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.CardinalityChecker;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.ItemChecker;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.TransformFn;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.tree.AttributeLocation;

/**
 * A code injector that wraps every expression (other than a literal) in a TraceExpression, which causes
 * a TraceListener to be notified when the expression is evaluated
 */
public class XSLTTraceCodeInjector extends TraceCodeInjector {

    @Override
    protected boolean isApplicable(Expression exp) {
        if (traceLevel == TraceLevel.HIGH) {
            return !(exp instanceof TraceExpression || exp instanceof OnEmptyExpr || exp instanceof OnNonEmptyExpr);
        }
        return isTraceableExpression(exp);
    }

    /**
     * Decide whether a particular expression should be traced when tracing XSLT stylesheet execution.
     */
    public static boolean isTraceableExpression(Expression exp) {
        // Never trace an empty sequence (it's often an empty sequence constructor, for example <lre/>
        if (Literal.isEmptySequence(exp)) {
            return false;
        }
        // Don't trace an on-empty or on-non-empty expression (bug #6428)
        if (exp instanceof OnEmptyExpr || exp instanceof OnNonEmptyExpr) {
            return false;
        }
        // Don't trace an expression if its parent is an XPath expression (as distinct from an XSLT instruction)
        Expression parent = exp.getParentExpression();
        if (parent instanceof TraceExpression) {
            parent = parent.getParentExpression();
        }
        if (exp.isCallOn(TransformFn.class)) {
            return true;
        }
        if (exp.isInstruction()) {
            return true;
        }
        if (parent != null && parent.getLocation() instanceof XPathParser.NestedLocation) {
            return false;
        }
        // Do trace an expression if it's the direct content of an `xsl:sequence` instruction (which doesn't
        // actually appear on the expression tree in its own right)
        Location loc = exp.getLocation();
        if (loc instanceof XPathParser.NestedLocation) {
            loc = ((XPathParser.NestedLocation) loc).getContainingLocation();
        }
        if (loc instanceof AttributeLocation) {
            StructuredQName elementName = ((AttributeLocation) loc).getElementName();
            return elementName.hasURI(NamespaceUri.XSLT) && elementName.getLocalPart().equals("sequence");
        }
        // Otherwise trace the expression if it has a known location which differs from the parent expression
        // except in the case where the parent expression is a sequence constructor (`Block`)
        // or where it is a type-checking instruction
        return loc != null
                && loc.getLineNumber() != -1
                && !(parent != null && loc == parent.getLocation()
                             && !(parent instanceof Block
                                          || parent instanceof ComponentTracer
                                          || parent instanceof ItemChecker
                                          || parent instanceof CardinalityChecker
        ));
    }
   
}

