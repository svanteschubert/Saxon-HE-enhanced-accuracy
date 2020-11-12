////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.query.XQueryParser;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

/**
 * Parser to handle QNames in either lexical QName or EQName syntax, including resolving any prefix against
 * a URIResolver. The parser can be instantiated with various options to control the returned error code,
 * the handling of defaults, etc.
 *
 * The QNameParser is immutable; its properties are set using the <code>withProperty()</code> fluent API style.
 */
public class QNameParser {

    private NamespaceResolver resolver;
    private boolean acceptEQName = false;
    private String errorOnBadSyntax = "XPST0003";
    private String errorOnUnresolvedPrefix = "XPST0081";
    private XQueryParser.Unescaper unescaper = null;

    public QNameParser(NamespaceResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Set the namespace resolver to be used
     * @param resolver the namespace resolver
     * @return a new QNameParser using the specified NamespaceResolver
     */

    public QNameParser withNamespaceResolver(NamespaceResolver resolver) {
        QNameParser qp2 = copy();
        qp2.resolver = resolver;
        return qp2;
    }

    /**
     * Say whether URI-qualified names in <code>Q{uri}local</code> format should be accepted
     * @param acceptEQName true if extended QName syntax is accepted
     * @return a new QNameParser with the requested properties
     */

    public QNameParser withAcceptEQName(boolean acceptEQName) {
        if (acceptEQName == this.acceptEQName) {
            return this;
        }
        QNameParser qp2 = copy();
        qp2.acceptEQName = acceptEQName;
        return qp2;
    }

    /**
     * Say what error code should be thrown when the QName syntax is invalid
     * @param code the error code to be thrown
     * @return a new QNameParser with the requested properties
     */

    public QNameParser withErrorOnBadSyntax(String code) {
        if (code.equals(errorOnBadSyntax)) {
            return this;
        }
        QNameParser qp2 = copy();
        qp2.errorOnBadSyntax = code;
        return qp2;
    }

    /**
     * Say what error code should be thrown when the prefix is undeclared
     * @param code the error code to be thrown
     * @return a new QNameParser with the requested properties
     */

    public QNameParser withErrorOnUnresolvedPrefix(String code) {
        if (code.equals(errorOnUnresolvedPrefix)) {
            return this;
        }
        QNameParser qp2 = copy();
        qp2.errorOnUnresolvedPrefix = code;
        return qp2;
    }

    /**
     * Supply a callback used to unescape special characters appearing in the URI part of an EQName
     * @param unescaper the callback to be used
     * @return a new QNameParser with the requested properties
     */

    public QNameParser withUnescaper(XQueryParser.Unescaper unescaper) {
        QNameParser qp2 = copy();
        qp2.unescaper = unescaper;
        return qp2;
    }

    private QNameParser copy() {
        QNameParser qp2 = new QNameParser(resolver);
        qp2.acceptEQName = acceptEQName;
        qp2.errorOnBadSyntax = errorOnBadSyntax;
        qp2.errorOnUnresolvedPrefix = errorOnUnresolvedPrefix;
        qp2.unescaper = unescaper;
        return qp2;
    }

    /**
     * Make a structured QName from a lexical QName, using a supplied NamespaceResolver to
     * resolve the prefix. The local part of the QName is checked for validity; the prefix is
     * not checked, on the grounds that an invalid prefix will fail to resolve to a URI.
     *
     * @param lexicalName the QName as a lexical name (prefix:local)
     * @param defaultNS the default namespace to use if there is no prefix
     * @return the StructuredQName object corresponding to this lexical QName
     * @throws net.sf.saxon.trans.XPathException if the namespace prefix is not in scope or if the value is lexically
     *                        invalid. Error code FONS0004 is set if the namespace prefix has not been declared; error
     *                        code FOCA0002 is set if the name is lexically invalid. These may need to be
     *                        changed on return depending on the caller's requirements.
     */

    public StructuredQName parse(CharSequence lexicalName, String defaultNS) throws XPathException {
        lexicalName = Whitespace.trimWhitespace(lexicalName);
        if (acceptEQName && lexicalName.length() >= 4 && lexicalName.charAt(0) == 'Q' && lexicalName.charAt(1) == '{') {
            String name = lexicalName.toString();
            int endBrace = name.indexOf('}');
            if (endBrace < 0) {
                throw new XPathException("Invalid EQName: closing brace not found", errorOnBadSyntax);
            } else if (endBrace == name.length() - 1) {
                throw new XPathException("Invalid EQName: local part is missing", errorOnBadSyntax);
            }
            String uri = Whitespace.collapseWhitespace(name.substring(2, endBrace)).toString();
            if (uri.contains("{")) {
                throw new XPathException("Invalid EQName: URI contains opening brace", errorOnBadSyntax);
            }
            //String uri = Whitespace.collapseWhitespace(name.substring(2, endBrace)).toString();
            if (unescaper != null && uri.contains("&")) {
                uri = unescaper.unescape(uri).toString();
            }
            if (uri.equals(NamespaceConstant.XMLNS)) {
                throw new XPathException("The string '" + NamespaceConstant.XMLNS + "' cannot be used as a namespace URI", "XQST0070");
            }
            String local = name.substring(endBrace + 1);
            checkLocalName(local);
            return new StructuredQName("", uri, local);
        }
        try {
            String[] parts = NameChecker.getQNameParts(lexicalName);
            checkLocalName(parts[1]);
            if (parts[0].isEmpty()) {
                return new StructuredQName("", defaultNS, parts[1]);
            }
            String uri = resolver.getURIForPrefix(parts[0], false);
            if (uri == null) {
                throw new XPathException("Namespace prefix '" + parts[0] + "' has not been declared", errorOnUnresolvedPrefix);
            }
            return new StructuredQName(parts[0], uri, parts[1]);
        } catch (QNameException e) {
            throw new XPathException(e.getMessage(), errorOnBadSyntax);
        }
    }

    private void checkLocalName(String local) throws XPathException {
        if (!NameChecker.isValidNCName(local)) {
            throw new XPathException("Invalid EQName: local part is not a valid NCName", errorOnBadSyntax);
        }
    }

}

