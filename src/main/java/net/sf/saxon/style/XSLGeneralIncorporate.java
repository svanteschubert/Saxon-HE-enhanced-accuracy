////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.DocumentFn;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.DocumentKey;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.ElementImpl;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;


/**
 * Abstract class to represent an xsl:include or xsl:import element in the stylesheet.
 * The xsl:include and xsl:import elements have mandatory attribute href
 */

public abstract class XSLGeneralIncorporate extends StyleElement {

    private String href;
    private DocumentImpl targetDoc;

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


    /**
     * isImport() returns true if this is an xsl:import declaration rather than an xsl:include
     *
     * @return true if this is an xsl:import declaration, false if it is an xsl:include
     */

    public abstract boolean isImport();

    @Override
    public void prepareAttributes() {

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("href")) {
                href = Whitespace.trim(value);
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (href == null) {
            reportAbsence("href");
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        validateInstruction();
    }

    public void validateInstruction() {
        checkEmpty();
        checkTopLevel(isImport() ? "XTSE0190" : "XTSE0170", false);
    }

    /**
     * Get the included or imported stylesheet module
     *
     * @param importer   the module that requested the include or import (used to check for cycles)
     * @param precedence the import precedence to be allocated to the included or imported module
     * @return the xsl:stylesheet element at the root of the included/imported module
     */

    /*@Nullable*/
    public StylesheetModule getIncludedStylesheet(StylesheetModule importer, int precedence) {

        if (href == null) {
            // error already reported
            return null;
        }

        //checkEmpty();
        //checkTopLevel((this instanceof XSLInclude ? "XTSE0170" : "XTSE0190"));

        try {
            PrincipalStylesheetModule psm = importer.getPrincipalStylesheetModule();
            //PreparedStylesheet pss = psm.getPreparedStylesheet();
            URIResolver resolver = getCompilation().getCompilerInfo().getURIResolver();
            Configuration config = getConfiguration();
            XSLStylesheet includedSheet;
            StylesheetModule incModule;

            DocumentKey key = DocumentFn.computeDocumentKey(href, getBaseURI(), getCompilation().getPackageData(), resolver, false);
            includedSheet = (XSLStylesheet)psm.getStylesheetDocument(key);
            if (includedSheet != null) {
                // we already have the stylesheet document in cache; but we need to create a new module,
                // because the import precedence might be different. See test impincl30.
                incModule = new StylesheetModule(includedSheet, precedence);
                incModule.setImporter(importer);

                // check for recursion
                if (checkForRecursion(importer, incModule.getRootElement())) {
                    return null;
                }

            } else {

//                Map<DocumentURI, TreeInfo> map = getCompilation().getStylesheetModules();
//                DocumentImpl includedDoc = (DocumentImpl)map.get(key);
                DocumentImpl includedDoc = targetDoc;
                assert includedDoc != null;
                ElementImpl outermost = includedDoc.getDocumentElement();

                if (outermost instanceof LiteralResultElement) {
                    includedDoc = ((LiteralResultElement) outermost).makeStylesheet(false);
                    outermost = includedDoc.getDocumentElement();
                }

                if (!(outermost instanceof XSLStylesheet)) {
                    String verb = this instanceof XSLImport ? "Imported" : "Included";
                    compileError(verb + " document " + href + " is not a stylesheet", "XTSE0165");
                    return null;
                }
                includedSheet = (XSLStylesheet) outermost;
                psm.putStylesheetDocument(key, includedSheet);

                incModule = new StylesheetModule(includedSheet, precedence);
                incModule.setImporter(importer);
                ComponentDeclaration decl = new ComponentDeclaration(incModule, includedSheet);
                includedSheet.validate(decl);

                if (includedSheet.validationError != null) {
                    if (reportingCircumstances == OnFailure.REPORT_ALWAYS) {
                        includedSheet.compileError(includedSheet.validationError);
                    } else if (includedSheet.reportingCircumstances == OnFailure.REPORT_UNLESS_FORWARDS_COMPATIBLE
                        // not sure if this can still happen
                        /*&& !incSheet.forwardsCompatibleModeIsEnabled()*/) {
                        includedSheet.compileError(includedSheet.validationError);
                    }
                }
            }

            incModule.spliceIncludes();          // resolve any nested imports and includes;

            // Check the consistency of input-type-annotations
            //assert thisSheet != null;
            importer.setInputTypeAnnotations(includedSheet.getInputTypeAnnotationsAttribute() |
                    incModule.getInputTypeAnnotations());

            return incModule;

        } catch (XPathException err) {
            err.setErrorCode("XTSE0165");
            err.setIsStaticError(true);
            compileError(err);
            return null;
        }
    }

    public void setTargetDocument(DocumentImpl doc) {
        this.targetDoc = doc;
    }

    private boolean checkForRecursion(StylesheetModule importer, Source source) {
        StylesheetModule anc = importer;

        if (source.getSystemId() != null) {
            while (anc != null) {
                if (DocumentKey.normalizeURI(source.getSystemId())
                        .equals(DocumentKey.normalizeURI(anc.getRootElement().getSystemId()))) {
                    compileError("A stylesheet cannot " + getLocalPart() + " itself",
                            this instanceof XSLInclude ? "XTSE0180" : "XTSE0210");
                    return true;
                }
                anc = anc.getImporter();
            }
        }
        return false;
    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) {
        // no action. The node will never be compiled, because it replaces itself
        // by the contents of the included file.
    }
}

