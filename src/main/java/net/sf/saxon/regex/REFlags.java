////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

/**
 * Class representing a set of regular expression flags (some combination of i, m, s, x, q).
 * Also contains options affecting the regular expression dialect: whether or not XPath 2.0
 * and XPath 3.0 extensions to XSD regex syntax are accepted.
 */

public class REFlags {

    private boolean caseIndependent;
    private boolean multiLine;
    private boolean singleLine;
    private boolean allowWhitespace;
    private boolean literal;
    private boolean xpath20;
    private boolean xpath30;
    private boolean xsd11;
    private boolean debug; // flags = ";g"
    private boolean allowUnknownBlockNames = false; //flags = ";k"

    /**
     * Create the regular expression flags
     *
     * @param flags    a string containing zero or more of 'i', 'x', 'm', 's'
     * @param language one of "XSD10", "XSD11", "XP20", or "XP30" indicating the regular expression dialect.
     *                 Also allow combinations, e.g. "XP20/XSD11".
     */
    public REFlags(String flags, String language) {

        if (language.equals("XSD10")) {
            // no action
        } else if (language.contains("XSD11")) {
            allowUnknownBlockNames = !language.contains("XP");
            xsd11 = true;
        }
        if (language.contains("XP20")) {
            xpath20 = true;
        } else if (language.contains("XP30")) {
            xpath20 = true;
            xpath30 = true;
        }

        int semi = flags.indexOf(';');
        int endStd = semi >= 0 ? semi : flags.length();
        for (int i = 0; i < endStd; i++) {
            char c = flags.charAt(i);
            switch (c) {
                case 'i':
                    caseIndependent = true;
                    break;
                case 'm':
                    multiLine = true;
                    break;
                case 's':
                    singleLine = true;
                    break;
                case 'q':
                    literal = true;
                    if (!xpath30) {
                        throw new RESyntaxException("'q' flag requires XPath 3.0 to be enabled");
                    }
                    break;
                case 'x':
                    allowWhitespace = true;
                    break;
                default:
                    throw new RESyntaxException("Unrecognized flag '" + c + "'");
            }
        }
        for (int i = semi + 1; i < flags.length(); i++) {
            char c = flags.charAt(i);
            switch (c) {
                case 'g':
                    debug = true;
                    break;
                case 'k':
                    allowUnknownBlockNames = true;
                    break;
                case 'K':
                    allowUnknownBlockNames = false;
                    break;
            }
        }
    }

    public boolean isCaseIndependent() {
        return caseIndependent;
    }

    public boolean isMultiLine() {
        return multiLine;
    }

    public boolean isSingleLine() {
        return singleLine;
    }

    public boolean isAllowWhitespace() {
        return allowWhitespace;
    }

    public boolean isLiteral() {
        return literal;
    }

    public boolean isAllowsXPath20Extensions() {
        return xpath20;
    }

    public boolean isAllowsXPath30Extensions() {
        return xpath30;
    }

    public boolean isAllowsXSD11Syntax() {
        return xsd11;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setAllowUnknownBlockNames(boolean allow) {
        this.allowUnknownBlockNames = allow;
    }

    public boolean isAllowUnknownBlockNames() {
        return allowUnknownBlockNames;
    }


}
