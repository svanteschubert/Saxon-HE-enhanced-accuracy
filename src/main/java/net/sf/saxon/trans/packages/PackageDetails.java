////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.packages;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.style.StylesheetPackage;

import javax.xml.transform.Source;
import java.util.Map;

/**
 * Information about a package held in a package library; the package may or may not be loaded in memory
 */
public class PackageDetails {
    /**
     * The name and version number of the package
     */
    public VersionedPackageName nameAndVersion;
    /**
     * The original package name, as defined by the <code>@base</code> attribute in the
     * configuration file.
     *
     * <p>Required only if the package name defined in the name attribute differs from the
     * package name in the XSLT source. If present, the value must match the package name
     * defined in the XSLT source. Using this attribute allows packages to be renamed.
     * This is useful when a package has static parameters whose values are bound in child
     * withParam elements: it allows two instances of a package to be made available (under
     * different names) with different bindings for the static parameters.</p>
     */
    public String baseName;
    /**
     * A short name for the package. Useful when nominating the package as the value of
     * the -xsl option on the command line.
     *
     * <p>Any convenient short name for the package name/version combination.
     * This can be used for convenience as the value of the -xsl option on the Transform
     * command line. (We suggest using a value that is unlikely to be confused with a
     * filename, for example * if the same stylesheet package is used all the time.)</p>
     */
    public String shortName;
    /**
     * The executable form of the compiled package
     */
    public StylesheetPackage loadedPackage;
    /**
     * A Source object identifying a location where the source XSLT code of the package
     * is to be found. Note that in general a Source is consumed by use (so it can only
     * be read once); the sourceLocation is therefore typically useless once a loadedPackage
     * exists.
     */
    public Source sourceLocation;
    /**
     * A Source object identifying a location where the compiled SEF code of the package
     * is to be found. Note that in general a Source is consumed by use (so it can only
     * be read once); the exportLocation is therefore typically useless once a loadedPackage
     * exists.
     */
    public Source exportLocation;
    /**
     * The priority of this version of a package relative to other versions of the same
     * package. By default (if two versions have the same priority), the higher version
     * is used. This attribute allows (for example) version 2.0.10 to be marked as the preferred
     * release even if a version 3.0-beta is available, by giving the two versions different
     * priority. A numerically higher value indicates a higher priority.
     */
    public Integer priority;
    /**
     * The values of static stylesheet parameters for this package instance. In a function
     * library, two instances of the same package may exist with different settings for
     * stylesheet parameters, but they must then be given an alias package name. The nameAndVersion
     * contains this alias; the baseName contains the original name as defined in the XSLT source
     * code.
     */
    public Map<StructuredQName, GroundedValue> staticParams;
    /**
     * During processing (compilation) of a package, the <code>beingProcessed</code> field
     * indicates the thread in which this processing is taking place; at other times, the field
     * is null. This field is used when checking for cycles of package dependencies.
     */
    public Thread beingProcessed;
}

