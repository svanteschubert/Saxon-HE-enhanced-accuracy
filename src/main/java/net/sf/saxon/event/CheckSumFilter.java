////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.lib.DigestMaker;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

import java.util.Arrays;
import java.util.HashMap;

/**
 * A filter to go on a Receiver pipeline and calculate a checksum of the data passing through the pipeline.
 * Optionally the filter will also check any checksum (represented by a processing instruction with name
 * SIGMA) found in the file.
 *
 * <p>The checksum takes account of element, attribute, and text nodes only. The order of attributes
 * within an element makes no difference.</p>
 *
 * <p>For the SIGMA2 checksum, the order of attributes within an element does make a difference.</p>
 */

public class CheckSumFilter extends ProxyReceiver {
    private final static boolean DEBUG = false;
    private final DigestMaker digest;
    private int checksum = 0;
    private int sequence = 0;
    private boolean checkExistingChecksum = false;
    private boolean checksumCorrect = false;
    private boolean checksumFound = false;
    private boolean digestCorrect = false;
    private boolean digestFound = false;
    private boolean requireDigest = false;
    private boolean rootElement;

    // The code that uses the checksum asks for it after the document element
    // has been processed but before endDocument is called, so we use depth
    // to keep track of when we end the document element (we need to make an
    // adjustment to the checksum).
    private int depth = 0;

    // We have to generate different checksum values for XML and JSON outputs
    private String target = "unknown";

    public final static String SIGMA = "Σ";
    public final static String SIGMA2 = "Σ2";

    public CheckSumFilter(Receiver nextReceiver) {
        super(nextReceiver);
        rootElement = true;
        digest = new DigestMaker();
    }

    /**
     * Ask the filter to check any existing checksums found in the file
     * @param check true if existing checksums are to be checked
     */

    public void setCheckExistingChecksum(boolean check) {
        this.checkExistingChecksum = check;
    }

    @CSharpReplaceBody(code="")
    private static void trace(String message) {
        if (DEBUG) {
            System.err.println(message);
        }
    }

    @Override
    public void startDocument(int properties) throws XPathException {
        trace("CHECKSUM - START DOC");
        super.startDocument(properties);
    }

    @Override
    public void endDocument() throws XPathException {
        trace("Σ ::= " + Integer.toHexString(checksum));
        nextReceiver.endDocument();
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
        trace("After append: " + Integer.toHexString(checksum));
        super.append(item, locationId, copyNamespaces);
    }

