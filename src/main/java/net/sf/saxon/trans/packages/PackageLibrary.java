////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.packages;


import net.sf.saxon.Configuration;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.style.*;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;

import javax.xml.transform.Source;
import java.io.File;
import java.util.*;

/**
 * A PackageLibrary represents a collection of packages that can be searched, typically to satisfy
 * an xsl:use-package declaration. Packages are indexed by their package name and version. It is
 * possible to index a package under a different name and/or version than appears in the source code;
 * this allows the same source package to be compiled into two different variants with different
 * settings for static parameters, so that the xsl:use-package declaration can pick up one or the other
 * by a suitable choice of package name or version. A package can also have a short-form alias,
 * which is ignored by xsl:use-package, but can be used to identify the package in the -xsl option
 * of the Transform command line (this is only useful when the package is set up from a configuration
 * file).
 */

public class PackageLibrary {

    private final Configuration config;
    private final CompilerInfo compilerInfo;

    private Map<String, List<PackageVersion>> packageVersions =
            new HashMap<>();

    private Map<VersionedPackageName, PackageDetails> packages =
            new HashMap<>();


    /**
     * Create an empty package library
     *
     * @param compilerInfo the compiler options to be used if any package from the library
     *                     needs to be compiled. The package library retains a reference
     *                     to the CompilerInfo, so any subsequent changes to the options in the
     *                     CompilerInfo will take effect.
     */

    public PackageLibrary(CompilerInfo compilerInfo) {
        this.compilerInfo = compilerInfo;
        this.config = compilerInfo.getConfiguration();
    }

    public CompilerInfo getCompilerInfo() {
        return compilerInfo;
    }

    /**
     * Create a package library as a copy of an existing package library
     *
     * @param library the existing package library to be copied. The new package
     *                library will contain a reference to the same CompilerInfo
     *                as the supplied package library, and any subsequent changes
     *                to this CompilerInfo will take effect.
     */

    public PackageLibrary(PackageLibrary library) {
        packageVersions =
                new HashMap<>(library.packageVersions);
        packages = new HashMap<>(library.packages);
        compilerInfo = library.compilerInfo;
        config = library.config;
    }

    /**
     * Create a package library from a set of source files (being either the source XSLT of the
     * top-level modules of the packages in question, or the exported SEF file representing the
     * compiled package)
     *
     * @param info the compiler to which this library belongs
     * @param files  the files making up the package library
     * @throws XPathException if any of the files doesn't have a top-level xsl:package element
     *                        containing the package name and package-version attributes. Note that validation
     *                        of the content of the file is minimal; if the name and version can be successfully
     *                        extracted, then full validation will be carried out if and when the package is actually
     *                        used.
     */

    public PackageLibrary(CompilerInfo info, Set<File> files) throws XPathException {
        compilerInfo = info;
        config = info.getConfiguration();
        for (File file : files) {
            PackageDetails details = PackageInspector.getPackageDetails(file, config);
            if (details == null) {
                throw new XPathException("Unable to get package name and version for file " + file.getName());
            }
            addPackage(details);
        }
    }

    /**
     * Add a compiled and loaded package to this package library. This will replace
     * any existing package with the same name and version.
     *
     * @param packageIn The stylesheet package to be added
     */
    public synchronized void addPackage(StylesheetPackage packageIn) {
        String name = packageIn.getPackageName();
        PackageVersion version = packageIn.getPackageVersion();
        VersionedPackageName vp = new VersionedPackageName(name, version);
        PackageDetails details = new PackageDetails();
        details.nameAndVersion = vp;
        details.loadedPackage = packageIn;
        if (vp.packageName != null) {
            packages.put(vp, details);
            addPackage(details);
        }
    }

    /**
     * Add an entry for a package to the library
     *
     * @param details details of the package to be added. The package
     *                referenced in these details may exist in either
     *                source or compiled form. No static processing
     *                or validation takes place at this stage.
     */

