////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.Optionality;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;


/**
 * An xsl:context-item element in the stylesheet. <br>
 */

public class XSLContextItem extends StyleElement {

    private ItemType requiredType = AnyItemType.getInstance();
    private Optionality optionality = Optionality.OPTIONAL;


    @Override
    protected void prepareAttributes() {

        String asAtt = null;
        String useAtt = null;

        for (AttributeInfo att : attributes()) {
                NodeName attName = att.getNodeName();
                String f = attName.getDisplayName();
                String value = att.getValue();
            switch (f) {
                case "as" -> asAtt = Whitespace.trim(value);
                case "use" -> useAtt = Whitespace.trim(value);
                default -> checkUnknownAttribute(attName);
            }
        }
        if (asAtt != null) {
            SequenceType st;
            try {
                st = makeSequenceType(asAtt);
            } catch (XPathException e) {
                st = SequenceType.SINGLE_ITEM;
                compileErrorInAttribute(e, "as");
            }
            if (st.getCardinality() != StaticProperty.EXACTLY_ONE) {
                compileError("The xsl:context-item/@use attribute must be an item type (no occurrence indicator allowed)", "XTSE0020");
                return;
            }
            requiredType = st.getPrimaryType();
        }
        if (useAtt != null) {
            switch (useAtt) {
                case "required":
                    optionality = Optionality.REQUIRED;
                    break;
                case "optional":
                    // no action, this is the default
                    break;
                case "absent":
                    optionality = Optionality.PROHIBITED;
                    break;
                default:
                    invalidAttribute("use", "required|optional|absent");
                    break;
            }
        }
        if (asAtt != null && optionality == Optionality.PROHIBITED) {
            compileError("The 'as' attribute must be omitted when use='absent' is specified",
                         this instanceof XSLGlobalContextItem ? "XTSE3089": "XTSE3088");
        }
    }

    /**
     * Check that the stylesheet element is valid. This is called once for each element, after
     * the entire tree has been built. As well as validation, it can perform first-time
     * initialisation. The default implementation does nothing; it is normally overriden
     * in subclasses.
     *
     * @param decl the declaration to be validated
     * @throws XPathException if any error is found during validation
     */

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        if (!(getParent() instanceof XSLTemplate)) {
            compileError("xsl:context-item can appear only as a child of xsl:template");
            return;
        }
        if (optionality != Optionality.REQUIRED && ((XSLTemplate) getParent()).getTemplateName() == null) {
            compileError("xsl:context-item appearing in an xsl:template declaration with no name attribute must specify use=required",
                "XTSE0020");
        }
        ((XSLTemplate)getParent()).setContextItemRequirements(requiredType, optionality);
        SequenceTool.supply(iteratePrecedingSiblingAxis(AnyGNode.TEST), (ItemConsumer<? super Item>) prec -> {
            if (((NodeInfo) prec).getNodeKind() != Type.TEXT || !Whitespace.isAllWhite(prec.getUnicodeStringValue())) {
                compileError("xsl:context-item must be the first child of xsl:template");
            }
        });
    }

    public ItemType getRequiredContextItemType() {
        return requiredType;
    }

    public Optionality getContextValueOptionality() {
        return optionality;
    }


}
