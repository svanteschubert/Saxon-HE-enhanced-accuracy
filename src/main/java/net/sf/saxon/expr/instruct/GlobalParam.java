////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trans.XPathException;

/**
 * The compiled form of a global xsl:param element in an XSLT stylesheet or an
 * external variable declared in the prolog of a Query. <br>
 * The xsl:param element in XSLT has mandatory attribute name and optional attribute select. It can also
 * be specified as required="yes" or required="no". In standard XQuery 1.0 external variables are always required,
 * and no default value can be specified; but Saxon provides an extension pragma that allows a query
 * to specify a default. XQuery 1.1 adds standard syntax for defining a default value.
 */

public final class GlobalParam extends GlobalVariable {

    private boolean implicitlyRequired;

    public GlobalParam() {
    }

    /**
     * Indicate that this parameter is implicitly required, because the default value does not match the type
     *
     * @param requiredParam true if this is a required parameter
     */

    public void setImplicitlyRequiredParam(boolean requiredParam) {
        this.implicitlyRequired = requiredParam;
    }

    /**
     * Ask whether this variable represents a required parameter
     * @return true if this variable represents a required parameter
     */

    public boolean isImplicitlyRequiredParam() {
        return this.implicitlyRequired;
    }

    @Override
    public String getTracingTag() {
        return "xsl:param";
    }

    /**
     * Evaluate the variable
     */
    @Override
    public GroundedValue evaluateVariable(XPathContext context, Component target) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        Bindery b = controller.getBindery(getPackageData());
        GroundedValue val = b.getGlobalVariableValue(this);
        if (val != null) {
            if (val instanceof Bindery.FailureValue) {
                throw ((Bindery.FailureValue)val).getObject();
            }
            return val;
        }
        val = controller.getConvertedParameter(getVariableQName(), getRequiredType(), context);
        if (val != null) {
            return b.saveGlobalVariableValue(this, val);
        }
        if (isRequiredParam()) {
            XPathException e = new XPathException("No value supplied for required parameter $" +
                    getVariableQName().getDisplayName());
            e.setXPathContext(context);
            e.setLocator(this);
            e.setErrorCode(getPackageData().isXSLT() ? "XTDE0050" : "XPDY0002");
            throw e;
        } else if (isImplicitlyRequiredParam()) {
            XPathException e = new XPathException("A value must be supplied for parameter $" +
                    getVariableQName().getDisplayName() +
                    " because there is no default value for the required type");
            e.setXPathContext(context);
            e.setLocator(this);
            e.setErrorCode("XTDE0700");
            throw e;
        }
        // evaluate and save the default value
        return actuallyEvaluate(context, target);
    }

    /**
     * Evaluate the variable
     */

    @Override
    public GroundedValue evaluateVariable(XPathContext context) throws XPathException {
        return evaluateVariable(context, null);
    }

    @Override
    protected String getFlags() {
        String f = super.getFlags();
        if (isImplicitlyRequiredParam()) {
            f += "i";
        }
        return f;
    }
}

