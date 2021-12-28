////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
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
    private boolean mayBeOmitted = true;
    private boolean absentFocus = false;


    @Override
    public void prepareAttributes() {

        String asAtt = null;
        String useAtt = null;

        for (AttributeInfo att : attributes()) {
                NodeName attName = att.getNodeName();
                String f = attName.getDisplayName();
                String value = att.getValue();
            switch (f) {
                case "as":
                    asAtt = Whitespace.trim(value);
                    break;
                case "use":
                    useAtt = Whitespace.trim(value);
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }
        if (asAtt != null) {
            SequenceType st;
            try {
                st = makeSequenceType(asAtt);
            } catch (XPathException e) {
                st = SequenceType.SINGLE_ITEM;
                compileErrorInAttribute(e.getMessage(), e.getErrorCodeLocalPart(), "as");
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
                    mayBeOmitted = false;
                    break;
                case "optional":
                    // no action, this is the default
                    break;
                case "absent":
                    absentFocus = true;
                    break;
                default:
                    invalidAttribute("use", "required|optional|absent");
                    break;
            }
        }
        if (asAtt != null && absentFocus) {
            compileError("The 'as' attribute must be omitted when use='absent' is specified", "XTSE3089");
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
        if (mayBeOmitted && ((XSLTemplate) getParent()).getTemplateName() == null) {
            compileError("xsl:context-item appearing in an xsl:template declaration with no name attribute must specify use=required",
                "XTSE0020");
        }
        ((XSLTemplate)getParent()).setContextItemRequirements(requiredType, mayBeOmitted, absentFocus);
        iterateAxis(AxisInfo.PRECEDING_SIBLING).forEachOrFail(prec -> {
            if (((NodeInfo)prec).getNodeKind() != Type.TEXT || !Whitespace.isWhite(prec.getStringValueCS())) {
                compileError("xsl:context-item must be the first child of xsl:template");
            }
        });
    }

    public ItemType getRequiredContextItemType() {
        return requiredType;
    }

    public boolean isMayBeOmitted() {
        return mayBeOmitted;
    }

    public boolean isAbsentFocus() {
        return absentFocus;
    }


}
