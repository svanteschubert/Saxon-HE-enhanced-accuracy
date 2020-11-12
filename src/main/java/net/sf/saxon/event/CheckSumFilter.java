////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

/**
 * A filter to go on a Receiver pipeline and calculate a checksum of the data passing through the pipeline.
 * Optionally the filter will also check any checksum (represented by a processing instruction with name
 * SIGMA) found in the file.
 *
 * <p>The checksum takes account of element, attribute, and text nodes only. The order of attributes
 * within an element makes no difference.</p>
 */

public class CheckSumFilter extends ProxyReceiver {

    private final static boolean DEBUG = false;

    private int checksum = 0;
    private int sequence = 0;
    private boolean checkExistingChecksum = false;
    private boolean checksumCorrect = false;
    private boolean checksumFound = false;

    public final static String SIGMA = "\u03A3";

    public CheckSumFilter(Receiver nextReceiver) {
        super(nextReceiver);
    }

    /**
     * Ask the filter to check any existing checksums found in the file
     * @param check true if existing checksums are to be checked
     */

    public void setCheckExistingChecksum(boolean check) {
        this.checkExistingChecksum = check;
    }

    @Override
    public void startDocument(int properties) throws XPathException {
        if (DEBUG) {
            System.err.println("CHECKSUM - START DOC");
        }
        super.startDocument(properties);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES};
     *                            the default (0) means
     */
    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        checksum ^= hash(item.toString(), sequence++);
        if (DEBUG) {
            System.err.println("After append: " + Integer.toHexString(checksum));
        }
        super.append(item, locationId, copyNamespaces);
    }

    /**
     * Character data
     */
    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (!Whitespace.isWhite(chars)) {
            checksum ^= hash(chars, sequence++);
            if (DEBUG) {
                System.err.println("After characters " + chars + ": " + Integer.toHexString(checksum));
            }
        }
        super.characters(chars, locationId, properties);
    }

    /**
     * Notify the start of an element
     */
    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        checksum ^= hash(elemName, sequence++);
        if (DEBUG) {
            System.err.println("After startElement " + elemName.getDisplayName() + ": " + checksum);
        }
        checksumCorrect = false;
        for (AttributeInfo att : attributes) {
            checksum ^= hash(att.getNodeName(), sequence);
            checksum ^= hash(att.getValue(), sequence);
            if (DEBUG) {
                System.err.println("After attribute " + att.getNodeName().getDisplayName() + ": " + checksum +
                                           "(" + hash(att.getNodeName(), sequence) + "," + hash(att.getValue(), sequence) + ")");
            }
        }
        super.startElement(elemName, type, attributes, namespaces, location, properties);
    }

    /**
     * End of element
     */
    @Override
    public void endElement() throws XPathException {
        checksum ^= 1;
        if (DEBUG) {
            System.err.println("After endElement: " + checksum);
        }
        super.endElement();
    }

    /**
     * Processing Instruction
     */
    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (target.equals(SIGMA)) {
            checksumFound = true;
            if (checkExistingChecksum) {
                try {
                    int found = (int) Long.parseLong("0" + data, 16);
                    checksumCorrect = found == checksum;
                } catch (NumberFormatException e) {
                    checksumCorrect = false;
                }
            }
        }
        super.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Ask whether a checksum has been found
     * @return true if a checksum processing instruction has been found (whether or not the checksum was correct)
     */

    public boolean isChecksumFound() {
        return checksumFound;
    }

    /**
     * Get the accumulated checksum
     * @return the checksum of the events passed through the filter so far.
     */

    public int getChecksum() {
        return checksum;
    }

    /**
     * Ask if a correct checksum has been found in the file
     * @return true if a checksum has been found, if its value matches, and if no significant data has been encountered
     * after the checksum
     */

    public boolean isChecksumCorrect() {
        return checksumCorrect || "skip".equals(System.getProperty("saxon-checksum"));
    }

    private int hash(CharSequence s, int sequence) {
        int h = sequence<<8;
        for (int i=0; i<s.length(); i++) {
            h = (h<<1) + s.charAt(i);
        }
        return h;
    }

    private int hash(NodeName n, int sequence) {
        //System.err.println("hash(" + n.getLocalPart() + ") " + hash(n.getLocalPart(), sequence) + "/" + hash(n.getURI(), sequence));
        return hash(n.getLocalPart(), sequence) ^ hash(n.getURI(), sequence);
    }
}

