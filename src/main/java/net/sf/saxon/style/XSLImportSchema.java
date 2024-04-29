////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.LicenseException;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
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
    protected void prepareAttributes() {

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
            String schemaLoc = Whitespace.trim(getAttributeValue(NamespaceUri.NULL, "schema-location"));
            String namespace = Whitespace.trim(getAttributeValue(NamespaceUri.NULL, "namespace"));
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
                throw new XPathException(err).withErrorCode("XTSE1650").withLocation(this);
            }
            NodeInfo inlineSchema = null;
            NamespaceUri targetNamespace = null;
            for (NodeInfo child : children()) {
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
                    namespace = inlineSchema.getAttributeValue(NamespaceUri.NULL, "targetNamespace");
                    if (namespace == null) {
                        namespace = "";
                    }
                }

                targetNamespace = NamespaceUri.of(namespace);
                targetNamespace = config.readInlineSchema(inlineSchema, targetNamespace,
                        getCompilation().getCompilerInfo().getErrorReporter());
                getPrincipalStylesheetModule().addImportedSchema(targetNamespace);
            }
            if (inlineSchema != null) {
                return;
            }
            if (namespace.equals(NamespaceConstant.XML) ||
                    namespace.equals(NamespaceConstant.FN) ||
                    namespace.equals(NamespaceConstant.SCHEMA_INSTANCE)) {
                targetNamespace = NamespaceUri.of(namespace);
                config.addSchemaForBuiltInNamespace(targetNamespace);
                getPrincipalStylesheetModule().addImportedSchema(targetNamespace);
                return;
            }
            targetNamespace = NamespaceUri.of(namespace);
            boolean namespaceKnown = config.isSchemaAvailable(targetNamespace);
            if (schemaLoc == null && !namespaceKnown) {
                issueWarning("No schema for this namespace is known, " +
                        "and no schema-location was supplied, so no schema has been imported",
                    SaxonErrorCode.SXWN9006);
                return;
            }
            if (namespaceKnown && !config.getBooleanProperty(Feature.MULTIPLE_SCHEMA_IMPORTS)) {
                if (schemaLoc != null) {
                    issueWarning("The schema document at " + schemaLoc +
                        " is ignored because a schema for this namespace is already loaded", SaxonErrorCode.SXWN9006);
                }
            }
            if (!namespaceKnown) {
                PipelineConfiguration pipe = config.makePipelineConfiguration();
//                SchemaURIResolver schemaResolver = config.makeSchemaURIResolver(
//                        getCompilation().getCompilerInfo().getResourceResolver());
//                pipe.setSchemaURIResolver(schemaResolver);
                pipe.setErrorReporter(getCompilation().getCompilerInfo().getErrorReporter());
                targetNamespace = config.readSchema(pipe, getBaseURI(), schemaLoc, targetNamespace);
            }
            getPrincipalStylesheetModule().addImportedSchema(targetNamespace);
        } catch (SchemaException err) {
            if (err.getErrorCodeQName() == null) {
                compileError(err.getMessage(), "XTSE0220");
            } else {
                compileError(err.getMessage(), err.getErrorCodeQName());
            }
        }

    }


    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // No action. The effect of import-schema is compile-time only
    }
}

