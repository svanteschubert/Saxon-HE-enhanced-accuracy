////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

import java.util.Set;

/**
 * A StaticContext contains the information needed while an expression or pattern
 * is being parsed. The information is also sometimes needed at run-time.
 */

public interface StaticContext {

    /**
     * Get the system configuration
     *
     * @return the Saxon configuration
     */

    Configuration getConfiguration();

    /**
     * Get information about the containing package (unit of compilation)
     * @return the package data
     */

    PackageData getPackageData();

    /**
     * Construct a dynamic context for early evaluation of constant subexpressions.
     *
     * @return a newly constructed dynamic context
     */

    XPathContext makeEarlyEvaluationContext();

    /**
     * Construct a RetainedStaticContext, which extracts information from this StaticContext
     * to provide the subset of static context information that is potentially needed
     * during expression evaluation
     * @return a RetainedStaticContext object: either a newly created one, or one that is
     * reused from a previous invocation.
     */

    RetainedStaticContext makeRetainedStaticContext();

    /**
     * Get the containing location. This is location information relevant to an expression or query
     * as a whole. In the case of an XPath expression held in a node of an XML document, it will
     * provide the location of that node. In the case of a query held in a file, it contains the
     * location of the file (in its systemId property). The method does NOT provide fine-grained
     * location information for each contained subexpression. The location that is returned should
     * be immutable for the duration of parsing of an XPath expression or query.
     *
     * @return the containing location
     */

    Location getContainingLocation();

    /**
     * Issue a compile-time warning.
     * @param message The warning message. This should not contain any prefix such as "Warning".
     * @param locator the location of the construct in question. May be null.
     */

    void issueWarning(String message, Location locator);

    /**
     * Get the System ID of the container of the expression. This is the containing
     * entity (file) and is therefore useful for diagnostics. Use getBaseURI() to get
     * the base URI, which may be different.
     *
     * @return the system ID
     */

    String getSystemId();

    /**
     * Get the static base URI, for resolving any relative URI's used
     * in the expression.
     * Used by the document(), doc(), resolve-uri(), and base-uri() functions.
     * May return null if the base URI is not known.
     *
     * @return the static base URI, or null if not known
     */

    String getStaticBaseURI();

    /**
     * Bind a variable used in this expression to the Binding object in which it is declared
     *
     * @param qName The name of the variable
     * @return an expression representing the variable reference, This will often be
     *         a {@link VariableReference}, suitably initialized to refer to the corresponding variable declaration,
     *         but in general it can be any expression which returns the variable's value when evaluated.
     * @throws XPathException if the variable cannot be bound (has not been declared)
     */

    Expression bindVariable(StructuredQName qName) throws XPathException;

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context
     *
     * @return the function library
     */

    FunctionLibrary getFunctionLibrary();

    /**
     * Get the name of the default collation.
     *
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined
     */

    String getDefaultCollationName();

    /**
     * Get the default XPath namespace for elements and types
     *
     * @return the default namespace, or {@link NamespaceConstant#NULL} for the non-namespace
     */

    String getDefaultElementNamespace();

    /**
     * Get the matching policy for unprefixed element names in axis steps. This is a Saxon extension.
     * The value can be any of {@link UnprefixedElementMatchingPolicy#DEFAULT_NAMESPACE} (the default),
     * which uses the value of {@link #getDefaultElementNamespace()}, or {@link UnprefixedElementMatchingPolicy#DEFAULT_NAMESPACE_OR_NONE},
     * which matches both the namespace given in {@link #getDefaultElementNamespace()} and the null namespace,
     * or {@link UnprefixedElementMatchingPolicy#ANY_NAMESPACE}, which matches any namespace (that is, it
     * matches by local name only).
     */

    default UnprefixedElementMatchingPolicy getUnprefixedElementMatchingPolicy() {
        return UnprefixedElementMatchingPolicy.DEFAULT_NAMESPACE;
    }

    /**
     * Get the default function namespace
     *
     * @return the default namespace for function names
     */

    String getDefaultFunctionNamespace();

    /**
     * Determine whether backwards compatibility mode is used
     *
     * @return true if 1.0 compaibility mode is in force.
     */

    boolean isInBackwardsCompatibleMode();

    /**
     * Ask whether a Schema for a given target namespace has been imported. Note that the
     * in-scope element declarations, attribute declarations and schema types are the types registered
     * with the (schema-aware) configuration, provided that their namespace URI is registered
     * in the static context as being an imported schema namespace. (A consequence of this is that
     * within a Configuration, there can only be one schema for any given namespace, including the
     * null namespace).
     *
     * @param namespace the target namespace in question
     * @return true if the given namespace has been imported
     */

    boolean isImportedSchema(String namespace);

    /**
     * Get the set of imported schemas
     *
     * @return a Set, the set of URIs representing the target namespaces of imported schemas,
     *         using the zero-length string to denote the "null" namespace.
     */

    Set<String> getImportedSchemaNamespaces();

    /**
     * Get a namespace resolver to resolve the namespaces declared in this static context.
     *
     * @return a namespace resolver.
     */

    NamespaceResolver getNamespaceResolver();

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     *
     * @return the required type of the context item
     * @since 9.3
     */

    ItemType getRequiredContextItemType();

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @return the decimal format manager for this static context, or null if no named decimal
     *         formats are available in this environment.
     * @since 9.2
     */

    DecimalFormatManager getDecimalFormatManager();

    /**
     * Get the XPath language level supported, as an integer (being the actual version
     * number times ten). In Saxon 9.9 the possible values are 20 (XPath 2.0), 30 (XPath 3.0),
     * 31 (XPath 3.1), and 305 (XPath 3.0 plus the extensions defined in XSLT 3.0).
     *
     * @return the XPath language level; the return value will be either 20, 30, 305, or 31
     * @since 9.7
     */

    int getXPathVersion();

    /**
     * Get the KeyManager, containing definitions of keys available for use.
     *
     * @return the KeyManager. This is used to resolve key names, both explicit calls
     *         on key() used in XSLT, and system-generated calls on key() which may
     *         also appear in XQuery and XPath
     */

    KeyManager getKeyManager();

    /**
     * Get type alias. This is a Saxon extension. A type alias is a QName which can
     * be used as a shorthand for an itemtype, using the syntax ~typename anywhere that
     * an item type is permitted.
     * @param typeName the name of the type alias
     * @return the corresponding item type, if the name is recognised; otherwise null.
     */

    ItemType resolveTypeAlias(StructuredQName typeName);

    /**
     * Get the optimization options in use. By default these are taken from the
     * {@link Configuration}
     * @return the optimization options in use
     */

    default OptimizerOptions getOptimizerOptions() {
        return getConfiguration().getOptimizerOptions();
    }

}

