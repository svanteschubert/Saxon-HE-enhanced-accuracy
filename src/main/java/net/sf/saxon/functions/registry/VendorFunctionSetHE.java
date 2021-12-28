////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.ApplyFn;
import net.sf.saxon.functions.Doc_2;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapCreate;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.ma.map.MapUntypedContains;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import javax.xml.transform.SourceLocator;


/**
 * Implementation of vendor functions in the Saxon namespace, available in Saxon-HE because they
 * are used internally. This library is available in all Saxon versions.
 */
public class VendorFunctionSetHE extends BuiltInFunctionSet {

    private final static VendorFunctionSetHE THE_INSTANCE = new VendorFunctionSetHE();

    public static VendorFunctionSetHE getInstance() {
        return THE_INSTANCE;
    }

    private VendorFunctionSetHE() {
        init();
    }

    private void init() {

        // Test whether supplied argument is equal to an integer
        register("is-whole-number", 1, IsWholeNumberFn.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY);

        // Evaluate the value of a try-catch variable such as $err:code
        register("dynamic-error-info", 1, DynamicErrorInfoFn.class, AnyItemType.getInstance(), STAR, FOCUS | LATE | SIDE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        // saxon:apply is the same as fn:apply, but does not require the HOF feature
        register("apply", 2, ApplyFn.class, AnyItemType.getInstance(), STAR, LATE)
                .arg(0, AnyFunctionType.getInstance(), ONE, null)
                .arg(1, ArrayItemType.ANY_ARRAY_TYPE, ONE, null);

        // Create a map according to the semantics of the XPath map constructor and XSLT xsl:map instruction
        register("create-map", 1, MapCreate.class, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR, null);

        // Variant of the doc() function with an options parameter
        register("doc", 2, Doc_2.class, NodeKindTest.DOCUMENT, ONE, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, EMPTY)
                .optionDetails(Doc_2.makeOptionsParameter());

        // Ask whether the supplied element node has any local namespace declarations
        register("has-local-namespaces", 1, HasLocalNamespaces.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, NodeKindTest.ELEMENT, ONE, null);

        // Ask whether the supplied element node has consistent in scope namespaces throughout its subtree
        register("has-uniform-namespaces", 1, HasUniformNamespaces.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, NodeKindTest.ELEMENT, ONE, null);

        // Function analogous to map:contains except in the way it handles untyped key values
        register("map-untyped-contains", 2, MapUntypedContains.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE, null);

    }

    @Override
    public String getNamespace() {
        return NamespaceConstant.SAXON;
    }

    @Override
    public String getConventionalPrefix() {
        return "saxon";
    }

    /**
     * Implement saxon:is-whole-number
     */

    public static class IsWholeNumberFn extends SystemFunction {
        /**
         * Allow the function to create an optimized call based on the values of the actual arguments
         *
         * @param visitor     the expression visitor
         * @param contextInfo information about the context item
         * @param arguments   the supplied arguments to the function call. Note: modifying the contents
         *                    of this array should not be attempted, it is likely to have no effect.
         * @return either a function call on this function, or an expression that delivers
         * the same result, or null indicating that no optimization has taken place
         * @throws XPathException if an error is detected
         */
        @Override
        public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
            if (arguments[0].getItemType().getPrimitiveItemType() == BuiltInAtomicType.INTEGER) {
                return Literal.makeLiteral(BooleanValue.TRUE);
            }
            return null;
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            NumericValue val = (NumericValue) arguments[0].head();
            return BooleanValue.get(val != null && val.isWholeNumber());
        }

    }

    /**
     * Implement saxon:has-local-namespaces. The function takes an element node as input and returns
     * true if (a) the element is parentless, or (b) the parent is a document node, or (c) the
     * element has namespace declarations or undeclarations that differ from those of the parent
     * element (that is, if its in-scope namespace bindings are different from those of the parent
     * element).
     *
     * <p>This function is provided for use by the XSLT-compiler-in-XSLT, so that it can decide
     * efficiently whether to generate an "ns" element containing namespace bindings in the SEF file.</p>
     */

