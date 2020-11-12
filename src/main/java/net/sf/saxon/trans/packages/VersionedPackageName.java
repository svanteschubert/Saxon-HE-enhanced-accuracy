////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.packages;

import net.sf.saxon.style.PackageVersion;
import net.sf.saxon.trans.XPathException;

/**
 * This class represents the combination of an XSLT package name (that is, a URI) and a version
 * number
 */
public class VersionedPackageName {

    public String packageName;
    public PackageVersion packageVersion;

    public VersionedPackageName(String packageName, PackageVersion version) {
        this.packageName = packageName;
        this.packageVersion = version;
    }

    public VersionedPackageName(String packageName, String version) throws XPathException {
        this.packageName = packageName;
        this.packageVersion = new PackageVersion(version);
    }

    @Override
    public String toString() {
        return packageName + " (" + packageVersion.toString() + ")";
    }

    /**
     * Compare two package name/version pairs for equality, ignoring the suffix part
     * of the version number. For example (http://package-one/, 2.1-alpha) compares equal
     * to (http://package-one/, 2.1-beta)
     * @param other the other name/version pair
     * @return true if the values are equal in all respects other than the alphanumeric
     * suffix of the version number.
     */

    public boolean equalsIgnoringSuffix(VersionedPackageName other) {
        return packageName.equals(other.packageName) &&
                packageVersion.equalsIgnoringSuffix(other.packageVersion);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VersionedPackageName &&
                packageName.equals(((VersionedPackageName)obj).packageName) &&
                packageVersion.equals(((VersionedPackageName)obj).packageVersion);
    }

    @Override
    public int hashCode() {
        return packageName.hashCode() ^ packageVersion.hashCode();
    }
}

