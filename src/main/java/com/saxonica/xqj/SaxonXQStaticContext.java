////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.query.StaticQueryContext;

import javax.xml.xquery.XQConstants;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItemType;
import javax.xml.xquery.XQStaticContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Saxon implementation of the XQJ XQStaticContext interface
 */
public class SaxonXQStaticContext implements XQStaticContext {

    private Configuration config;
    private int bindingMode = XQConstants.BINDING_MODE_IMMEDIATE;
    private int holdability = XQConstants.HOLDTYPE_HOLD_CURSORS_OVER_COMMIT;
    private int scrollability = XQConstants.SCROLLTYPE_FORWARD_ONLY;
    /*@NotNull*/ private Map<String, String> namespaces = new HashMap<>();
    private String baseURI = "";
    boolean preserveBoundarySpace = false;
    boolean constructionModeIsPreserve = false;
    boolean inheritNamespaces = true;
    boolean preserveNamespaces = true;
    boolean emptyLeast = true;
    boolean isOrdered = true;
    /*@Nullable*/ SaxonXQItemType contextItemStaticType = null;
    String defaultCollationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
    String defaultElementNamespace = "";
    String defaultFunctionNamespace = NamespaceConstant.FN;

    /**
     * Create a SaxonXQStaticContext object, the Saxon implementation of XQStaticContext in XQJ
     *
     * @param config the Saxon configuration
     */

    public SaxonXQStaticContext(Configuration config) {
        this.config = config;
    }

    /**
     * Create a SaxonXQStaticContext object as a copy of another SaxonXQStaticContext object
     *
     * @param sc the static context to be copied
     */

    public SaxonXQStaticContext(/*@NotNull*/ SaxonXQStaticContext sc) {
        this.config = sc.config;
        this.bindingMode = sc.bindingMode;
        this.holdability = sc.holdability;
        this.scrollability = sc.scrollability;
        this.namespaces = new HashMap<>(sc.namespaces);
        this.baseURI = sc.baseURI;
        this.preserveBoundarySpace = sc.preserveBoundarySpace;
        this.constructionModeIsPreserve = sc.constructionModeIsPreserve;
        this.inheritNamespaces = sc.inheritNamespaces;
        this.preserveNamespaces = sc.preserveNamespaces;
        this.emptyLeast = sc.emptyLeast;
        this.isOrdered = sc.isOrdered;
        this.contextItemStaticType = sc.contextItemStaticType;
        this.defaultCollationName = sc.defaultCollationName;
        this.defaultElementNamespace = sc.defaultElementNamespace;
        this.defaultFunctionNamespace = sc.defaultFunctionNamespace;
    }

    /**
     * Get a new Saxon StaticQueryContext object holding the information held in this
     * XQStaticContext
     *
     * @return a newly constructed StaticQueryContext object
     */

