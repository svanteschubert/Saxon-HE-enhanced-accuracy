////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.KeyDefinition;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.value.AtomicValue;

/**
 * An entry on the context stack. A new entry is created every time the context changes. This is a
 * representation of the stack created on request; it does not hold live data.
 */
public abstract class ContextStackFrame {

    private XPathContext context;
    private Location location;
    private Item contextItem;
    private Object container;

    /**
     * Set the location of the instruction that caused this new context
     * to be created
     *
     * @param loc the location of the instruction
     */

    public void setLocation(Location loc) {
        this.location = loc;
    }

    /**
     * Get the system ID representing the location of the instruction that caused this new context
     * to be created
     *
     * @return the system ID (base URI/module URI) of the module containing the instruction
     */

    public String getSystemId() {
        return location.getSystemId();
    }

    /**
     * Get the line number of the location of the instruction that caused this new context
     * to be created
     *
     * @return the line number of the instruction within its containing module
     */

    public int getLineNumber() {
        return location.getLineNumber();
    }

    /**
     * Set the container of the instruction that caused this new context to be created. This will
     * generally be an object such as an XSLT Template or a user-defined function
     *
     * @param container the container of the instruction
     */

    public void setComponent(Object container) {
        this.container = container;
    }

    /**
     * Get the container of the instruction that caused this new context to be created. This will
     * generally be an object such as an XSLT Template or a user-defined function
     *
     * @return the container of the instruction in the expression tree
     */

    public Object getContainer() {
        return container;
    }

    /**
     * Set the value of the context at this level of the context stack
     *
     * @param context the context as it was when this new context was created
     */

    public void setContext(XPathContext context) {
        this.context = context;
    }

    /**
     * Get the value of the context at this level of the context stack
     *
     * @return the context as it was when this new context was created
     */

    public XPathContext getContext() {
        return context;
    }

    /**
     * Set the value of the context item at this level of the context stack
     *
     * @param contextItem the context item as it was when this new context was created
     */

    public void setContextItem(Item contextItem) {
        this.contextItem = contextItem;
    }

    /**
     * Get the value of the context item at this level of the context stack
     *
     * @return the context item as it was when this new context was created
     */

    public Item getContextItem() {
        return contextItem;
    }

    /**
     * Display a representation of the stack frame on the specified output stream
     *
     * @param out the output stream
     */

    public abstract void print(Logger out);

    /**
     * Show the location of a call (for use by subclasses)
     *
     * @return typically "(" + systemId() + "#" + lineNumber() + ")"
     */

    protected String showLocation() {
        if (getSystemId() == null) {
            return "";
        }
        int line = getLineNumber();
        if (line == -1 || line == 0xfffff) {
            return "(" + getSystemId() + ")";
        } else {
            return "(" + getSystemId() + "#" + getLineNumber() + ")";
        }
    }



    /**
     * Subclass of ContextStackFrame representing the outermost stack frame,
     * for the calling application
     */

    public static class CallingApplication extends ContextStackFrame {
        @Override
        public void print(Logger out) {
            //out.println("  (called from external application)");
        }
    }

    /**
     * Subclass of ContextStackFrame representing a built-in template rule in XSLT
     */

    public static class BuiltInTemplateRule extends ContextStackFrame {
        private XPathContext context;
        public BuiltInTemplateRule(XPathContext context) {
            this.context = context;
        }
        @Override
        public void print(Logger out) {
            Item contextItem = context.getContextItem();
            String diag;
            if (contextItem instanceof NodeInfo) {
                diag = Navigator.getPath((NodeInfo)contextItem);
            } else if (contextItem instanceof AtomicValue) {
                diag = "value " + contextItem.toString();
            } else if (contextItem instanceof MapItem) {
                diag = "map";
            } else if (contextItem instanceof ArrayItem) {
                diag = "array";
            } else if (contextItem instanceof Function) {
                diag = "function";
            } else {
                diag = "item";
            }
            out.error("  in built-in template rule for " + diag + " in " +
                              context.getCurrentMode().getActor().getModeTitle().toLowerCase());
        }
    }

