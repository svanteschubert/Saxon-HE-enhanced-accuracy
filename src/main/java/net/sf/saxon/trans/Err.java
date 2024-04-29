////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Instruction;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.str.BMPString;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SingletonClosure;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.IntIterator;

/**
 * Class containing utility methods for handling error messages
 */

public class Err {

    public static final int ELEMENT = 1;
    public static final int ATTRIBUTE = 2;
    public static final int FUNCTION = 3;
    public static final int VALUE = 4;
    public static final int VARIABLE = 5;
    public static final int GENERAL = 6;
    public static final int URI = 7;
    public static final int EQNAME = 8;

    /**
     * Add delimiters to represent variable information within an error message
     *
     * @param cs the variable information to be delimited
     * @return the delimited variable information
     */
    public static String wrap(UnicodeString cs) {
        return wrap(cs, GENERAL);
    }

    /**
     * Add delimiters to represent variable information within an error message
     *
     * @param cs the variable information to be delimited
     * @return the delimited variable information
     */
    public static String wrap(String cs) {
        return wrap(cs, GENERAL);
    }

    /**
     * Add delimiters to represent variable information within an error message
     *
     * @param cs the variable information to be delimited
     * @return the delimited variable information
     */
    public static String wrap(String cs, int valueType) {
        return wrap(StringView.of(cs), valueType);
    }

