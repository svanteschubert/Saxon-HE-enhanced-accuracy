////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

/**
 * This class is used to represent unparsed entities in the PullProvider interface
 */

public class UnparsedEntity {

    private String name;
    private String systemId;
    private String publicId;
    private String baseURI;

    /**
     * Get the name of the unparsed entity
     *
     * @return the name of the unparsed entity
     */

    public String getName() {
        return name;
    }

    /**
     * Set the name of the unparsed entity
     *
     * @param name the name of the unparsed entity
     */

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the system identifier of the unparsed entity
     *
     * @return the system identifier of the unparsed entity
     */

    public String getSystemId() {
        return systemId;
    }

    /**
     * Set the system identifier of the unparsed entity
     *
     * @param systemId the system identifier of the unparsed entity
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the public identifier of the unparsed entity
     *
     * @return the public identifier of the unparsed entity
     */

    public String getPublicId() {
        return publicId;
    }

    /**
     * Set the public identifier of the unparsed entity
     *
     * @param publicId the public identifier of the unparsed entity
     */

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    /**
     * Get the base URI of the unparsed entity
     *
     * @return the base URI  of the unparsed entity
     */

    public String getBaseURI() {
        return baseURI;
    }

    /**
     * Set the base URI of the unparsed entity
     *
     * @param baseURI the base URI  of the unparsed entity
     */

    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }


}
