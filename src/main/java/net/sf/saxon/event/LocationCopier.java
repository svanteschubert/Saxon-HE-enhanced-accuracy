////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.NodeInfo;

/**
 * A Receiver that can be inserted into an event pipeline to copy location information.
 * The class is used when it is necessary to copy a subtree along with its location information;
 * for example, when copying an inline schema within a stylesheet to a separate schema document.
 * <p><i>Note: prior to 9.2, the LocationCopier was a ProxyReceiver that passed all events on the
 * pipeline unchanged. It no longer does this, instead it is found as the LocationProvider on a
 * pipeline, but does not itself see the pipeline events.</i></p>
 */

public class LocationCopier implements CopyInformee<Location> {

    private final boolean wholeDocument;
    private String systemId;

    public LocationCopier(boolean wholeDocument, String systemId) {
        this.wholeDocument = wholeDocument;
        this.systemId = systemId;
    }

    /**
     * Provide information about the node being copied. This method is called immediately before
     * the startElement call for the element node in question.
     *
     * @param element the node being copied, which must be an element node
     */

    @Override
    public Location notifyElementNode(NodeInfo element) {
        String systemId = wholeDocument ? element.getSystemId() : element.getBaseURI();
        // The logic behind this is that if we are copying the whole document, we will be copying all
        // the relevant xml:base attributes; so retaining the systemId values is sufficient to enable
        // the base URIs of the nodes to be preserved. But if we only copy an element (for example
        // an xsl:import-schema element - see test schema091 - then its base URI might be affected
        // by xml:base attributes that aren't being copied. Ideally we would have two separate properties,
        // but XDM doesn't work that way.
        int lineNumber = element.getLineNumber();
        int columnNumber = element.getColumnNumber();
        return new Loc(systemId, lineNumber, columnNumber);
    }

    public String getSystemId() {
        return systemId;
    }
}

