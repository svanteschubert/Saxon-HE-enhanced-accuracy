////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.java;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.sort.AlphanumericCollator;
import net.sf.saxon.expr.sort.CaseFirstCollator;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.SimpleCollation;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.XPathException;

import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Properties;

/**
 * A JavaCollationFactory allows a Collation to be created given
 * a set of properties that the collation should have. This class creates a collator using
 * the facilities of the Java platform; there is a corresponding class that uses the .NET
 * platform.
 */

public abstract class JavaCollationFactory {

    /**
     * The class is a never instantiated
     */
    private JavaCollationFactory() {
    }

    /**
     * Make a collator with given properties
     *
     * @param config the Configuration
     * @param uri the Collation URI
     * @param props  the desired properties of the collation
     * @return a collation with these properties
     */

    /*@Nullable*/
    public static StringCollator makeCollation(Configuration config, String uri, Properties props)
            throws XPathException {

        Collator collator = null;
        StringCollator stringCollator = null;

        // If a specific collation class is requested, this overrides everything else

        String classAtt = props.getProperty("class");
        if (classAtt != null) {
            Object comparator = config.getInstance(classAtt, null);
            if (comparator instanceof Collator) {
                collator = (Collator) comparator;
            } else if (comparator instanceof StringCollator) {
                stringCollator = (StringCollator) comparator;
            } else if (comparator instanceof Comparator) {
                stringCollator = new SimpleCollation(uri, (Comparator) comparator);
            } else {
                throw new XPathException("Requested collation class " + classAtt + " is not a Comparator");
            }
        }

        // If rules are specified, create a RuleBasedCollator

        if (collator == null && stringCollator == null) {
            String rulesAtt = props.getProperty("rules");
            if (rulesAtt != null) {
                try {
                    collator = new RuleBasedCollator(rulesAtt);
                } catch (ParseException e) {
                    throw new XPathException("Invalid collation rules: " + e.getMessage());
                }
            }

            // Start with the lang attribute

            if (collator == null) {
                String langAtt = props.getProperty("lang");
                if (langAtt != null) {
                    collator = Collator.getInstance(getLocale(langAtt));
                } else {
                    collator = Collator.getInstance();  // use default locale
                }
            }
        }

        if (collator != null) {
            // See if there is a strength attribute
            String strengthAtt = props.getProperty("strength");
            if (strengthAtt != null) {
                switch (strengthAtt) {
                    case "primary":
                        collator.setStrength(Collator.PRIMARY);
                        break;
                    case "secondary":
                        collator.setStrength(Collator.SECONDARY);
                        break;
                    case "tertiary":
                        collator.setStrength(Collator.TERTIARY);
                        break;
                    case "identical":
                        collator.setStrength(Collator.IDENTICAL);
                        break;
                    default:
                        throw new XPathException("strength must be primary, secondary, tertiary, or identical");
                }
            }

            // Look for the properties ignore-case, ignore-modifiers, ignore-width

            String ignore = props.getProperty("ignore-width");
            if (ignore != null) {
                if (ignore.equals("yes") && strengthAtt == null) {
                    collator.setStrength(Collator.TERTIARY);
                } else if (ignore.equals("no")) {
                    // no-op
                } else {
                    throw new XPathException("ignore-width must be yes or no");
                }
            }

            ignore = props.getProperty("ignore-case");
            if (ignore != null && strengthAtt == null) {
                switch (ignore) {
                    case "yes":
                        collator.setStrength(Collator.SECONDARY);
                        break;
                    case "no":
                        // no-op
                        break;
                    default:
                        throw new XPathException("ignore-case must be yes or no");
                }
            }

            ignore = props.getProperty("ignore-modifiers");
            if (ignore != null) {
                if (ignore.equals("yes") && strengthAtt == null) {
                    collator.setStrength(Collator.PRIMARY);
                } else if (ignore.equals("no")) {
                    // no-op
                } else {
                    throw new XPathException("ignore-modifiers must be yes or no");
                }
            }

            // The ignore-symbols property is ignored

            // See if there is a decomposition attribute
            String decompositionAtt = props.getProperty("decomposition");
            if (decompositionAtt != null) {
                switch (decompositionAtt) {
                    case "none":
                        collator.setDecomposition(Collator.NO_DECOMPOSITION);
                        break;
                    case "standard":
                        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
                        break;
                    case "full":
                        collator.setDecomposition(Collator.FULL_DECOMPOSITION);
                        break;
                    default:
                        throw new XPathException("decomposition must be none, standard, or full");
                }
            }
        }

        if (stringCollator == null) {
            stringCollator = new SimpleCollation(uri, collator);
        }

        // See if there is a case-order property
        String caseOrder = props.getProperty("case-order");
        if (caseOrder != null && !"#default".equals(caseOrder)) {
            // force base collator to ignore case differences
            if (collator != null) {
                collator.setStrength(Collator.SECONDARY);
            }
            stringCollator = CaseFirstCollator.makeCaseOrderedCollator(uri, stringCollator, caseOrder);
        }

        // See if there is an alphanumeric property
        String alphanumeric = props.getProperty("alphanumeric");
        if (alphanumeric != null && !"no".equals(alphanumeric)) {
            switch (alphanumeric) {
                case "yes":
                    stringCollator = new AlphanumericCollator(stringCollator);
                    break;
                case "codepoint":
                    stringCollator = new AlphanumericCollator(CodepointCollator.getInstance());
                    break;
                default:
                    throw new XPathException("alphanumeric must be yes, no, or codepoint");
            }
        }

        return stringCollator;
    }

    /**
     * Get a locale given a language code in XML format
     */

    private static Locale getLocale(String lang) {
        int hyphen = lang.indexOf("-");
        String language, country;
        if (hyphen < 1) {
            language = lang;
            country = "";
        } else {
            language = lang.substring(0, hyphen);
            country = lang.substring(hyphen + 1);
        }
        return new Locale(language, country);
    }


}

