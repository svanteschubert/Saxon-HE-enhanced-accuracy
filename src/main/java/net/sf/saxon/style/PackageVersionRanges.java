////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;

/**
 * A class to handle a set of package version ranges
 * <p>This implements the semantics given in
 * <a href="http://www.w3.org/TR/xslt-30/#package-versions">http://www.w3.org/TR/xslt-30/#package-versions</a>,
 * where a comma-separated sequence of package-version ranges are declared as one of:</p>
 * <ul>
 * <li>'*' - any version</li>
 * <li><code>package-version</code> - an exact match</li>
 * <li><code>package-version'.*'</code> - any package whose version starts with the given prefix</li>
 * <li><code>package-version'+'</code> - any package whose version orders equal or later than the given version</li>
 * <li><code>'to 'package-version</code> - any package whose version orders equal or earlier than the given version</li>
 * <li><code>package-version' to 'package-version</code> - any package whose version orders equal to or between the given versions</li>
 * </ul>
 */
public class PackageVersionRanges {

    private final ArrayList<PackageVersionRange> ranges;

    private static class PackageVersionRange {
        private final String display;
        private PackageVersion low;
        private PackageVersion high;
        private boolean all = false;
        private boolean prefix = false;

        public PackageVersionRange(String s) throws XPathException {
            display = s;
            if (s.endsWith("+")) {
                low = new PackageVersion(s.replace("+", ""));
                high = PackageVersion.MAX_VALUE;
            } else if (s.matches("^to\\s.*")) {
                low = PackageVersion.ZERO;
                String end = s.substring(3);
                if (end.endsWith(".*")) {
                    high = new PackageVersion(end.substring(0, end.length() - 2));
                    prefix = true;
                } else {
                    high = new PackageVersion(end);
                }
            } else if (s.matches(".*\\s?to\\s+.*")) {
                String[] range = s.split("\\s*to\\s+");
                if (range.length > 2) {
                    throw new XPathException("Invalid version range:" + s, "XTSE0020");
                }
                low = new PackageVersion(range[0]);
                String end = range[1];
                if (end.endsWith(".*")) {
                    high = new PackageVersion(end.substring(0, end.length() - 2));
                    prefix = true;
                } else {
                    high = new PackageVersion(end);
                }
            } else if (s.endsWith(".*")) {
                prefix = true;
                low = new PackageVersion(s.substring(0, s.length() - 2));

            } else {
                low = new PackageVersion(s);
                high = low;
            }
        }

        /**
         * Create a range representing "all" packages
         */
        public PackageVersionRange() {
            display = "*";
            all = true;
        }

        boolean contains(PackageVersion v) {
            if (all) {
                return true;
            } else if (prefix) {
                if (high != null) {
                    return low.compareTo(v) <= 0
                            && (v.compareTo(high) <= 0 || high.isPrefix(v));
                } else {
                    return low.isPrefix(v);
                }
            } else {
                return low.compareTo(v) <= 0 && v.compareTo(high) <= 0;
            }
        }

        @Override
        public String toString() {
            return display;
        }
    }

    /**
     * Generate a set of package version ranges
     *
     * @param s Input string describing the ranges in the grammar described in
     *          <a href="http://www.w3.org/TR/xslt-30/#package-versions">http://www.w3.org/TR/xslt-30/#package-versions</a>
     * @throws XPathException if any version range is invalid
     */
    public PackageVersionRanges(String s) throws XPathException {
        ranges = new ArrayList<>();
        String trimmed = Whitespace.normalize(s);
        if (trimmed.equals("*")) {
            ranges.add(new PackageVersionRange());
        } else {
            for (String p : trimmed.split("\\s?,\\s?")) {
                ranges.add(new PackageVersionRange(p));
            }
        }
    }

    /**
     * Test whether a given package version lies within any of the ranges described in this PackageVersionRanges
     *
     * @param version The version to be checked
     * @return true if the version is contained in any of the ranges, false otherwise
     */
    public boolean contains(PackageVersion version) {
        for (PackageVersionRange r : ranges) {
            if (r.contains(version)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        if (ranges.size() == 1) {
            return ranges.get(0).toString();
        } else {
            StringBuilder buffer = new StringBuilder(256);
            for (PackageVersionRange r : ranges) {
                buffer.append(r.toString()).append(",");
            }
            buffer.setLength(buffer.length() - 1);
            return buffer.toString();
        }
    }
}

