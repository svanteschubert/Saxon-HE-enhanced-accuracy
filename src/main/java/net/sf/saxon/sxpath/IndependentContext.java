////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LocalVariableReference;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Schema;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 * An IndependentContext provides a context for parsing an XPath expression appearing
 * in a context other than a stylesheet.
 * <p>This class is used in a number of places where freestanding XPath expressions occur.
 * These include the native Saxon XPath API, the .NET XPath API, XPath expressions used
 * in XML Schema identity constraints, and XPath expressions supplied to saxon:evaluate().
 * It is not used by the JAXP XPath API (though it shares code with that API through
 * the common superclass AbstractStaticContext).</p>
 * <p>This class currently provides no mechanism for binding user-defined functions.</p>
 */

public class IndependentContext extends AbstractStaticContext
        implements NamespaceResolver {

    protected HashMap<String, NamespaceUri> namespaces = new HashMap<>(10);
    protected HashMap<StructuredQName, XPathVariable> variables = new HashMap<>(20);
    protected NamespaceResolver externalResolver = null;
    protected SequenceType requiredContextValueType = SequenceType.SINGLE_ITEM;
    protected Schema importedSchema;
    protected boolean autoDeclare = false;
    protected Executable executable;
    protected RetainedStaticContext retainedStaticContext;
    protected OptimizerOptions optimizerOptions;
    protected boolean parentlessContextItem;


    /**
     * Create an IndependentContext along with a new (non-schema-aware) Saxon Configuration
     */

    public IndependentContext() {
        this(new Configuration());
    }

    /**
     * Create an IndependentContext using a specific Configuration
     *
     * @param config the Saxon configuration to be used
     */

    public IndependentContext(Configuration config) {
        this(config, 31);
    }

    /**
     * Create an IndependentContext using a specific Configuration
     *
     * @param config the Saxon configuration to be used
     * @param version the XPath language version (31 = XPath 3.1, 40 = XPath 4.0)
     */

    public IndependentContext(Configuration config, int version) {
        setConfiguration(config);
        clearNamespaces();
        xpathLanguageLevel = version;
        setDefaultFunctionLibrary(version);
        usingDefaultFunctionLibrary = true;
        setDefaultCollationName(config.getDefaultCollationName());
        setOptimizerOptions(config.getOptimizerOptions());
        PackageData pd = new PackageData(config);
        pd.setHostLanguage(HostLanguage.XPATH, version);
        pd.setSchemaAware(false);
        setPackageData(pd);
    }

    /**
     * Create a IndependentContext as a copy of another IndependentContext
     *
     * @param ic the IndependentContext to be copied
     */

    public IndependentContext(IndependentContext ic) {
        this(ic.getConfiguration(), ic.getXPathVersion());
        setPackageData(ic.getPackageData());
        setBaseURI(ic.getStaticBaseURI());
        setContainingLocation(ic.getContainingLocation());
        setDefaultElementNamespace(ic.getDefaultElementNamespace());
        setDefaultFunctionNamespace(ic.getDefaultFunctionNamespace());
        setBackwardsCompatibilityMode(ic.isInBackwardsCompatibleMode());
        setImportedSchema(ic.getImportedSchema());
        namespaces = new HashMap<>(ic.namespaces);
        variables = new HashMap<>(10);
        FunctionLibraryList libList = (FunctionLibraryList) ic.getFunctionLibrary();
        if (libList != null) {
            setFunctionLibrary((FunctionLibraryList) libList.copy());
        }
        setDefaultCollationName(ic.getDefaultCollationName());
        externalResolver = ic.externalResolver;
        autoDeclare = ic.autoDeclare;
        setUnprefixedElementMatchingPolicy(ic.getUnprefixedElementMatchingPolicy());
        requiredContextValueType = ic.requiredContextValueType;
        setExecutable(ic.getExecutable());
        setOptimizerOptions(ic.getOptimizerOptions());
        retainedStaticContext = null;
        parentlessContextItem = ic.parentlessContextItem;
    }

    /**
     * Construct a RetainedStaticContext, which extracts information from this StaticContext
     * to provide the subset of static context information that is potentially needed
     * during expression evaluation.
     *
     * This implementation returns the same RetainedStaticContext object each time, which relies
     * on the information in the static context being effectively immutable while the IndependentContext
     * object remains in use.
     *
     * @return a RetainedStaticContext object: either a newly created one, or one that is
     * reused from a previous invocation.
     */
    @Override
    public RetainedStaticContext makeRetainedStaticContext() {
        if (retainedStaticContext == null) {
            retainedStaticContext = new RetainedStaticContext(this);
        }
        return retainedStaticContext;
    }

    /**
     * Declare a namespace whose prefix can be used in expressions
     *
     * @param prefix The namespace prefix. Must not be null. Supplying "" sets the
     *               default element namespace.
     * @param uri    The namespace URI. Must not be null.
     */

    public void declareNamespace(String prefix, NamespaceUri uri) {
        if (prefix == null) {
            throw new NullPointerException("Null prefix supplied to declareNamespace()");
        }
        if (uri == null) {
            throw new NullPointerException("Null namespace URI supplied to declareNamespace()");
        }
        if (prefix.isEmpty()) {
            setDefaultElementNamespace(uri);
        } else {
            namespaces.put(prefix, uri);
        }
    }

    /**
     * Set the default namespace for elements and types
     *
     * @param uri the namespace to be used for unprefixed element and type names.
     *            The value "" (or NamespaceConstant.NULL) represents the non-namespace
     */

    @Override
    public void setDefaultElementNamespace(NamespaceUri uri) {
        if (uri == null) {
            uri = NamespaceUri.NULL;
        }
        super.setDefaultElementNamespace(uri);
        namespaces.put("", uri);
    }

    /**
     * Clear all the declared namespaces, except for the standard ones (xml, xsl, saxon).
     * This also resets the default element namespace to the "null" namespace
     */

    public void clearNamespaces() {
        namespaces.clear();
        declareNamespace("xml", NamespaceUri.XML);
        declareNamespace("xsl", NamespaceUri.XSLT);
        declareNamespace("saxon", NamespaceUri.SAXON);
        declareNamespace("xs", NamespaceUri.SCHEMA);
        declareNamespace("", NamespaceUri.NULL);
    }

    /**
     * Clear all the declared namespaces, including the standard ones (xml, xslt, saxon).
     * Leave only the XML namespace and the default namespace (xmlns="").
     * This also resets the default element namespace to the "null" namespace.
     */

    public void clearAllNamespaces() {
        namespaces.clear();
        declareNamespace("xml", NamespaceUri.XML);
        declareNamespace("", NamespaceUri.NULL);
    }

    /**
     * Declares all the namespaces that are in-scope for a supplied node, removing all previous
     * namespace declarations.
     * In addition, the standard namespaces (xml, xslt, saxon) are declared. This method also
     * sets the default element namespace to be the same as the default namespace for this node.
     *
     * @param node The node whose in-scope namespaces are to be used as the context namespaces.
     *             If the node is an attribute, text node, etc, then the namespaces of its parent element are used.
     */

    public void setNamespaces(NodeInfo node) {
        namespaces.clear();
        externalResolver = null;
        int kind = node.getNodeKind();
        if (kind == Type.ATTRIBUTE || kind == Type.TEXT ||
                kind == Type.COMMENT || kind == Type.PROCESSING_INSTRUCTION ||
                kind == Type.NAMESPACE) {
            node = (NodeInfo)node.getParent();
        }
        if (node == null) {
            return;
        }

        SequenceIterator iter = node.iterateNamespaceAxis(AnyGNode.TEST);
        while (true) {
            NodeInfo ns = (NodeInfo)iter.next();
            if (ns == null) {
                return;
            }
            String prefix = ns.getLocalPart();
            if ("".equals(prefix)) {
                setDefaultElementNamespace(NamespaceUri.of(ns.getStringValue()));
            } else {
                declareNamespace(ns.getLocalPart(), NamespaceUri.of(ns.getStringValue()));
            }
        }
    }

    /**
     * Set an external namespace resolver. If this is set, then all resolution of namespace
     * prefixes is delegated to the external namespace resolver, and namespaces declared
     * individually on this IndependentContext object are ignored.
     *
     * @param resolver the external NamespaceResolver
     */

    public void setNamespaceResolver(NamespaceResolver resolver) {
        externalResolver = resolver;
    }

    /**
     * Say whether undeclared variables are allowed. By default, they are not allowed. When
     * undeclared variables are allowed, it is not necessary to predeclare the variables that
     * may be used in the XPath expression; instead, a variable is automatically declared when a reference
     * to the variable is encountered within the expression.
     *
     * @param allow true if undeclared variables are allowed, false if they are not allowed.
     * @since 9.2
     */

    public void setAllowUndeclaredVariables(boolean allow) {
        autoDeclare = allow;
    }

    /**
     * Ask whether undeclared variables are allowed. By default, they are not allowed. When
     * undeclared variables are allowed, it is not necessary to predeclare the variables that
     * may be used in the XPath expression; instead, a variable is automatically declared when a reference
     * to the variable is encountered within the expression.
     *
     * @return true if undeclared variables are allowed, false if they are not allowed.
     * @since 9.2
     */

    public boolean isAllowUndeclaredVariables() {
        return autoDeclare;
    }

    /**
     * Declare a variable. A variable must be declared before an expression referring
     * to it is compiled. The initial value of the variable will be the empty sequence
     *
     * @param qname The name of the variable
     * @return an XPathVariable object representing information about the variable that has been
     *         declared.
     */

    public XPathVariable declareVariable(QNameValue qname) {
        return declareVariable(qname.getStructuredQName());
    }

    /**
     * Declare a variable. A variable must be declared before an expression referring
     * to it is compiled. The initial value of the variable will be the empty sequence
     *
     * @param namespaceURI The namespace URI of the name of the variable. Supply "" to represent
     *                     names in no namespace (null is also accepted)
     * @param localName    The local part of the name of the variable (an NCName)
     * @return an XPathVariable object representing information about the variable that has been
     *         declared.
     */

    public XPathVariable declareVariable(NamespaceUri namespaceURI, String localName) {
        StructuredQName qName = new StructuredQName("", namespaceURI==null ? NamespaceUri.NULL : namespaceURI, localName);
        return declareVariable(qName);
    }

    /**
     * Declare a variable. A variable must be declared before an expression referring
     * to it is compiled. The initial value of the variable will be the empty sequence
     *
     * @param qName the name of the variable.
     * @return an XPathVariable object representing information about the variable that has been
     *         declared.
     * @since 9.2
     */


    public XPathVariable declareVariable(StructuredQName qName) {
        XPathVariable var = variables.get(qName);
        if (var != null) {
            return var;
        } else {
            var = XPathVariable.make(qName);
            int slot = variables.size();
            var.setSlotNumber(slot);
            variables.put(qName, var);
            return var;
        }
    }

    /**
     * Get an iterator over all the variables that have been declared, either explicitly by an
     * application call on declareVariable(), or implicitly if the option <code>allowUndeclaredVariables</code>
     * is set.
     *
     * @return an iterator; the objects returned by this iterator will be instances of XPathVariable
     * @since 9.2
     */

    public Iterable<XPathVariable> getExternalVariables() {
        return variables.values();
    }

    /**
     * Get the declared variable with a given name, if there is one
     *
     * @param qName the name of the required variable
     * @return the explicitly or implicitly declared variable with this name if it exists,
     *         or null otherwise
     * @since 9.2
     */

    public XPathVariable getExternalVariable(StructuredQName qName) {
        return variables.get(qName);
    }

    /**
     * Get the slot number allocated to a particular variable
     *
     * @param qname the name of the variable
     * @return the slot number, or -1 if the variable has not been declared
     */

    public int getSlotNumber(QNameValue qname) {
        StructuredQName sq = qname.getStructuredQName();
        XPathVariable var = variables.get(sq);
        if (var == null) {
            return -1;
        }
        return var.getLocalSlotNumber();
    }

    @Override
    public NamespaceResolver getNamespaceResolver() {
        if (externalResolver != null) {
            return externalResolver;
        } else {
            return this;
        }
    }


    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is ""
     * @return the uri for the namespace, or null if the prefix is not in scope.
     *         Return "" if the prefix maps to the null namespace.
     */

    @Override
    public NamespaceUri getURIForPrefix(String prefix, boolean useDefault) {
        if (externalResolver != null) {
            return externalResolver.getURIForPrefix(prefix, useDefault);
        }
        if (prefix.isEmpty()) {
            return useDefault ? getDefaultElementNamespace() : NamespaceUri.NULL;
        } else {
            NamespaceUri uri = prologNamespaces.getNamespaceUri(prefix);
            if (uri == NamespaceUri.NULL && xpathLanguageLevel >= 40) {
                return null; // Prefix binding has been undeclared
            }
            if (uri == null) {
                uri = namespaces.get(prefix);
            }
            return uri;
        }
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    @Override
    public Iterator<String> iteratePrefixes() {
        if (externalResolver != null) {
            return externalResolver.iteratePrefixes();
        } else {
            return namespaces.keySet().iterator();
        }
    }

    /**
     * Bind a variable used in an XPath Expression to the XSLVariable element in which it is declared.
     * This method is provided for use by the XPath parser, and it should not be called by the user of
     * the API, or overridden, unless variables are to be declared using a mechanism other than the
     * declareVariable method of this class.
     *
     * @param qName the name of the variable
     * @return the resulting variable reference
     */

    @Override
    public Expression bindVariable(StructuredQName qName) throws XPathException {
        XPathVariable var = variables.get(qName);
        if (var == null) {
            if (autoDeclare) {
                return new LocalVariableReference(declareVariable(qName));
            } else {
                throw new XPathException("Undeclared variable in XPath expression: $" + qName.getClarkName(), "XPST0008");
            }
        } else {
            return new LocalVariableReference(var);
        }
    }

    /**
     * Get a Stack Frame Map containing definitions of all the declared variables. This will return a newly
     * created object that the caller is free to modify by adding additional variables, without affecting
     * the static context itself.
     */

    public SlotManager getStackFrameMap() {
        SlotManager map = getConfiguration().makeSlotManager();
        XPathVariable[] va = new XPathVariable[variables.size()];

        for (XPathVariable var : variables.values()) {
            va[var.getLocalSlotNumber()] = var;
        }
        for (XPathVariable v : va) {
            map.allocateSlotNumber(v.getVariableQName(), v);
        }
        return map;
    }

    public Collection<XPathVariable> getDeclaredVariables() {
        return variables.values();
    }

    /**
     * Add an imported schema to the static context
     */

    public void setImportedSchema(Schema schema) {
        this.importedSchema = schema;
    }

    /**
     * Get the imported schema for this static context, if any
     * @return the imported schema, or the MinimalSchema if no schema has been imported
     */

    public Schema getImportedSchema() {
         return importedSchema==null ? getConfiguration().emptySchema() : importedSchema;
    }

    /**
     * Declare the static type of the context item. If this type is declared, and if a context item
     * is supplied when the query is invoked, then the context item must conform to this type (no
     * type conversion will take place to force it into this type).
     *
     * @param type the required type of the context item
     * @since 9.3
     */

    public void setRequiredContextItemType(ItemType type) {
        requiredContextValueType = SequenceType.one(type);
    }

    /**
     * Declare the static type of the context item. If this type is declared, and if a context value
     * is supplied when the query is invoked, then the context value must conform to this type (no
     * type conversion will take place to force it into this type).
     *
     * @param type the required type of the context item
     * @since 13.0
     */

    public void setRequiredContextValueType(SequenceType type) {
        requiredContextValueType = type;
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     *
     * @return the required type of the context item
     * @since 9.3
     */

    @Override
    public ItemType getRequiredContextItemType() {
        return requiredContextValueType.getPrimaryType();
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     *
     * @return the required type of the context item
     * @since 9.3
     */

    @Override
    public SequenceType getRequiredContextValueType() {
        return requiredContextValueType;
    }

    /**
     * Set the optimizer options to be used for compiling expressions that use this static context.
     * By default the optimizer options set in the {@link Configuration} are used.
     * @param options the optimizer options to be used
     */

    public void setOptimizerOptions(OptimizerOptions options) {
        this.optimizerOptions = options;
    }

    /**
     * Get the optimizer options being used for compiling expressions that use this static context.
     * By default the optimizer options set in the {@link Configuration} are used.
     *
     * @return the optimizer options being used
     */

    @Override
    public OptimizerOptions getOptimizerOptions() {
        return this.optimizerOptions;
    }

    public void setExecutable(Executable exec) {
        executable = exec;
    }

    /*@Nullable*/
    public Executable getExecutable() {
        return executable;
    }


    public int getColumnNumber() {
        return -1;
    }

    public String getPublicId() {
        return null;
    }

    public int getLineNumber() {
        return -1;
    }

    /**
     * Ask whether the context item is known to be parentless
     *
     * @return true if it is known that the context item for evaluating the expression will have no parent
     */
    public boolean isContextItemParentless() {
        return parentlessContextItem;
    }

    /**
     * Say whether the context item is known to be parentless. This is used for XSD Assertions;
     * it allows a static warning if an XSD assertion attempts to look outside the element being
     * validated.
     *
     * @param parentless true if it is known that the context item for evaluating the expression
     *                  will have no parent
     */

    public void setContextItemParentless(boolean parentless) {
        parentlessContextItem = parentless;
    }
}

