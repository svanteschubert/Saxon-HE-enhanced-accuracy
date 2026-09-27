////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.packages;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.event.Sink;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import javax.xml.transform.stream.StreamSource;
import java.io.File;

/**
 * The PackageInspector class is a Receiver that looks at an incoming stylesheet document
 * and extracts the package name and version from the root element; parsing is then
 * abandoned.
 *
 */

public class PackageInspector extends ProxyReceiver {

    private boolean isSefFile;
    private String packageName;
    private String packageVersion = "1";
    private int elementCount = 0;
    private String diagnostics;

    PackageInspector(PipelineConfiguration pipe) {
        super(new Sink(pipe));
    }

    /**
     * Abort the parse when the first start element tag is found
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        if (elementCount++ >= 1) {
            // abort the parse when the second start element tag is found
            throw new XPathException("#start#");
        }
        isSefFile = elemName.hasURI(NamespaceUri.SAXON_XSLT_EXPORT);
        if (attributes.get(NamespaceUri.NULL, "name") == null) {
            diagnostics = "Top level element " + elemName.getStructuredQName().getEQName() + " has no @name attribute";
        } else {
            packageName = attributes.get(NamespaceUri.NULL, "name").getValue();
        }
        if (attributes.get(NamespaceUri.NULL, "package-version") != null) {
            packageVersion = attributes.get(NamespaceUri.NULL, "package-version").getValue();
        }
        if (attributes.get(NamespaceUri.NULL, "packageVersion") != null) {
            packageVersion = attributes.get(NamespaceUri.NULL, "packageVersion").getValue();
        }
        AttributeInfo saxonVersion = attributes.get(NamespaceUri.NULL, "saxonVersion");
        if (saxonVersion != null) {
            if (saxonVersion.getValue().startsWith("9")) {
                throw new XPathException("Saxon " + Version.getProductVersion() + " cannot load a SEF file created using version " + saxonVersion.getValue());
            }
        }
    }
    
    private VersionedPackageName getNameAndVersion() {
        if (packageName == null) {
            return null;
        }
        try {
            return new VersionedPackageName(packageName, packageVersion);
        } catch (XPathException e) {
            return null;
        }
    }

    public PackageDetails getPackageDetails(File top, Configuration config) throws XPathException {
        try {
            ParseOptions options = new ParseOptions()
                    .withDTDValidationMode(Validation.SKIP)
                    .withSchemaValidationMode(Validation.SKIP);
            Sender.send(new StreamSource(top), this, options);
        } catch (XPathException e) {
            // early exit is expected
            if (!e.getMessage().equals("#start#")) {
                throw e;
            }
        }
        VersionedPackageName vp = getNameAndVersion();
        if (vp == null) {
            return null;
        } else {
            PackageDetails details = new PackageDetails();
            details.nameAndVersion = vp;
            if (isSefFile) {
                details.exportLocation = new StreamSource(top);
            } else {
                details.sourceLocation = new StreamSource(top);
            }
            return details;
        }
    }

    public String getDiagnostics() {
        return diagnostics;
    }
}