    /**
     * Character data
     */
    @Override
    public void characters(UnicodeString chars, Location locationId, int properties) throws XPathException {
        if (!Whitespace.isAllWhite(chars)) {
            checksum ^= hash(chars.toString(), sequence++);
            trace("After characters " + chars + ": " + Integer.toHexString(checksum));
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
        trace("After startElement " + elemName.getDisplayName() + ": " + checksum);
        checksumCorrect = false;
        depth++;

        if (rootElement) {
            rootElement = false;

            boolean scm_schema = elemName.getNamespaceUri() == NamespaceUri.of("http://ns.saxonica.com/schema-component-model")
                    && "schema".equals(elemName.getLocalPart());

            // A digest is required for version 12.5+
            String version = attributes.getValue("saxonVersion");
            // No sneaky deleting the version to avoid the check. Except SCM files don't have a version...
            // NOTE: this will also cause very old SEF files (generated prior to 9.8?) to fail, as they don't have
            // a saxonVersion attribute either. 
            requireDigest = version == null && !scm_schema;
            if (version != null) {
                String minorVersion = "x"; // cause number format exception
                int dpos = version.indexOf('.');
                if (dpos > 0) {
                    minorVersion = version.substring(dpos+1);
                    version = version.substring(0, dpos);
                }
                try {
                    int majorVersion = Integer.parseInt(version);
                    if (majorVersion > 12) {
                        requireDigest = true;
                    } else if (majorVersion == 12) {
                        requireDigest = Integer.parseInt(minorVersion) >= 5;
                    }
                } catch (NumberFormatException e) {
                    requireDigest = true;
                }
            }

            target = attributes.getValue("target");
            if (target == null) {
                target = "unknown";
            }
        }

        // Need these in lexicographic order for the cryptographic hash.
        // I also want to assure that the current checksum works. And I want
        // to use the hash() function as the common place for collecting data
        // for both. That means a bit of fiddling around here (extra fiddling
        // because NodeName doesn't implement Comparable).

        HashMap<String,NodeName> namemap = new HashMap<>();
        HashMap<String,String> attrmap = new HashMap<>();
        String[] names = new String[attributes.size()];
        int index = 0;
        for (AttributeInfo att : attributes) {
            String key = att.getNodeName().getLocalPart() + att.getNodeName().getNamespaceUri();
            attrmap.put(key, att.getValue());
            namemap.put(key, att.getNodeName());
            names[index++] = key;
        }

        Arrays.sort(names);
        for (String key : names) {
            NodeName name = namemap.get(key);
            String value = attrmap.get(key);
            checksum ^= hash(name, sequence);
            trace("After attribute name " + name.getDisplayName() + ": " + checksum);
            checksum ^= hash(value, sequence);
            trace("After attribute value " + name.getDisplayName() + ": " + checksum);
        }

        super.startElement(elemName, type, attributes, namespaces, location, properties);
    }

    /**
     * End of element
     */
    @Override
    public void endElement() throws XPathException {
        depth--;
        if (depth == 0 && target.startsWith("JS")) {
            // We've reached the end. First calculate the SIGMA2 hash, and then
            // update the checksum with a checksum of the
            // SIGMA2 hash (because that's what SaxonJS2 is going to have done).
            // The sequence is 1 when SIGMA2 is seen by the JS2 verifier.
            //
            // This only applies when the output is JSON because when the output
            // is XML, the checksums are stored in processing instructions and
            // are always ignored by the verifier.

            String sigma2Hash = getDigest(); // the digest changes with subsequent hash calls, so fix it first
            checksum ^= hash(SIGMA2, 1);
            checksum ^= hash("", 1); // SIGMA2 is in no namespace
            checksum ^= hash(sigma2Hash, 1);
            trace("After SIGMA2: " + checksum);
        }

        checksum ^= 1;
        trace("After endElement: " + checksum);
        super.endElement();
    }

    /**
     * Processing Instruction
     */
    @Override
    public void processingInstruction(String target, UnicodeString data, Location locationId, int properties) throws XPathException {
        if (target.equals(SIGMA)) {
            checksumFound = true;
            if (checkExistingChecksum) {
                try {
                    int found = (int) Long.parseLong("0" + data, 16);
                    checksumCorrect = found == checksum;
                } catch (NumberFormatException e) {
                    checksumCorrect = false;
                }
                if (data.toString().equals(getDigest())) {
                    // This case represents some point in the future when we've
                    // abandoned the checksum and the digest is stored in SIGMA
                    digestFound = true;
                    digestCorrect = true;
                    checksumCorrect = true; // digest trumps checksum
                }
            }
        }
        if (target.equals(SIGMA2)) {
            digestFound = true;
            if (checkExistingChecksum) {
                digestCorrect = data.toString().equals(getDigest());
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

    public boolean isDigestFound() {
        return digestFound;
    }

    /**
     * Get the accumulated checksum
     * @return the checksum of the events passed through the filter so far.
     */

    public int getChecksum() {
        return checksum;
    }

    /**
     * Get the computed hash
     * @return the hash
     */
    public String getDigest() {
        return digest.getDigest();
    }

    /**
     * Ask if a correct checksum has been found in the file
     * @return true if a checksum has been found, if its value matches, and if no significant data has been encountered
     * after the checksum
     */

    public boolean isChecksumCorrect() {
        if (requireDigest && !digestCorrect) {
            return false;
        }
        return checksumCorrect || "skip".equals(System.getProperty("saxon-checksum"));
    }

    private int hash(String s, int sequence) {
        //System.err.printf("%d %d %s%n", sequence, s.length(), s);
        digest.update(sequence);
        digest.update(s);

        int h = sequence << 8;
        for (int i=0; i<s.length(); i++) {
            h = (h<<1) + s.charAt(i);
        }
        return h;
    }

    private int hash(NodeName n, int sequence) {
        return hash(n.getLocalPart(), sequence) ^ hash(n.getNamespaceUri().toString(), sequence);
    }
}

