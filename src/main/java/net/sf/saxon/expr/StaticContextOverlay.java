// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Schema;
import net.sf.saxon.value.SequenceType;

import java.util.*;

/**
 * This class implements the {@link StaticContext} interface by delegating
 * everything except namespace resolution to an immutable base static context.
 * A <code>StaticContextOverlay</code> is constructed when an XPath 4.0
 * expression includes a "declare namespace" declaration at the start.
 * This modifies the state of the overlay (which is never used for more
 * than one expression), but not the state of the base static context.
 */

public class StaticContextOverlay implements StaticContext, NamespaceResolver {

    private final StaticContext delegee;
    private UnprefixedElementMatchingPolicy localPolicy = UnprefixedElementMatchingPolicy.UNSPECIFIED;
    private NamespaceUri defaultElementNamespace;
    private final Map<String, NamespaceUri> prologNamespaces = new HashMap<>(4);

    /**
     * Construct a StaticContext object that delegates to an underlying ummutable StaticContext
     * @param delegee the StaticContext to which requests are delegated
     */
    public StaticContextOverlay(StaticContext delegee) {
        this.delegee = delegee;
    }

    public StaticContext getDelegee() {
        return delegee;
    }

    @Override
    public Configuration getConfiguration() {
        return delegee.getConfiguration();
    }

    @Override
    public PackageData getPackageData() {
        return delegee.getPackageData();
    }

    @Override
    public Schema getImportedSchema() {
        return delegee.getImportedSchema();
    }

    @Override
    public XPathContext makeEarlyEvaluationContext() {
        return delegee.makeEarlyEvaluationContext();
    }

    @Override
    public RetainedStaticContext makeRetainedStaticContext() {
        // This is inefficient because it retains the entire static context in run-time memory,
        // but we can assume for now that declaring namespaces in XPath expressions is uncommon.
        RetainedStaticContext rsc = delegee.makeRetainedStaticContext();
        rsc.setNamespaces(this);
        return rsc;
    }

    @Override
    public Location getContainingLocation() {
        return delegee.getContainingLocation();
    }

    @Override
    public void issueWarning(String message, String errorCode, Location locator) {
        delegee.issueWarning(message, errorCode, locator);
    }

    @Override
    public String getSystemId() {
        return delegee.getSystemId();
    }

    @Override
    public String getStaticBaseURI() {
        return delegee.getStaticBaseURI();
    }

    @Override
    public Expression bindVariable(StructuredQName qName) throws XPathException {
        return delegee.bindVariable(qName);
    }

    @Override
    public FunctionLibrary getFunctionLibrary() {
        return delegee.getFunctionLibrary();
    }

    @Override
    public String getDefaultCollationName() {
        return delegee.getDefaultCollationName();
    }

    /**
     * Register a namespace that is explicitly declared in the prolog of the XPath expression or query module.
     *
     * @param prefix The namespace prefix. Must not be null. May be zero-length to declare the default namespace
     *               for elements and types.
     * @param uri    The namespace URI. Must not be null. The value "" (zero-length string) is used
     *               to undeclare a namespace; it is not an error if there is no existing binding for
     *               the namespace prefix. Must not be "##any", which is handled separately.
     * @throws net.sf.saxon.trans.XPathException if the declaration is invalid
     */

    public void declarePrologNamespace(String prefix, NamespaceUri uri) throws XPathException {
        if (prefix == null) {
            throw new NullPointerException("Null prefix supplied to declarePrologNamespace()");
        }
        if (uri == null) {
            throw new NullPointerException("Null namespace URI supplied to declarePrologNamespace()");
        }
        if (prefix.equals("xml") != uri.equals(NamespaceUri.XML)) {
            throw new XPathException("Invalid declaration of the XML namespace", "XQST0070");
        }
        if (prologNamespaces.get(prefix) != null) {
            throw new XPathException("Duplicate declaration of namespace prefix \"" + prefix + '"', "XQST0033");
        } else {
            prologNamespaces.put(prefix, uri);
        }
        if (prefix.isEmpty()) {
            defaultElementNamespace = uri;
        }
    }

    @Override
    public NamespaceUri getDefaultElementNamespace() {
        if (defaultElementNamespace == null) {
            return delegee.getDefaultElementNamespace();
        }
        return defaultElementNamespace;
    }

    @Override
    public void setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy policy) {
        localPolicy = policy;
    }

    @Override
    public UnprefixedElementMatchingPolicy getUnprefixedElementMatchingPolicy() {
        if (localPolicy == UnprefixedElementMatchingPolicy.UNSPECIFIED) {
            return delegee.getUnprefixedElementMatchingPolicy();
        }
        return localPolicy;
    }

    @Override
    public NamespaceUri getDefaultFunctionNamespace() {
        return delegee.getDefaultFunctionNamespace();
    }

    @Override
    public boolean isInBackwardsCompatibleMode() {
        return delegee.isInBackwardsCompatibleMode();
    }

    @Override
    public NamespaceResolver getNamespaceResolver() {
        return this;
    }

    @Override
    public ItemType getRequiredContextItemType() {
        return delegee.getRequiredContextItemType();
    }

    @Override
    public SequenceType getRequiredContextValueType() {
        return delegee.getRequiredContextValueType();
    }

    @Override
    public DecimalFormatManager getDecimalFormatManager() {
        return delegee.getDecimalFormatManager();
    }

    @Override
    public int getXPathVersion() {
        return delegee.getXPathVersion();
    }

    @Override
    public KeyManager getKeyManager() {
        return delegee.getKeyManager();
    }

    @Override
    public ItemType resolveTypeAlias(StructuredQName typeName) {
        return delegee.resolveTypeAlias(typeName);
    }

    @Override
    public OptimizerOptions getOptimizerOptions() {
        return delegee.getOptimizerOptions();
    }

    @Override
    public NamespaceUri getURIForPrefix(String prefix, boolean useDefault) {
        NamespaceUri uri = prologNamespaces.get(prefix);
        if (uri == null) {
            return delegee.getNamespaceResolver().getURIForPrefix(prefix, useDefault);
        }
        if (uri == NamespaceUri.NULL) {
            return null;
        }
        return uri;
    }

    @Override
    public Iterator<String> iteratePrefixes() {
        List<String> prefixes = new ArrayList<>();
        Iterator<String> baseIterator = delegee.getNamespaceResolver().iteratePrefixes();
        while (baseIterator.hasNext()) {
            String prefix = baseIterator.next();
            if (!prologNamespaces.containsKey(prefix)) {
                prefixes.add(prefix);
            }
        }
        for (String prefix : prologNamespaces.keySet()) {
            if (prologNamespaces.get(prefix) != NamespaceUri.NULL) {
                prefixes.add(prefix);
            }
        }
        return prefixes.iterator();
    }
}

