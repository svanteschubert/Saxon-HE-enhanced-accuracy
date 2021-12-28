////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.GenericTreeInfo;

import java.util.Iterator;

/**
 * Implementation of TreeInfo for a Virtual Copy tree
 */
public class VirtualTreeInfo extends GenericTreeInfo {

    private boolean copyAccumulators;


    public VirtualTreeInfo(Configuration config) {
        super(config);
    }

    public VirtualTreeInfo(Configuration config, VirtualCopy vc) {
        super(config, vc);
    }

    public void setCopyAccumulators(boolean copy) {
        this.copyAccumulators = copy;
    }

    public boolean isCopyAccumulators() {
        return copyAccumulators;
    }

    /**
     * Get the list of unparsed entities defined in this document
     *
     * @return an Iterator, whose items are of type String, containing the names of all
     * unparsed entities defined in this document. If there are no unparsed entities or if the
     * information is not available then an empty iterator is returned
     * @since 9.1
     */
    @Override
    public Iterator<String> getUnparsedEntityNames() {
        return ((VirtualCopy)getRootNode()).getOriginalNode().getTreeInfo().getUnparsedEntityNames();
    }

    /**
     * Get the unparsed entity with a given name
     *
     * @param name the name of the entity
     * @return if the entity exists, return an array of two Strings, the first
     * holding the system ID of the entity (as an absolute URI if possible),
     * the second holding the public ID if there is one, or null if not.
     * If the entity does not exist, the method returns null.
     * Applications should be written on the assumption that this array may
     * be extended in the future to provide additional information.
     * @since 8.4
     */
    @Override
    public String[] getUnparsedEntity(String name) {
        return ((VirtualCopy) getRootNode()).getOriginalNode().getTreeInfo().getUnparsedEntity(name);
    }
}

