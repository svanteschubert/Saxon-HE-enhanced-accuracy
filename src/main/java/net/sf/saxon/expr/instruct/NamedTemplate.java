////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TraceableComponent;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The runtime object corresponding to a named xsl:template element in the stylesheet.
 * <p>Note that the Template object no longer has precedence information associated with it; this is now
 * only in the Rule object that references this Template. This allows two rules to share the same template,
 * with different precedences. This occurs when a stylesheet module is imported more than once, from different
 * places, with different import precedences.</p>
 *
 * <p>From Saxon 9.7, the NamedTemplate and TemplateRule objects are separated. A NamedTemplate represents
 * a template with a name attribute; a TemplateRule is a template with a match attribute. If an xsl:template
 * declaration has both attributes, two objects are created.</p>
 */

public class NamedTemplate extends Actor implements TraceableComponent {

    // The body of the template is represented by an expression,
    // which is responsible for any type checking that's needed.

    private StructuredQName templateName;
    private boolean hasRequiredParams;
    private boolean bodyIsTailCallReturner;
    private SequenceType requiredType;
    private ItemType requiredContextItemType = AnyItemType.getInstance();
    private boolean mayOmitContextItem = true;
    private boolean absentFocus = false;
    private List<LocalParamInfo> localParamDetails = new ArrayList<>(4);

    /**
     * Create a named template
     */

    public NamedTemplate(StructuredQName templateName) {
        setTemplateName(templateName);
    }

    /**
     * Initialize the template
     *
     * @param templateName the name of the template (if any)
     *                     performed by apply-imports
     */

    public void setTemplateName(StructuredQName templateName) {
        this.templateName = templateName;
    }

    /**
     * Set the required context item type. Used when there is an xsl:context-item child element
     *
     * @param type          the required context item type
     * @param mayBeOmitted  true if the context item may be absent
     * @param absentFocus true if the context item is treated as absent even if supplied (use=absent)
     */

    public void setContextItemRequirements(ItemType type, boolean mayBeOmitted, boolean absentFocus) {
        requiredContextItemType = type;
        mayOmitContextItem = mayBeOmitted;
        this.absentFocus = absentFocus;
    }

    @Override
    public SymbolicName getSymbolicName() {
        if (getTemplateName() == null) {
            return null;
        } else {
            return new SymbolicName(StandardNames.XSL_TEMPLATE, getTemplateName());
        }
    }

    @Override
    public String getTracingTag() {
        return "xsl:template";
    }

    /**
     * Get the properties of this object to be included in trace messages, by supplying
     * the property values to a supplied consumer function
     *
     * @param consumer the function to which the properties should be supplied, as (property name,
     *                 value) pairs.
     */
    @Override
    public void gatherProperties(BiConsumer<String, Object> consumer) {
        consumer.accept("name", getTemplateName());
    }

    /**
     * Set the expression that forms the body of the template
     *
     * @param body the body of the template
     */

    @Override
    public void setBody(Expression body) {
        super.setBody(body);
        bodyIsTailCallReturner = (body instanceof TailCallReturner);
    }

    /**
     * Get the name of the template (if it is named)
     *
     * @return the template name, or null if unnamed
     */

    public StructuredQName getTemplateName() {
        return templateName;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     */

    @Override
    public StructuredQName getObjectName() {
        return templateName;
    }

    /**
     * Set whether this template has one or more required parameters
     *
     * @param has true if the template has at least one required parameter
     */

    public void setHasRequiredParams(boolean has) {
        hasRequiredParams = has;
    }

    /**
     * Ask whether this template has one or more required parameters
     *
     * @return true if this template has at least one required parameter
     */

    public boolean hasRequiredParams() {
        return hasRequiredParams;
    }

    /**
     * Set the required type to be returned by this template
     *
     * @param type the required type as defined in the "as" attribute on the xsl:template element
     */

    public void setRequiredType(SequenceType type) {
        requiredType = type;
    }

    /**
     * Get the required type to be returned by this template
     *
     * @return the required type as defined in the "as" attribute on the xsl:template element
     */

    public SequenceType getRequiredType() {
        if (requiredType == null) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            return requiredType;
        }
    }

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    public boolean isMayOmitContextItem() {
        return mayOmitContextItem;
    }

