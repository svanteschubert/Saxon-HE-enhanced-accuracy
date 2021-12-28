////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SchemaURIResolver;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.LicenseException;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.NodeImpl;
import net.sf.saxon.type.SchemaException;
import net.sf.saxon.value.Whitespace;


/**
 * Compile-time representation of an xsl:import-schema declaration
 * in a stylesheet
 */

public class XSLImportSchema extends StyleElement {

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     *
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    @Override
    public void prepareAttributes() {

        String namespace = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            if (f.equals("schema-location")) {
                //
            } else if (f.equals("namespace")) {
                namespace = Whitespace.trim(value);
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if ("".equals(namespace)) {
            compileError("The zero-length string is not a valid namespace URI. " +
                    "For a schema with no namespace, omit the namespace attribute");
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkTopLevel("XTSE0010", false);
    }

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        //
    }

    public void readSchema() throws XPathException {
        try {
            String schemaLoc = Whitespace.trim(getAttributeValue("", "schema-location"));
            String namespace = Whitespace.trim(getAttributeValue("", "namespace"));
            if (namespace == null) {
                namespace = "";
            } else {
                namespace = namespace.trim();
            }
            Configuration config = getConfiguration();
            try {
                config.checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT,
                                            "xsl:import-schema",
                                            getPackageData().getLocalLicenseId());
            } catch (LicenseException err) {
                XPathException xe = new XPathException(err);
                xe.setErrorCode("XTSE1650");
                xe.setLocator(this);
                throw xe;
            }
            NodeImpl inlineSchema = null;
            for (NodeImpl child : children()) {
                if (inlineSchema != null) {
                    compileError(getDisplayName() + " must not have more than one child element");
                }
                inlineSchema = child;
                if (inlineSchema.getFingerprint() != StandardNames.XS_SCHEMA) {
                    compileError("The only child element permitted for " + getDisplayName() + " is xs:schema");
                }
                if (schemaLoc != null) {
                    compileError("The schema-location attribute must be absent if an inline schema is present", "XTSE0215");
                }

                if (namespace.isEmpty()) {
                    namespace = inlineSchema.getAttributeValue("", "targetNamespace");
                    if (namespace == null) {
                        namespace = "";
                    }
                }

                namespace = config.readInlineSchema(inlineSchema, namespace,
                        getCompilation().getCompilerInfo().getErrorReporter());
                getPrincipalStylesheetModule().addImportedSchema(namespace);
            }
            if (inlineSchema != null) {
                return;
            }
            if (namespace.equals(NamespaceConstant.XML) ||
                namespace.equals(NamespaceConstant.FN)||
                namespace.equals(NamespaceConstant.SCHEMA_INSTANCE)) {
                config.addSchemaForBuiltInNamespace(namespace);
                getPrincipalStylesheetModule().addImportedSchema(namespace);
                return;
            }
            boolean namespaceKnown = config.isSchemaAvailable(namespace);
            if (schemaLoc == null && !namespaceKnown) {
                compileWarning("No schema for this namespace is known, " +
                        "and no schema-location was supplied, so no schema has been imported",
                    SaxonErrorCode.SXWN9006);
                return;
            }
            if (namespaceKnown && !config.getBooleanProperty(Feature.MULTIPLE_SCHEMA_IMPORTS)) {
                if (schemaLoc != null) {
                    compileWarning("The schema document at " + schemaLoc +
                        " is ignored because a schema for this namespace is already loaded", SaxonErrorCode.SXWN9006);
                }
            }
            if (!namespaceKnown) {
                PipelineConfiguration pipe = config.makePipelineConfiguration();
                SchemaURIResolver schemaResolver = config.makeSchemaURIResolver(
                        getCompilation().getCompilerInfo().getURIResolver());
                pipe.setSchemaURIResolver(schemaResolver);
                pipe.setErrorReporter(getCompilation().getCompilerInfo().getErrorReporter());
                namespace = config.readSchema(pipe, getBaseURI(), schemaLoc, namespace);
            }
            getPrincipalStylesheetModule().addImportedSchema(namespace);
        } catch (SchemaException err) {
            String errorCode = err.getErrorCodeLocalPart() == null ? "XTSE0220" : err.getErrorCodeLocalPart();
            compileError(err.getMessage(), errorCode);
        }

    }


    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // No action. The effect of import-schema is compile-time only
    }
}