    /**
     * Add delimiters to represent variable information within an error message
     *
     * @param cs        the variable information to be delimited
     * @param valueType the type of value, e.g. element name or attribute name
     * @return the delimited variable information
     */
    public static String wrap(UnicodeString cs, int valueType) {
        if (cs == null) {
            return "(NULL)";
        }
        StringBuilder sb = new StringBuilder(64);
        IntIterator iter = cs.codePoints();
        int len = 0;
        while (iter.hasNext()) {
            int c = iter.next();
            len++;
            switch (c) {
                case '\n':
                    sb.append("\\n");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < 32) {
                        sb.append("\\x");
                        sb.append(Integer.toHexString(c));
                    } else {
                        sb.appendCodePoint(c);
                    }
                    break;
            }
        }
        String s;
        if (valueType == ELEMENT || valueType == ATTRIBUTE) {
            s = sb.toString();
            if (s.startsWith("{")) {
                s = "Q" + s;
            }
            if (s.startsWith("Q{")) {
                try {
                    StructuredQName qn = StructuredQName.fromEQName(sb.toString());
                    String uri = abbreviateURI(qn.getNamespaceUri());
                    s = "Q{" + uri + "}" + qn.getLocalPart();
                } catch (Exception e) {
                    s = sb.toString();
                }
            }
        } else if (valueType == URI) {
            s = abbreviateURI(sb.toString());
        } else if (valueType == EQNAME) {
            s = abbreviateEQName(sb.toString());
        } else {
            s = len > 30 ? sb.toString().substring(0, 30) + "..." : sb.toString();
        }
        switch (valueType) {
            case ELEMENT:
                return "<" + s + ">";
            case ATTRIBUTE:
                return "@" + s;
            case FUNCTION:
                return s + "()";
            case VARIABLE:
                return "$" + s;
            case VALUE:
                return "\"" + s + "\"";
            case EQNAME:
                return s;
            default:
                return "{" + s + "}";
        }
    }

    /**
     * Create a string representation of an item for use in an error message
     */

    public static String depict(Item item) {
        if (item == null) {
            return "(*null*)";
        }
        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo)item;
            switch (node.getNodeKind()) {
                case Type.DOCUMENT:
                    return "doc(" + abbreviateURI(node.getSystemId()) + ')';
                case Type.ELEMENT:
                    return '<' + node.getDisplayName() + '>';
                case Type.ATTRIBUTE:
                    return '@' + node.getDisplayName() + "=\"" + node.getUnicodeStringValue() + '"';
                case Type.TEXT:
                    return "text{" + truncate30(node.getUnicodeStringValue()) + "}";
                case Type.COMMENT:
                    return "<!--...-->";
                case Type.PROCESSING_INSTRUCTION:
                    return "<?" + node.getLocalPart() + "...?>";
                case Type.NAMESPACE:
                    return "xmlns:" + node.getLocalPart() + "=" + abbreviateURI(node.getStringValue());
                default:
                    return "";
            }
        } else {
            return item.toShortString();
        }
    }

    public static String depictCodepoint(int cp) {
        String hexCode = "#x" + Integer.toHexString(cp);
        if (cp >= 20 && cp < UTF16CharacterSet.SURROGATE1_MIN) {
            return "'" + (char)cp + "'(" + hexCode + ")";
        } else {
            return hexCode;
        }
    }

    public static CharSequence depictSequence(Sequence seq) {
        if (seq == null) {
            return "(*null*)";
        }
        try {
            if (seq instanceof GroundedValue) {
                GroundedValue val = (GroundedValue) seq;
                if (val.getLength() == 0) {
                    return "()";
                } else if (val.getLength() == 1) {
                    return depict(seq.head());
                } else {
                    return depictSequenceStart(val.iterate(), 3, val.getLength());
                }
            } else if (seq instanceof SingletonClosure) {
                SingletonClosure sc = (SingletonClosure) seq;
                if (sc.isBuilt()) {
                    return sc.asItem() == null ? "()" : depict(sc.asItem());
                } else {
                    return "(*not-yet-evaluated singleton*)";
                }
            } else if (seq instanceof net.sf.saxon.value.MemoClosure) {
                net.sf.saxon.value.MemoClosure mc = (net.sf.saxon.value.MemoClosure)seq;
                seq = mc.getSequenceAsIs();
                if (seq == null) {
                    return "(*not-yet-evaluated sequence*)";
                } else {
                    return depictSequence(seq);
                }
            } else {
                return "(*lazily evaluated*)";
            }
        } catch (Exception e) {
            return "(*unreadable*)";
        }
    }

    public static String depictSequenceStart(SequenceIterator seq, int max, int actual) {
        StringBuilder sb = new StringBuilder(64);
        int count = 0;
        sb.append(" (");
        Item next;
        while ((next = seq.next()) != null) {
            if (count++ > 0) {
                sb.append(", ");
            }
            if (count > max) {
                sb.append("... [" + actual + "])");
                return sb.toString();
            }

            sb.append(Err.depict(next));
        }
        sb.append(") ");
        return sb.toString();
    }

    public static UnicodeString truncate30(UnicodeString cs) {
        if (cs.length() <= 30) {
            return Whitespace.collapseWhitespace(cs);
        } else {
            return Whitespace.collapseWhitespace(cs.substring(0, 30)).concat(BMPString.of("..."));
        }
    }

    /**
     * Abbreviate a URI for use in error messages
     *
     * @param uri the full URI
     * @return the URI, truncated at the last slash or to the last 15 characters, with a leading ellipsis, as appropriate
     */

    public static String abbreviateURI(String uri) {
        if (uri==null) {
            return "";
        }
        int lastSlash = (uri.endsWith("/") ? uri.substring(0, uri.length()-1) : uri).lastIndexOf('/');
        if (lastSlash < 0) {
            if (uri.length() > 15) {
                uri = "..." + uri.substring(uri.length() - 15);
            }
            return uri;
        } else {
            return "..." + uri.substring(lastSlash);
        }
    }

    public static String abbreviateURI(NamespaceUri uri) {
        return abbreviateURI(uri.toString());
    }

    public static String abbreviateEQName(String eqName) {
        try {
            if (eqName.startsWith("{")) {
                eqName = "Q" + eqName;
            }
            StructuredQName sq = StructuredQName.fromEQName(eqName);
            return "Q{" + abbreviateURI(sq.getNamespaceUri()) + "}" + sq.getLocalPart();
        } catch (Exception e) {
            return eqName;
        }
    }

    public static String wrap(Expression exp) {
        if (ExpressionTool.expressionSize(exp) < 10 && !(exp instanceof Instruction)) {
            return "{" + exp + "}";
        } else {
            return exp.getExpressionName();
        }
    }


    public static String describeGenre(Genre genre) {
        switch (genre) {
            case ANY:
                return "any item";
            case ATOMIC:
                return "an atomic value";
            case NODE:
                return "a node";
            case FUNCTION:
                return "a function";
            case MAP:
                return "a map";
            case ARRAY:
                return "an array";
            case EXTERNAL:
            default:
                return "an external object";
        }
    }

    public static String describeVisibility(Visibility vis) {
        return vis.toString().toLowerCase();
    }

    public static String show(Location loc) {
        return abbreviateURI(loc.getSystemId()) + "#" + loc.getLineNumber();
    }

    public static String indefiniteArticleFor(String s, boolean caps) {
        if ("aeioux".indexOf(s.charAt(0)) >= 0) {
            return (caps ? "An" : "an");
        } else {
            return (caps ? "A" : "a");
        }
    }
}
