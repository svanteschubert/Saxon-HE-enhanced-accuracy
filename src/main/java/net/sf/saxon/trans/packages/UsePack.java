////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.packages;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.style.PackageVersionRanges;
import net.sf.saxon.trans.XPathException;

/**
 * Describes an xsl:use-package requirement from within a package,
 * in terms of a name and a set of version ranges
 */
public class UsePack {
    public String packageName;
    public PackageVersionRanges ranges;
    public Location location;

    public UsePack(String name, String version, Location location) throws XPathException {
        this.packageName = name;
        this.ranges = new PackageVersionRanges(version == null ? "*" : version);
        this.location = location;
    }
}

