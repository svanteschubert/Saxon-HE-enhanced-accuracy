////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Handler for xsl:catch elements in stylesheet.
 * The xsl:catch element has a mandatory attribute errors, a space-separated list of NameTests defining
 * which errors are caught. If an error is caught, the value to be returned may be defined either by a select
 * attribute or by the content of the saxon:catch element.
 */

public class XSLCatch extends StyleElement {

    private Expression select;
    private QNameTest nameTest;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    @Override
    public boolean isInstruction() {
        return false;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Ask whether variables declared in an "uncle" element are visible.
     *
     * @return true for all elements except xsl:fallback and saxon:catch
     */

    @Override
    protected boolean seesAvuncularVariables() {
        return false;
    }

    @Override
    public void prepareAttributes() {

        String selectAtt = null;
        String errorAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("select")) {
                selectAtt = value;
                select = makeExpression(selectAtt, att);
            } else if (f.equals("errors")) {
                errorAtt = value;
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (errorAtt == null) {
            // default is "catch all errors"
            nameTest = AnyNodeTest.getInstance(); // for error recovery
        } else {
            List<QNameTest> tests = parseNameTests(errorAtt);
            if (tests.size() == 0) {
                compileError("xsl:catch/@errors must not be empty");
            }
            if (tests.size() == 1) {
                nameTest = tests.get(0);
            } else {
                nameTest = new UnionQNameTest(tests);
            }
        }
    }

    /**
     * Parse an attribute consisting of a space-separated sequence of NameTests
     *
     * @param elements the value of the attribute
     * @return the sequence of NameTests
     */

    private List<QNameTest> parseNameTests(String elements) {
        List<QNameTest> result = new ArrayList<QNameTest>();
        StringTokenizer st = new StringTokenizer(elements, " \t\n\r", false);
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            QNameTest nt;
            if (s.equals("*")) {
                nt = AnyNodeTest.getInstance();
                result.add(nt);

            } else if (s.endsWith(":*")) {
                if (s.length() == 2) {
                    compileError("No prefix before ':*'");
                    result.add(AnyNodeTest.getInstance());
                }
                String prefix = s.substring(0, s.length() - 2);
                String uri = getURIForPrefix(prefix, false);
                nt = new NamespaceTest(
                        getNamePool(),
                        Type.ELEMENT,
                        uri);
                result.add(nt);
            } else if (s.startsWith("*:")) {
                if (s.length() == 2) {
                    compileErrorInAttribute("No local name after '*:'", "XTSE0010", "errors");
                    result.add(AnyNodeTest.getInstance());
                }
                String localname = s.substring(2);
                nt = new LocalNameTest(
                        getNamePool(),
                        Type.ELEMENT,
                        localname);
                result.add(nt);
            } else {
                String prefix;
                String localName;
                String uri;
                try {
                    String[] parts = NameChecker.getQNameParts(s);
                    prefix = parts[0];
                    if (parts[0].equals("")) {
                        uri = "";
                    } else {
                        uri = getURIForPrefix(prefix, false);
                        if (uri == null) {
                            undeclaredNamespaceError(prefix, "XTSE0280", "errors");
                            result.add(AnyNodeTest.getInstance());
                            break;
                        }
                    }
                    localName = parts[1];
                } catch (QNameException err) {
                    compileErrorInAttribute("Error code " + s + " is not a valid QName", "XTSE0280", "errors");
                    result.add(AnyNodeTest.getInstance());
                    break;
                }
                NamePool target = getNamePool();
                int nameCode = target.allocateFingerprint(uri, localName);
                nt = new NameTest(Type.ELEMENT, nameCode, getNamePool());
                result.add(nt);
            }
        }
        return result;
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        select = typeCheck("select", select);
        if (select != null && hasChildNodes()) {
            compileError("An xsl:catch element with a select attribute must be empty", "XTSE3150");
        }
        if (!(getParent() instanceof XSLTry)) {
            compileError("xsl:catch may appear only as a child of xsl:try");
        }
    }

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (select == null) {
            select = compileSequenceConstructor(exec, decl, true);
        }
        ((XSLTry) getParent()).addCatchClause(nameTest, select);
        return null;
    }

}