    public synchronized void addPackage(PackageDetails details) {
        VersionedPackageName vp = details.nameAndVersion;
        String name = vp.packageName;
        PackageVersion version = vp.packageVersion;
        List<PackageVersion> versions = packageVersions.computeIfAbsent(name, k -> new ArrayList<>());
        versions.add(version);
        packages.put(vp, details);
    }

    /**
     * Add a package supplied in the form of a file: either a source XSLT package, or
     * an exported package
     *
     * @param file a file to be added to the package library
     */

    public void addPackage(File file) throws XPathException {
        PackageDetails details = PackageInspector.getPackageDetails(file, config);
        if (details == null) {
            throw new XPathException("Unable to get package name and version for file " + file.getName());
        }
        addPackage(details);
    }


    /**
     * Find a package from the library that has the given name and whose version lies in the given ranges.
     * If multiple versions exist, select the one with highest priority or version number.
     *
     * @param name   The name of the package. This must match the name under which the
     *               package is registered in the library, which is not necessarily the same
     *               as the package name appearing in the source code.
     * @param ranges The ranges of versions of that package that are acceptable
     * @return Details of the package that best meets the criteria, or null if none can be found.
     * The name of the package must match; if there are multiple versions, then the
     * version chosen is based first on the priority attached to this package/version
     * in the library, and if the priorities are equal (or there are no explicit priorities)
     * then the one with highest version number is taken.
     */

