////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LocalBinding;
import net.sf.saxon.expr.VariableReference;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryParser;
import net.sf.saxon.style.SourceBinding;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

/**
 * Dummy Parser extension for syntax in XPath that is accepted only in particular product variants.
 * Originally, this meant XPath 3.0 syntax associated with higher-order functions. It now covers
 * Saxon syntax extensions and XQuery Update.
 */
public class ParserExtension {

    public ParserExtension() {
    }



    public void needExtension(XPathParser p, String what) throws XPathException {
        p.grumble(what + " require support for Saxon extensions, available in Saxon-PE or higher");
    }

    private void needUpdate(XPathParser p, String what) throws XPathException {
        p.grumble(what + " requires support for XQuery Update, available in Saxon-EE or higher");
    }


    public void handleExternalFunctionDeclaration(XQueryParser p, XQueryFunction func) throws XPathException {
        needExtension(p, "External function declarations");
    }

    /**
     * Parse an ItemType within a SequenceType
     *
     * @param p the XPath parser
     * @return the ItemType after parsing
     * @throws XPathException if a static error is found
     */

    public ItemType parseExtendedItemType(XPathParser p) throws XPathException {
        return null;
    }


    /**
     * Parse an extended XSLT pattern in the form ~itemType (predicate)*
     *
     * @param p the XPath parser
     * @return the equivalent expression in the form .[. instance of type] (predicate)*
     * @throws XPathException if a static error is found
     */

    public Expression parseTypePattern(XPathParser p) throws XPathException {
        needExtension(p, "type-based patterns");
        return null;
    }


    public static class TemporaryXSLTVariableBinding implements LocalBinding {
        SourceBinding declaration;

        public TemporaryXSLTVariableBinding(SourceBinding decl) {
            this.declaration = decl;
        }

        @Override
        public SequenceType getRequiredType() {
            return declaration.getInferredType(true);
        }

        @Override
        public Sequence evaluateVariable(XPathContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isGlobal() {
            return false;
        }


        @Override
        public boolean isAssignable() {
            return false;
        }

        @Override
        public int getLocalSlotNumber() {
            return 0;
        }

        @Override
        public StructuredQName getVariableQName() {
            return declaration.getVariableQName();
        }

        @Override
        public void addReference(VariableReference ref, boolean isLoopingReference) {

        }

        @Override
        public IntegerValue[] getIntegerBoundsForVariable() {
            return null;
        }

        @Override
        public void setIndexedVariable() {
        }

        @Override
        public boolean isIndexedVariable() {
            return false;
        }
    }

    /**
     * Parse a type alias declaration. Allowed only in Saxon-PE and higher
     *
     * @param p the XPath parser
     * @throws XPathException if parsing fails
     */

    public void parseItemTypeDeclaration(XQueryParser p) throws XPathException {
        needExtension(p, "Item type declarations");
    }

    /**
     * Parse the "declare revalidation" declaration.
     * Syntax: not allowed unless XQuery update is in use
     *
     * @param p the XPath parser
     * @throws XPathException if the syntax is incorrect, or is not allowed in this XQuery processor
     */

    public void parseRevalidationDeclaration(XQueryParser p) throws XPathException {
        needUpdate(p, "A revalidation declaration");
    }

    /**
     * Parse an updating function declaration (allowed in XQuery Update only)
     *
     * @param p the XPath parser
     * @throws XPathException if parsing fails PathMapor if updating functions are not allowed
     */

    public void parseUpdatingFunctionDeclaration(XQueryParser p) throws XPathException {
        needUpdate(p, "An updating function");
    }

    protected Expression parseExtendedExprSingle(XPathParser p) throws XPathException {
        return null;
    }


}