    public static class HasLocalNamespaces extends SystemFunction {

        // TODO: this is experimental and not yet publicly documented

        @Override
        public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
            NodeInfo child = (NodeInfo) arguments[0].head();
            NodeInfo parent = child.getParent();
            return BooleanValue.get(
                    parent == null || parent.getNodeKind() == Type.DOCUMENT ||
                    child.getAllNamespaces() != parent.getAllNamespaces());
        }

    }

    /**
     * Implement saxon:has-uniform-namespaces. The function takes an element node as input and returns
     * true if it can be guaranteed that all descendant elements have the same namespace context as
     * the target element. (If the result is false, there may or may not be descendant elements
     * with new namespace declarations or undeclarations.)
     *
     * <p>This function is provided for use by the XSLT-compiler-in-XSLT, so that it can decide
     * efficiently whether to generate an "ns" element containing namespace bindings in the SEF file.</p>
     *
     * <p>Currently returns false for any non-TinyTree element.</p>
     */

    public static class HasUniformNamespaces extends SystemFunction {

        // TODO: this is experimental and not yet publicly documented

        @Override
        public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
            NodeInfo val = (NodeInfo) arguments[0].head();
            if (val instanceof TinyElementImpl) {
                return BooleanValue.get(((TinyElementImpl)val).hasUniformNamespaces());
            } else {
                return BooleanValue.FALSE;
            }
        }

    }


    /**
     * Implement saxon:dynamic-error-info
     */

    public static class DynamicErrorInfoFn extends SystemFunction {

        /**
         * Determine the special properties of this function. The general rule
         * is that a system function call is non-creative unless more details
         * are defined in a subclass.
         *
         * @param arguments the actual arguments supplied in a call to the function
         */
        @Override
        public int getSpecialProperties(Expression[] arguments) {
            return 0; // treat as creative to avoid loop-lifting: test case try-catch-err-code-variable-14
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            String var = arguments[0].head().getStringValue();
            @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
            XPathException error = context.getCurrentException();
            if (error == null) {
                return EmptySequence.getInstance();
            }
            SourceLocator locator = error.getLocator();
            switch (var) {
                case "code":
                    StructuredQName errorCodeQName = error.getErrorCodeQName();
                    if (errorCodeQName == null) {
                        errorCodeQName = new StructuredQName("saxon", NamespaceConstant.SAXON, "XXXX9999");
                    }
                    return new QNameValue(errorCodeQName, BuiltInAtomicType.QNAME);
                case "description":
                    String s = error.getMessage();
                    if (error.getCause() != null) {
                        s += "(" + error.getCause().getMessage() + ")";
                    }
                    return new StringValue(s);
                case "value":
                    Sequence value = error.getErrorObject();
                    if (value == null) {
                        return EmptySequence.getInstance();
                    } else {
                        return value;
                    }
                case "module":
                    String module = locator == null ? null : locator.getSystemId();
                    if (module == null) {
                        return EmptySequence.getInstance();
                    } else {
                        return new StringValue(module);
                    }
                case "line-number":
                    int line = locator == null ? -1 : locator.getLineNumber();
                    if (line == -1) {
                        return EmptySequence.getInstance();
                    } else {
                        return new Int64Value(line);
                    }
                case "column-number":
                    // Bug 4144
                    int column = -1;
                    if (locator == null) {
                        return EmptySequence.getInstance();
                    } else if (locator instanceof XPathParser.NestedLocation) {
                        column = ((XPathParser.NestedLocation) locator).getContainingLocation().getColumnNumber();
                    } else {
                        column = locator.getColumnNumber();
                    }
                    if (column == -1) {
                        return EmptySequence.getInstance();
                    } else {
                        return new Int64Value(column);
                    }
                default:
                    return EmptySequence.getInstance();
            }

        }
    }



}

// Copyright (c) 2018-2020 Saxonica Limited