    public synchronized PackageDetails findPackage(String name, PackageVersionRanges ranges) {
        Set<PackageDetails> candidates = new HashSet<>();
        List<PackageVersion> available = packageVersions.get(name);
        if (available == null) {
            return null;
        }
        int maxPriority = Integer.MIN_VALUE;
        for (PackageVersion pv : available) {
            PackageDetails details = packages.get(new VersionedPackageName(name, pv));
            if (ranges.contains(pv)) {
                candidates.add(details);
                Integer priority = details.priority;
                if (priority != null && priority > maxPriority) {
                    maxPriority = priority;
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return candidates.iterator().next();
        } else {
            // more than one candidate
            Set<PackageVersion> shortList = new HashSet<>();
            PackageDetails highest = null;
            if (maxPriority == Integer.MIN_VALUE) {
                for (PackageDetails details : candidates) {
                    if (highest == null || details.nameAndVersion.packageVersion.compareTo(highest.nameAndVersion.packageVersion) > 0) {
                        highest = details;
                    }
                }
            } else {
                for (PackageDetails details : candidates) {
                    Integer priority = details.priority;
                    PackageVersion pv = details.nameAndVersion.packageVersion;
                    if (priority != null && priority == maxPriority && (highest == null || pv.compareTo(highest.nameAndVersion.packageVersion) > 0)) {
                        highest = details;
                    }
                }
            }
            return highest;
        }
    }

    /**
     * Find the entry with a given shortName.
     *
     * @param shortName the shortName of the entry
     * @return the PackageDetails for this shortName if present, or null otherwise.
     * @throws IllegalStateException if there is more than one entry with the requested shortName
     */

    public synchronized PackageDetails findDetailsForAlias(String shortName) {
        assert shortName != null;
        PackageDetails selected = null;
        for (PackageDetails details : packages.values()) {
            if (shortName.equals(details.shortName)) {
                if (selected == null) {
                    selected = details;
                } else {
                    throw new IllegalStateException("Non-unique shortName in package library: " + shortName);
                }
            }
        }
        return selected;
    }

    /**
     * Obtain a loaded package, given details of the package.
     * This will return the package if it is already loaded; otherwise it will attempt to
     * compile and/or load the package from XSLT source or from an export file.
     * It will also load all the packages on which it depends, recursively, and will report
     * an error if any cycles are found.
     *
     * @param details    the package details for the required package, as previously returned
     *                   (perhaps) using {@link #findPackage(String, PackageVersionRanges)}
     * @param disallowed the names/versions of packages that reference this package, and that
     *                   therefore cannot be referenced by this package without creating a cycle of dependencies.
     * @return the loaded package
     * @throws XPathException if loading the package fails, typically either because (a) a package
     *                        contains static XSLT errors, or (b) because there is a cycle of package dependencies
     * @since 9.8
     */

    public StylesheetPackage obtainLoadedPackage(PackageDetails details, List<VersionedPackageName> disallowed) throws XPathException {
        if (details.loadedPackage != null) {
            return details.loadedPackage;
        } else if (details.exportLocation != null) {
            testForCycles(details, disallowed);
            details.beingProcessed = Thread.currentThread();
            Source input = details.exportLocation;
            IPackageLoader loader = config.makePackageLoader();
            StylesheetPackage pack = loader.loadPackage(input);
            checkNameAndVersion(pack, details);
            details.loadedPackage = pack;
            details.beingProcessed = null;
            return pack;
        } else if (details.sourceLocation != null) {
            testForCycles(details, disallowed);
            details.beingProcessed = Thread.currentThread();
            Compilation compilation = new Compilation(config, compilerInfo);
            compilation.setUsingPackages(disallowed);
            compilation.setExpectedNameAndVersion(details.nameAndVersion);
            compilation.clearParameters();
            compilation.setLibraryPackage(true);
            if (details.staticParams != null) {
                for (Map.Entry<StructuredQName, GroundedValue> entry : details.staticParams.entrySet()) {
                    compilation.setParameter(entry.getKey(), entry.getValue());
                }
            }
            PrincipalStylesheetModule psm = compilation.compilePackage(details.sourceLocation);
            details.beingProcessed = null;
            if (compilation.getErrorCount() > 0) {
                throw new XPathException("Errors found in package " + details.nameAndVersion.packageName);
            }
            StylesheetPackage styPack = psm.getStylesheetPackage();
            checkNameAndVersion(styPack, details);
            details.loadedPackage = styPack;
            return styPack;
        } else {
            return null;
        }
    }

    private void testForCycles(PackageDetails details, List<VersionedPackageName> disallowed) throws XPathException {
        if (details.beingProcessed == Thread.currentThread()) {
            // Report a cycle of package dependencies
            FastStringBuffer buffer = new FastStringBuffer(1024);
            for (VersionedPackageName n : disallowed) {
                buffer.append(n.packageName);
                buffer.append(", ");
            }
            buffer.append("and ");
            buffer.append(details.nameAndVersion.packageName);
            throw new XPathException("There is a cycle of package dependencies involving " + buffer, "XTSE3005");
        }
    }

    private void checkNameAndVersion(StylesheetPackage pack, PackageDetails details) throws XPathException {
        String storedName = pack.getPackageName();
        if (details.baseName != null) {
            if (!details.baseName.equals(storedName)) {
                throw new XPathException("Base name of package (" + details.baseName +
                                                 ") does not match the value in the XSLT source (" +
                                                 storedName + ")");
            }
        } else {
            if (!details.nameAndVersion.packageName.equals(storedName)) {
                throw new XPathException("Registered name of package (" + details.nameAndVersion.packageName +
                                                 ") does not match the value in the XSLT source (" +
                                                 storedName + ")");
            }
        }
        PackageVersion actualVersion = pack.getPackageVersion();
        if (!actualVersion.equals(details.nameAndVersion.packageVersion)) {
            throw new XPathException("Registered version number of package (" + details.nameAndVersion.packageVersion +
                                             ") does not match the value in the XSLT source (" +
                                             actualVersion + ")");
        }
    }

    /**
     * Supply all the packages that currently exist in this library
     *
     * @return every package indexed
     */
    public synchronized List<StylesheetPackage> getPackages() {
        List<StylesheetPackage> result = new ArrayList<>();
        for (PackageDetails details : packages.values()) {
            if (details.loadedPackage != null) {
                result.add(details.loadedPackage);
            }
        }
        return result;
    }

}
