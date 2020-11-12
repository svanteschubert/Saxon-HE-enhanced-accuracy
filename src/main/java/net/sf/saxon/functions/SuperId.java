////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.sort.DocumentOrderIterator;
import net.sf.saxon.expr.sort.LocalOrderComparer;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Whitespace;


/**
 * The XPath id() or element-with-id() function
 * XPath 2.0 version: accepts any sequence as the first parameter; each item in the sequence
 * is taken as an IDREFS value, that is, a space-separated list of ID values.
 * Also accepts an optional second argument to identify the target document, this
 * defaults to the context node.
 */


public abstract class SuperId extends SystemFunction {

    public static final int ID = 0;
    public static final int ELEMENT_WITH_ID = 1;

    public abstract int getOp();

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     * @param arguments the actual arguments to the call
     */

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        int prop = StaticProperty.ORDERED_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NO_NODES_NEWLY_CREATED;
        if ((getArity() == 1) ||
                (arguments[1].getSpecialProperties() & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) {
            prop |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        return prop;
    }

    /**
     * Get an iterator over the nodes that have an id equal to one of the values is a whitespace separated
     * string
     *
     * @param doc       The document to be searched
     * @param idrefs    a string containing zero or more whitespace-separated ID values to be found in the document
     * @param operation either {@link #ID} or {@link #ELEMENT_WITH_ID}
     * @return an iterator over the nodes whose ID is one of the specified values
     * @throws XPathException if an error occurs
     */

    public static SequenceIterator getIdSingle(TreeInfo doc, String idrefs, int operation) throws XPathException {
        if (Whitespace.containsWhitespace(idrefs)) {
            Whitespace.Tokenizer tokens = new Whitespace.Tokenizer(idrefs);
            IdMappingFunction map = new IdMappingFunction();
            map.document = doc;
            map.operation = operation;
            SequenceIterator result = new MappingIterator(tokens, map);
            return new DocumentOrderIterator(result, LocalOrderComparer.getInstance());
        } else {
            return SingletonIterator.makeIterator(doc.selectID(idrefs, operation == ELEMENT_WITH_ID));
        }
    }

    /**
     * Get an iterator over the nodes that have an id equal to one of the values is a set of whitespace separated
     * strings
     *
     * @param doc    The document to be searched
     * @param idrefs an iterator over a set of strings each of which is a string containing
     *               zero or more whitespace-separated ID values to be found in the document
     * @param operation distinguishes id() and element-with-id()
     * @return an iterator over the nodes whose ID is one of the specified values
     * @throws XPathException if an error occurs
     */

    public static SequenceIterator getIdMultiple(
        TreeInfo doc, SequenceIterator idrefs, int operation) throws XPathException {
        IdMappingFunction map = new IdMappingFunction();
        map.document = doc;
        map.operation = operation;
        SequenceIterator result = new MappingIterator(idrefs, map);
        return new DocumentOrderIterator(result, LocalOrderComparer.getInstance());
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo start = arguments.length == 1 ? getContextNode(context) : (NodeInfo) arguments[1].head();
        NodeInfo arg1 = start.getRoot();
        if (arg1.getNodeKind() != Type.DOCUMENT) {
            throw new XPathException("In the " + getFunctionName().getLocalPart() + "() function," +
                    " the tree being searched must be one whose root is a document node", "FODC0001", context);
        }
        TreeInfo doc = arg1.getTreeInfo();
        SequenceIterator result;
        if (arguments[0] instanceof AtomicValue) {
            result = getIdSingle(doc, ((AtomicValue)arguments[0]).getStringValue(), getOp());
        } else {
            SequenceIterator idrefs = arguments[0].iterate();
            result = getIdMultiple(doc, idrefs, getOp());
        }
        return SequenceTool.toLazySequence(result);
    }

    private static class IdMappingFunction implements MappingFunction {

        public TreeInfo document;
        private int operation;

        /**
         * Evaluate the function for a single string value
         * (implements the MappingFunction interface)
         */

        @Override
        public SequenceIterator map(Item item) {

            String idrefs = Whitespace.trim(item.getStringValueCS());

            // If this value contains a space, we need to break it up into its
            // separate tokens; if not, we can process it directly

            if (Whitespace.containsWhitespace(idrefs)) {
                Whitespace.Tokenizer tokens = new Whitespace.Tokenizer(idrefs);
                IdMappingFunction submap = new IdMappingFunction();
                submap.document = document;
                submap.operation = operation;
                return new MappingIterator(tokens, submap);

            } else {
                return SingletonIterator.makeIterator(document.selectID(idrefs, operation == ELEMENT_WITH_ID));
            }
        }
    }

    public static class Id extends SuperId {
        @Override
        public int getOp() {
            return ID;
        }
    }

    public static class ElementWithId extends SuperId {
        @Override
        public int getOp() {
            return ELEMENT_WITH_ID;
        }
    }



}