    /**
     * Subclass of ContextStackFrame representing a call to a user-defined function
     * either in XSLT or XQuery
     */

    public static class FunctionCall extends ContextStackFrame {

        StructuredQName functionName;

        /**
         * Get the name of the function being called
         *
         * @return the name of the function being called
         */
        public StructuredQName getFunctionName() {
            return functionName;
        }

        /**
         * Set the name of the function being called
         *
         * @param functionName the name of the function being called
         */
        public void setFunctionName(StructuredQName functionName) {
            this.functionName = functionName;
        }

        /**
         * Display a representation of the stack frame on the specified output stream
         *
         * @param out the output stream
         */

        @Override
        public void print(Logger out) {
            out.error("  at " + (functionName == null ? "(anonymous)" : functionName.getDisplayName()) + "() " + showLocation());
        }

    }

    /**
     * Subclass of ContextStackFrame representing an xsl:apply-templates call in XSLT
     */

    public static class ApplyTemplates extends ContextStackFrame {

        /**
         * Display a representation of the stack frame on the specified output stream
         *
         * @param out the output stream
         */

        @Override
        public void print(Logger out) {
            out.error("  at xsl:apply-templates " + showLocation());
            Item node = getContextItem();
            if (node instanceof NodeInfo) {
                out.error("     processing " + Navigator.getPath((NodeInfo) node));
            }
        }
    }

    /**
     * Subclass of ContextStackFrame representing an xsl:call-template instruction in XSLT
     */

    public static class CallTemplate extends ContextStackFrame {

        /**
         * Get the name of the template being called
         *
         * @return the name of the template being called. Note this may be null in the case of the
         *         extension instruction saxon:call-template
         */
        public StructuredQName getTemplateName() {
            return templateName;
        }

        /**
         * Set the name of the template being called
         *
         * @param templateName the name of the template being called.
         */
        public void setTemplateName(StructuredQName templateName) {
            this.templateName = templateName;
        }

        StructuredQName templateName;

        /**
         * Display a representation of the stack frame on the specified output stream
         *
         * @param out the output stream
         */

        @Override
        public void print(Logger out) {
            String name = templateName == null ? "??" : templateName.getDisplayName();
            out.error("  at xsl:call-template name=\"" + name + "\" " + showLocation());
        }
    }

    /**
     * Subclass of ContextStackFrame representing the evaluation of a variable (typically a global variable)
     */

    public static class VariableEvaluation extends ContextStackFrame {

        /**
         * Get the name of the variable
         *
         * @return the name of the variable
         */
        public StructuredQName getVariableName() {
            return variableName;
        }

        /**
         * Set the name of the variable
         *
         * @param variableName the name of the variable
         */
        public void setVariableName(StructuredQName variableName) {
            this.variableName = variableName;
        }

        StructuredQName variableName;

        /**
         * Display a representation of the stack frame on the specified output stream
         *
         * @param out the output stream
         */

        @Override
        public void print(Logger out) {
            out.error("  in " + displayContainer(getContainer()) + " " + showLocation());
        }
    }

    private static String displayContainer(/*@NotNull*/ Object container) {
        if (container instanceof Actor) {
            StructuredQName name = ((Actor) container).getComponentName();
            String objectName = name == null ? "" : name.getDisplayName();
            if (container instanceof NamedTemplate) {
                return "template name=\"" + objectName + "\"";
            } else if (container instanceof UserFunction) {
                return "function " + objectName + "()";
            } else if (container instanceof AttributeSet) {
                return "attribute-set " + objectName;
            } else if (container instanceof KeyDefinition) {
                return "key " + objectName;
            } else if (container instanceof GlobalVariable) {
                StructuredQName qName = ((GlobalVariable) container).getVariableQName();
                if (qName.hasURI(NamespaceConstant.SAXON_GENERATED_VARIABLE)) {
                    return "optimizer-created global variable";
                } else {
                    return "global variable $" + qName.getDisplayName();
                }
            }
        } else if (container instanceof TemplateRule) {
            return "template match=\"" + ((TemplateRule) container).getMatchPattern().toString() + "\"";
        }
        return "";
    }


}

