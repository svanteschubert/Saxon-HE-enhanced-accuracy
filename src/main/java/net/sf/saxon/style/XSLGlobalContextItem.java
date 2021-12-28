////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.instruct.GlobalContextRequirement;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.pattern.SameNameTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;

/**
 * An xsl:global-context-item declaration in the stylesheet
 */
public class XSLGlobalContextItem extends XSLContextItem {

    @Override
    public boolean isDeclaration() {
        return true;
    }

    /**
     * Check that the stylesheet element is valid. This is called once for each element, after
     * the entire tree has been built. As well as validation, it can perform first-time
     * initialisation. The default implementation does nothing; it is normally overriden
     * in subclasses.
     *
     * @param decl the declaration to be validated
     * @throws net.sf.saxon.trans.XPathException
     *          if any error is found during validation
     */
    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        AxisIterator prior = iterateAxis(AxisInfo.PRECEDING_SIBLING, new SameNameTest(this));
        if (prior.next() != null) {
            compileError("xsl:global-context-item must not appear twice within the same stylesheet module", "XTSE3087");
        }
    }

    /**
     * Method supplied by declaration elements to add themselves to a stylesheet-level index
     *
     * @param decl the Declaration being indexed. (This corresponds to the StyleElement object
     *             except in cases where one module is imported several times with different precedence.)
     * @param top  the outermost XSLStylesheet element
     * @throws net.sf.saxon.trans.XPathException
     *          if any error is encountered
     */
    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        prepareAttributes();
        GlobalContextRequirement req = new GlobalContextRequirement();
        req.setMayBeOmitted(isMayBeOmitted());
        req.setAbsentFocus(isAbsentFocus());
        req.addRequiredItemType(getRequiredContextItemType());
        try {
            top.getStylesheetPackage().setContextItemRequirements(req);
        } catch (XPathException e) {
            e.setLocation(decl.getSourceElement());
            throw e;
        }
    }
}
