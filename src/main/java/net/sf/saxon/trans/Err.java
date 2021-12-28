////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Instruction;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.*;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

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
    public static String wrap(CharSequence cs) {
        return wrap(cs, GENERAL);
    }

    /**
     * Add delimiters to represent variable information within an error message
     *
     * @param cs        the variable information to be delimited
     * @param valueType the type of value, e.g. element name or attribute name
     * @return the delimited variable information
     */
    public static String wrap(/*@Nullable*/ CharSequence cs, int valueType) {
        if (cs == null) {
            return "(NULL)";
        }
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
        int len = cs.length();
        for (int i = 0; i < len; i++) {
            char c = cs.charAt(i);
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
//                case '\\':
//                    sb.append("\\\\");
//                    break;
                default:
                    if (c < 32) {
                        sb.append("\\x");
                        sb.append(Integer.toHexString(c));
                    } else {
                        sb.cat(c);
                    }
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
                    String uri = abbreviateURI(qn.getURI());
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

    public static CharSequence depict(Item item) {
        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo)item;
            switch (node.getNodeKind()) {
                case Type.DOCUMENT:
                    return "doc(" + abbreviateURI(node.getSystemId()) + ')';
                case Type.ELEMENT:
                    return '<' + node.getDisplayName() + '>';
                case Type.ATTRIBUTE:
                    return '@' + node.getDisplayName() + "=\"" + node.getStringValueCS() + '"';
                case Type.TEXT:
                    return "text{" + truncate30(node.getStringValueCS()) + "}";
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

    public static CharSequence depictSequence(Sequence seq) {
        if (seq == null) {
            return "(*null*)";
        }
        try {
            GroundedValue val = seq.materialize();
            if (val.getLength() == 0) {
                return "()";
            } else if (val.getLength() == 1) {
                return depict(seq.head());
            } else {
                return depictSequenceStart(val.iterate(), 3, val.getLength());
            }
        } catch (Exception e) {
            return "(*unreadable*)";
        }
    }

    public static String depictSequenceStart(SequenceIterator seq, int max, int actual) {
        try {
            FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
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

                sb.cat(Err.depict(next));
            }
            sb.append(") ");
            return sb.toString();
        } catch (XPathException e) {
            return "";
        }
    }

    public static CharSequence truncate30(CharSequence cs) {
        if (cs.length() <= 30) {
            return Whitespace.collapseWhitespace(cs);
        } else {
            return Whitespace.collapseWhitespace(cs.subSequence(0, 30)) + "...";
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

    public static String abbreviateEQName(String eqName) {
        try {
            if (eqName.startsWith("{")) {
                eqName = "Q" + eqName;
            }
            StructuredQName sq = StructuredQName.fromEQName(eqName);
            return "Q{" + abbreviateURI(sq.getURI()) + "}" + sq.getLocalPart();
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


}