    /*@NotNull*/
    protected StaticQueryContext getSaxonStaticQueryContext() {
        StaticQueryContext sqc = config.newStaticQueryContext();
        sqc.setSchemaAware(config.isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION));
        sqc.setBaseURI(baseURI);
        sqc.setConstructionMode(constructionModeIsPreserve ? Validation.PRESERVE : Validation.STRIP);
        sqc.setDefaultElementNamespace(defaultElementNamespace);
        sqc.setDefaultFunctionNamespace(defaultFunctionNamespace);
        sqc.setEmptyLeast(emptyLeast);
        sqc.setInheritNamespaces(inheritNamespaces);
        sqc.setPreserveBoundarySpace(preserveBoundarySpace);
        sqc.setPreserveNamespaces(preserveNamespaces);
        if (contextItemStaticType != null) {
            sqc.setRequiredContextItemType(contextItemStaticType.getSaxonItemType());
        }
        for (Map.Entry<String, String> e : namespaces.entrySet()) {
            sqc.declareNamespace(e.getKey(), e.getValue());
        }
        return sqc;
    }


    @Override
    public void declareNamespace(String prefix, String uri) throws XQException {
        checkNotNull(prefix);
        checkNotNull(uri);
        if (uri.isEmpty()) {
            namespaces.remove(prefix);
        } else {
            namespaces.put(prefix, uri);
        }
    }

    @Override
    public String getBaseURI() {
        return baseURI;
    }


    @Override
    public int getBindingMode() {
        return bindingMode;
    }

    @Override
    public int getBoundarySpacePolicy() {
        return preserveBoundarySpace
                ? XQConstants.BOUNDARY_SPACE_PRESERVE
                : XQConstants.BOUNDARY_SPACE_STRIP;
    }

    @Override
    public int getConstructionMode() {
        return constructionModeIsPreserve
                ? XQConstants.CONSTRUCTION_MODE_PRESERVE
                : XQConstants.CONSTRUCTION_MODE_STRIP;
    }


    /*@Nullable*/
    @Override
    public XQItemType getContextItemStaticType() {
        return contextItemStaticType;
    }

    @Override
    public int getCopyNamespacesModeInherit() {
        return inheritNamespaces ?
                XQConstants.COPY_NAMESPACES_MODE_INHERIT :
                XQConstants.COPY_NAMESPACES_MODE_NO_INHERIT;
    }

    @Override
    public int getCopyNamespacesModePreserve() {
        return preserveNamespaces ?
                XQConstants.COPY_NAMESPACES_MODE_PRESERVE :
                XQConstants.COPY_NAMESPACES_MODE_NO_PRESERVE;
    }

    @Override
    public String getDefaultCollation() {
        return defaultCollationName;
    }

    @Override
    public String getDefaultElementTypeNamespace() {
        return defaultElementNamespace;
    }

    @Override
    public String getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    @Override
    public int getDefaultOrderForEmptySequences() {
        return emptyLeast ?
                XQConstants.DEFAULT_ORDER_FOR_EMPTY_SEQUENCES_LEAST :
                XQConstants.DEFAULT_ORDER_FOR_EMPTY_SEQUENCES_GREATEST;
    }

    /*@NotNull*/
    @Override
    public String[] getNamespacePrefixes() {
        String[] result = new String[namespaces.size()];
        Iterator iter = namespaces.keySet().iterator();
        for (int i = 0; i < result.length; i++) {
            if (iter.hasNext()) {
                result[i] = (String) iter.next();
            }
        }
        return result;
    }

    @Override
    public String getNamespaceURI(String prefix) throws XQException {
        checkNotNull(prefix);
        String uri = namespaces.get(prefix);
        if (uri == null) {
            throw new XQException("Unknown prefix");
        }
        return uri;
    }

    @Override
    public int getOrderingMode() {
        return isOrdered
                ? XQConstants.ORDERING_MODE_ORDERED
                : XQConstants.ORDERING_MODE_UNORDERED;
    }

    @Override
    public int getHoldability() {
        return holdability;
    }

    @Override
    public int getQueryLanguageTypeAndVersion() {
        return XQConstants.LANGTYPE_XQUERY;
    }

    @Override
    public int getQueryTimeout() {
        return 0;
    }

    @Override
    public int getScrollability() {
        return scrollability;
    }


    @Override
    public void setBaseURI(String baseUri) throws XQException {
        checkNotNull(baseUri);
        this.baseURI = baseUri;
    }

    @Override
    public void setBindingMode(int bindingMode) throws XQException {
        switch (bindingMode) {
            case XQConstants.BINDING_MODE_IMMEDIATE:
            case XQConstants.BINDING_MODE_DEFERRED:
                this.bindingMode = bindingMode;
                break;
            default:
                throw new XQException("Invalid value for binding mode - " + bindingMode);
        }
    }

    @Override
    public void setBoundarySpacePolicy(int policy) throws XQException {
        switch (policy) {
            case XQConstants.BOUNDARY_SPACE_PRESERVE:
                preserveBoundarySpace = true;
                break;
            case XQConstants.BOUNDARY_SPACE_STRIP:
                preserveBoundarySpace = false;
                break;
            default:
                throw new XQException("Invalid value for boundary space policy - " + policy);
        }
    }

    @Override
    public void setConstructionMode(int mode) throws XQException {
        switch (mode) {
            case XQConstants.CONSTRUCTION_MODE_PRESERVE:
                constructionModeIsPreserve = true;
                break;
            case XQConstants.CONSTRUCTION_MODE_STRIP:
                constructionModeIsPreserve = false;
                break;
            default:
                throw new XQException("Invalid value for construction mode - " + mode);
        }
    }

    @Override
    public void setContextItemStaticType(/*@Nullable*/ XQItemType contextItemType) {
        this.contextItemStaticType = (SaxonXQItemType) contextItemType;
    }

    @Override
    public void setCopyNamespacesModeInherit(int mode) throws XQException {
        switch (mode) {
            case XQConstants.COPY_NAMESPACES_MODE_INHERIT:
                inheritNamespaces = true;
                break;
            case XQConstants.COPY_NAMESPACES_MODE_NO_INHERIT:
                inheritNamespaces = false;
                break;
            default:
                throw new XQException("Invalid value for namespaces inherit mode - " + mode);
        }
    }

    @Override
    public void setCopyNamespacesModePreserve(int mode) throws XQException {
        switch (mode) {
            case XQConstants.COPY_NAMESPACES_MODE_PRESERVE:
                preserveNamespaces = true;
                break;
            case XQConstants.COPY_NAMESPACES_MODE_NO_PRESERVE:
                preserveNamespaces = false;
                break;
            default:
                throw new XQException("Invalid value for namespaces preserve mode - " + mode);
        }
    }

    @Override
    public void setDefaultCollation(String uri) throws XQException {
        checkNotNull(uri);
        defaultCollationName = uri;
    }

    @Override
    public void setDefaultElementTypeNamespace(String uri) throws XQException {
        checkNotNull(uri);
        defaultElementNamespace = uri;
    }

    @Override
    public void setDefaultFunctionNamespace(String uri) throws XQException {
        checkNotNull(uri);
        defaultFunctionNamespace = uri;
    }

    @Override
    public void setDefaultOrderForEmptySequences(int order) throws XQException {
        switch (order) {
            case XQConstants.DEFAULT_ORDER_FOR_EMPTY_SEQUENCES_GREATEST:
                emptyLeast = false;
                break;
            case XQConstants.DEFAULT_ORDER_FOR_EMPTY_SEQUENCES_LEAST:
                emptyLeast = true;
                break;
            default:
                throw new XQException("Invalid value for default order for empty sequences - " + order);
        }
    }

    @Override
    public void setOrderingMode(int mode) throws XQException {
        switch (mode) {
            case XQConstants.ORDERING_MODE_ORDERED:
                isOrdered = true;
                break;
            case XQConstants.ORDERING_MODE_UNORDERED:
                isOrdered = false;
                break;
            default:
                throw new XQException("Invalid ordering mode - " + mode);
        }
    }

    @Override
    public void setQueryTimeout(int seconds) throws XQException {
        if (seconds < 0) {
            throw new XQException("Query timeout must not be negative");
        }
        // no-op
    }

    @Override
    public void setHoldability(int holdability) throws XQException {
        switch (holdability) {
            case XQConstants.HOLDTYPE_HOLD_CURSORS_OVER_COMMIT:
            case XQConstants.HOLDTYPE_CLOSE_CURSORS_AT_COMMIT:
                this.holdability = holdability;
                break;
            default:
                throw new XQException("Invalid holdability value - " + holdability);
        }
    }

    @Override
    public void setQueryLanguageTypeAndVersion(int langtype) throws XQException {
        if (langtype != XQConstants.LANGTYPE_XQUERY) {
            throw new XQException("XQueryX is not supported");
        }
    }

    @Override
    public void setScrollability(int scrollability) throws XQException {
        switch (scrollability) {
            case XQConstants.SCROLLTYPE_FORWARD_ONLY:
            case XQConstants.SCROLLTYPE_SCROLLABLE:
                this.scrollability = scrollability;
                break;
            default:
                throw new XQException("Invalid scrollability value - " + scrollability);
        }
    }

    protected void checkNotNull(/*@Nullable*/ Object arg) throws XQException {
        if (arg == null) {
            throw new XQException("Argument is null");
        }
    }

}

