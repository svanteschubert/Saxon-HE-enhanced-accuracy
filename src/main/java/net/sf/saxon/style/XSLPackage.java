////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.packages.VersionedPackageName;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

/**
 * Handler for xsl:package elements. Explicit xsl:package elements are not permitted in Saxon-HE, but
 * implicit packages are created, so the class is present in HE. The top-level module of a stylesheet/package
 * will always be represented by an XSLPackage object, but if the original name was xsl:stylesheet or xsl:transform
 * then this original name will be present as the name of the element.
 */
public class XSLPackage extends XSLModuleRoot {

    private String nameAtt = null;
    private PackageVersion packageVersion = null;
    private boolean declaredModes = true;
    private boolean prepared = false;

    /**
     * Initialise a new ElementImpl with an element name
     *  @param elemName       Integer representing the element name, with namespaces resolved
     * @param elementType    the schema type of the element node
     * @param atts           The attribute list: always null
     * @param parent         The parent node
     * @param sequenceNumber Integer identifying this element within the document
     */
    @Override
    public void initialise(NodeName elemName, SchemaType elementType, AttributeMap atts, NodeInfo parent, int sequenceNumber) {
        super.initialise(elemName, elementType, atts, parent, sequenceNumber);
        processDefaultCollationAttribute();
        declaredModes = getLocalPart().equals("package");
    }

    /**
     * Get the name of the package (the value of its @name attribute)
     * @return the name of the package, or null if the @name attribute is omitted
     */

    public String getName() {
        if (nameAtt == null) {
            prepareAttributes();
        }
        return nameAtt;
    }

    /**
     * Get the requested XSLT version (the value of the @version attribute)
     * @return the value of the @version attribute, times ten as an integer
     */

    public int getVersion() {
        if (version == -1) {
            prepareAttributes();
        }
        return version;
    }

    public VersionedPackageName getNameAndVersion() {
        return new VersionedPackageName(getName(), getPackageVersion());
    }

    /**
     * Get the package version (the value of the @package-version attribute)
     * @return the value of the @package-version attribute, defaulting to "1.0"
     */
    public PackageVersion getPackageVersion() {
        if (packageVersion == null) {
            prepareAttributes();
        }
        return packageVersion;
    }



    @Override
    protected void prepareAttributes() {
        if (prepared) {
            // already done
            return;
        }
        prepared = true;

        String inputTypeAnnotationsAtt = null;
        String packageVersionAtt = null;
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String lexicalName = attName.getDisplayName();
            String value = att.getValue();
            if (lexicalName.equals("name") && getLocalPart().equals("package")) {
                nameAtt = Whitespace.trim(value);
            } else if (lexicalName.equals("id")) {
                // no action
            } else if (lexicalName.equals("version")) {
                if (version == -1) {
                    processVersionAttribute("");
                }
            } else if (lexicalName.equals("package-version") && getLocalPart().equals("package")) {
                packageVersionAtt = Whitespace.trim(value);

            } else if (lexicalName.equals("declared-modes") && getLocalPart().equals("package")) {
                declaredModes = processBooleanAttribute("declared-modes", value);

            } else if (lexicalName.equals("input-type-annotations")) {
                inputTypeAnnotationsAtt = value;
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (packageVersionAtt == null) {
            packageVersion = PackageVersion.ONE;
        } else {
            try {
                packageVersion = new PackageVersion(packageVersionAtt);
            } catch (XPathException ex) {
                compileErrorInAttribute(ex.getMessage(), ex.getErrorCodeLocalPart(), "package-version");
            }
        }

        if (version == -1) {
            version = 30;
            reportAbsence("version");
        }
        if (inputTypeAnnotationsAtt != null) {
            switch (inputTypeAnnotationsAtt) {
                case "strip":
                    //setInputTypeAnnotations(ANNOTATION_STRIP);
                    break;
                case "preserve":
                    //setInputTypeAnnotations(ANNOTATION_PRESERVE);
                    break;
                case "unspecified":
                    //
                    break;
                default:
                    compileError("Invalid value for input-type-annotations attribute. " +
                                         "Permitted values are (strip, preserve, unspecified)", "XTSE0020");
                    break;
            }
        }

    }

    /**
     * Ask whether it is required that modes be explicitly declared
     *
     * @return true if modes referenced within this package must be explicitly declared
     */
    @Override
    public boolean isDeclaredModes() {
        if (nameAtt == null) {
            prepareAttributes();
        }
        return declaredModes;
    }

    /**
     * Recursive walk through the stylesheet to validate all nodes
     * @param decl not used
     * @throws XPathException if invalid
     */


    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        for (NodeInfo child : children()) {
            int fp = child.getFingerprint();
            if (child.getNodeKind() == Type.TEXT ||
                    (child instanceof StyleElement && ((StyleElement) child).isDeclaration()) ||
                    child instanceof DataElement ) {
                // all is well
            } else if (child instanceof StyleElement) {
                if (getLocalPart().equals("package") &&
                        (fp == StandardNames.XSL_USE_PACKAGE || fp == StandardNames.XSL_EXPOSE)) {
                    // all is well
                } else if (!NamespaceConstant.XSLT.equals(child.getURI()) && !"".equals(child.getURI())) {
                    // elements in other namespaces are allowed and ignored
                } else if (child instanceof AbsentExtensionElement && ((StyleElement) child).forwardsCompatibleModeIsEnabled()) {
                    // this is OK: an unknown XSLT element is allowed in forwards compatibility mode
                } else if (NamespaceConstant.XSLT.equals(child.getURI())) {
                    if (child instanceof AbsentExtensionElement) {
                        // then the error will be reported later
                    } else {
                        ((StyleElement) child).compileError(
                                "Element " + child.getDisplayName() +
                                        " must not appear directly within " + getDisplayName(), "XTSE0010");
                    }

                } else {
                    ((StyleElement) child).compileError("Element " + child.getDisplayName() +
                                                                " must not appear directly within " + getDisplayName() +
                                                                " because it is not in a namespace", "XTSE0130");
                }
            }
        }

        if (declaredModes) {
            String defaultMode = getAttributeValue("default-mode");
            if (defaultMode != null && getPrincipalStylesheetModule().getRuleManager().obtainMode(getDefaultMode(), false) == null) {
                compileError("The default mode " + defaultMode + " has not been declared in an xsl:mode declaration", "XTSE3085");
            }
        }

    }



}