    public boolean isAbsentFocus() {
        return absentFocus;
    }


    /**
     * Get the local parameter with a given parameter id
     *
     * @param id the parameter id
     * @return the local parameter with this id if found, otherwise null
     */

    /*@Nullable*/
    public LocalParamInfo getLocalParamInfo(StructuredQName id) {
        List<LocalParamInfo> params = getLocalParamDetails();
        for (LocalParamInfo lp : params) {
            if (lp.name.equals(id)) {
                return lp;
            }
        }
        return null;
    }

    /**
     * Expand the template. Called when the template is invoked using xsl:call-template.
     * Invoking a template by this method does not change the current template.
     *
     *
     * @param output the destination for the result
     * @param context the XPath dynamic context
     * @return null if the template exited normally; but if it was a tail call, details of the call
     * that hasn't been made yet and needs to be made by the caller
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs while evaluating
     *                                           the template
     */

    public TailCall expand(Outputter output, XPathContext context) throws XPathException {
        Item contextItem = context.getContextItem();
        if (contextItem == null) {
            if (!mayOmitContextItem) {
                XPathException err =
                        new XPathException("The template requires a context item, but none has been supplied", "XTTE3090");
                err.setLocation(getLocation());
                err.setIsTypeError(true);
                throw err;
            }
        } else {
            TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
            if (requiredContextItemType != AnyItemType.getInstance() &&
                    !requiredContextItemType.matches(contextItem, th)) {
                RoleDiagnostic role = new RoleDiagnostic(
                        RoleDiagnostic.MISC, "context item for the named template", 0);
                String message = role.composeErrorMessage(requiredContextItemType, contextItem, th);
                XPathException err = new XPathException(message, "XTTE0590");
                err.setLocation(getLocation());
                err.setIsTypeError(true);
                throw err;
            }
            if (absentFocus) {
                context = context.newMinorContext();
                context.setCurrentIterator(null);
            }
        }
        if (bodyIsTailCallReturner) {
            return ((TailCallReturner) body).processLeavingTail(output, context);
        } else if (body != null) {
            body.process(output, context);
        }
        return null;
    }


    /**
     * Output diagnostic explanation to an ExpressionPresenter
     */

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("template");
        presenter.emitAttribute("name", getTemplateName());
        explainProperties(presenter);

        presenter.emitAttribute("slots", "" + getStackFrameMap().getNumberOfVariables());

        if (getBody() != null) {
            presenter.setChildRole("body");
            getBody().export(presenter);
        }
        presenter.endElement();
    }

    public void explainProperties(ExpressionPresenter presenter) throws XPathException {
        if (getRequiredContextItemType() != AnyItemType.getInstance()) {
            SequenceType st = SequenceType.makeSequenceType(getRequiredContextItemType(), StaticProperty.EXACTLY_ONE);
            presenter.emitAttribute("cxt", st.toAlphaCode());
        }

        String flags = "";
        if (mayOmitContextItem) {
            flags = "o";
        }
        if (!absentFocus) {
            flags += "s";
        }
        presenter.emitAttribute("flags", flags);
        if (getRequiredType() != SequenceType.ANY_SEQUENCE) {
            presenter.emitAttribute("as", getRequiredType().toAlphaCode());
        }
        presenter.emitAttribute("line", getLineNumber() + "");
        presenter.emitAttribute("module", getSystemId());
    }

    public void setLocalParamDetails(List<LocalParamInfo> details) {
        localParamDetails = details;
    }

    public List<LocalParamInfo> getLocalParamDetails() {
        return localParamDetails;
    }

    public static class LocalParamInfo {
        public StructuredQName name;
        public SequenceType requiredType;
        public boolean isRequired;
        public boolean isTunnel;
    }


}

