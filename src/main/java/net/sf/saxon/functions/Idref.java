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
import net.sf.saxon.trans.KeyDefinitionSet;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;


public class Idref extends SystemFunction {

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-significant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     * @param arguments the actual arguments to the function call
     */

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        int prop = StaticProperty.ORDERED_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NO_NODES_NEWLY_CREATED;
        if ((getArity() == 1) ||
                arguments[1].hasSpecialProperty(StaticProperty.CONTEXT_DOCUMENT_NODESET)) {
            prop |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        return prop;
    }

    /**
     * Get the result when multiple idref values are supplied. Note this is also called from
     * compiled XQuery code.
     *
     * @param doc     the document to be searched
     * @param keys    the idref values supplied
     * @param context the dynamic execution context
     * @return iterator over the result of the function
     * @throws XPathException if a dynamic error occurs
     */

    public static SequenceIterator getIdrefMultiple(TreeInfo doc, SequenceIterator keys, XPathContext context)
            throws XPathException {
        IdrefMappingFunction map = new IdrefMappingFunction();
        map.document = doc;
        map.keyContext = context;
        map.keyManager = context.getController().getExecutable().getTopLevelPackage().getKeyManager();
        map.keySet = map.keyManager.getKeyDefinitionSet(StandardNames.getStructuredQName(StandardNames.XS_IDREFS));
        SequenceIterator allValues = new MappingIterator(keys, map);
        return new DocumentOrderIterator(allValues, LocalOrderComparer.getInstance());
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
    public ZeroOrMore<NodeInfo> call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo start = arguments.length == 1 ? getContextNode(context) : (NodeInfo) arguments[1].head();
        NodeInfo arg2 = start.getRoot();
        if (arg2.getNodeKind() != Type.DOCUMENT) {
            throw new XPathException("In the idref() function," +
                    " the tree being searched must be one whose root is a document node", "FODC0001", context);
        }
        return new ZeroOrMore<>(getIdrefMultiple(arg2.getTreeInfo(), arguments[0].iterate(), context));
    }

    private static class IdrefMappingFunction implements MappingFunction {
        public TreeInfo document;
        public XPathContext keyContext;
        public KeyManager keyManager;
        public KeyDefinitionSet keySet;

        /**
         * Implement the MappingFunction interface
         */

        @Override
        public SequenceIterator map(Item item) throws XPathException {
            return keyManager.selectByKey(keySet, document, (StringValue)item, keyContext);
        }
    }

}

