////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.lib.FeatureKeys;

/**
 * The class acts as a register of Saxon-specific error codes.
 * <p>Technically, these codes should be in their own namespace. At present, however, they share the
 * same namespace as system-defined error codes.</p>
 */
public class SaxonErrorCode {

    /**
     * SXLM0001: stylesheet or query appears to be looping/recursing indefinitely
     */

    public static final String SXLM0001 = "SXLM0001";

    /**
     * SXCH0002: cannot supply output to ContentHandler because it is not well-formed
     */

    public static final String SXCH0002 = "SXCH0002";

    /**
     * SXCH0003: error reported by the ContentHandler (SAXResult) to which the result tree was sent
     */

    public static final String SXCH0003 = "SXCH0003";

    /**
     * SXCH0004: cannot load user-supplied ContentHandler
     */

    public static final String SXCH0004 = "SXCH0004";

    /**
     * SXCH0005: invalid pseudo-attribute syntax
     */

    public static final String SXCH0005 = "SXCH0005";

    /**
     * SXRE0001: stack overflow within regular expression evaluation
     */

    public static final String SXRE0001 = "SXRE0001";


    /**
     * SXSE0001: cannot use character maps in an environment with no Controller
     */

    public static final String SXSE0001 = "SXSE0001";

    /**
     * SXSE0002: cannot use output property saxon:supply-source-locator unless tracing was enabled at compile time
     */

    public static final String SXSE0002 = "SXSE0002";

    /**
     * SXXP0003: error reported by XML parser while parsing source document. This error code means that an
     * error occurred at the XML parsing level; the error was not detected by Saxon itself, but rather by
     * the underlying parser. It usually means either that the document (or one of its component entities)
     * could not be found, or that the content was not well formed, or it might be an encoding problem.
     */

    public static final String SXXP0003 = "SXXP0003";

    /**
     * SXXP0004: externally supplied node belongs to the wrong Configuration. Saxon requires that when
     * a stylesheet or query is run against a particular source document, the Configuration used to build
     * that source document is the same Configuration, or a compatible Configuration, as the one used to
     * compile the stylesheet or query. Two different configurations are compatible if they share the same
     * NamePool. The purpose of the rule is to ensure that the mapping from QNames to integer fingerprints
     * used in the stylesheet or query is the same as the mapping used in the source document. The constraint
     * applies to all nodes used in the stylesheet or query, whether in the initial source document, in a
     * document returned by the doc() or collection() function, or a node returned by a call on an external
     * (extension) function.
     */

    public static final String SXXP0004 = "SXXP0004";

    /**
     * SXXP0005: namespace of source document doesn't match namespace of the template rules in the stylesheet.
     * This is a warning suggesting that the stylesheet might be making the common mistake of using unprefixed
     * element names in match patterns and path expressions, when the source document actually uses a default
     * namespace. Often the problem can be solved by adding to the xsl:stylesheet element the attribute
     * xpath-default-namespace="URI", where URI is the namespace declared in the source document.
     *
     * <p>The check that generates this warning can be suppressed by setting the Configuration option
     * {@link FeatureKeys#SUPPRESS_XSLT_NAMESPACE_CHECK}. On the command line this can be done
     * by writing "--suppressXsltNamespaceCheck:on".</p>
     *
     * <p>The actual test that results in this warning is as follows. The root element of the principal
     * source document must be in a namespace N; and the set of template rules in the initial mode must
     * include no match pattern that explicitly matches a named element in namespace N, but must contain
     * at least one match pattern that matches named elements in some other namespace, or in no namespace.</p>
     */

    public static final String SXXP0005 = "SXXP0005";

    /**
     * SXXP0006: general error in schema processing/validation
     */

    public static final String SXXP0006 = "SXXP0006";

    /**
     * SXSQ0001: value of argument to SQL instruction is not a JDBC Connection object
     */

    public static final String SXSQ0001 = "SXSQ0001";

    /**
     * SXSQ0002: failed to close JDBC Connection
     */

    public static final String SXSQ0002 = "SXSQ0002";

    /**
     * SXSQ0003: failed to open JDBC Connection
     */

    public static final String SXSQ0003 = "SXSQ0003";

    /**
     * SXSQ0004: SQL Insert/Update/Delete action failed
     */

    public static final String SXSQ0004 = "SXSQ0004";

    /**
     * SXJE0001:  Must supply an argument for a non-static extension function
     */

    public static final String SXJE0001 = "SXJE0001";

    /**
     * SXJE0005: cannot convert xs:string to Java char unless the length is exactly one
     */

    public static final String SXJE0005 = "SXJE0005";

    /**
     * SXJE0051: supplied Java List/Array contains a member that cannot be converted to an Item
     */

    public static final String SXJE0051 = "SXJE0051";

    /**
     * SXJE0052: exception thrown by extension function
     */

    public static final String SXJE0052 = "SXJE0052";

    /**
     * SXJE0053: I/O error in saxon-read-binary-resource
     */

    public static final String SXJE0053 = "SXJE0053";

    /**
     * SXJM0001: Error in arguments to saxon:send-mail
     */

    public static final String SXJM0001 = "SXJM0001";

    /**
     * SXJM0002: Failure in saxon:send-mail reported by mail service
     */

    public static final String SXJM0002 = "SXJM0002";

    /**
     * SXOR0001: XSD saxon:ordered constraint not satisfied
     */

    public static final String SXOR0001 = "SXOR0001";

    /**
     * SXJX0001: integer in input to octets-to-base64Binary or octets-to-hexBinary is out of range 0-255
     */

    public static final String SXJX0001 = "SXJX0001";

    /**
     * SXJS0001: Cannot export for Javascript if the stylesheet uses unsupported features
     */

    public static final String SXJS0001 = "SXJS0001";

    /**
     * SXPK0001: No binding available for call-template instruction
     */

    public static final String SXPK0001 = "SXPK0001";

    /**
     * SXPK0002: invalid content found in compiled package
     */

    public static final String SXPK0002 = "SXPK0002";

    /**
     * SXPK0003: stylesheet package has unsatisfied schema dependency
     */

    public static final String SXPK0003 = "SXPK0003";

    /**
     * SXPK0004: documentation namespace can be used only for documentation
     */

    public static final String SXPK0004 = "SXPK0004";

    /**
     * SXRD0001: URI supplied to xsl:result-document does not identify a writable destination
     */

    public static final String SXRD0001 = "SXRD0001";

    /**
     * SXRD0002: Base output URI for xsl:result-document is unknown
     */

    public static final String SXRD0002 = "SXRD0002";

    /**
     * SXRD0003: Failure while closing the xsl:result-document destination after writing
     */

    public static final String SXRD0003 = "SXRD0003";

    /**
     * SXRD0004: Unwritable file given as the result destination
     */

    public static final String SXRD0004 = "SXRD0004";

    /**
     * SXST0001: Static error in template rule, found during JIT compilation
     */

    public static final String SXST0001 = "SXST0001";

    /**
     * SXST0060: Template in a streaming mode is not streamable
     */

    public static final String SXST0060 = "SXST0060";

    /**
     * SXST0061: Requested initial mode is streamable; must supply SAXSource or StreamSource
     */

    public static final String SXST0061 = "SXST0061";

    /**
     * SXST0062: Component cannot be streamed, though it should be streamable
     */

    public static final String SXST0062 = "SXST0062";

    /**
     * SXST0065: Cannot use tracing with streaming templates
     */

    public static final String SXST0065 = "SXST0065";

    /**
     * SXST0066: Cannot disable optimization when xsl:stream is used
     */

    public static final String SXST0066 = "SXST0066";

    /**
     * SXST0067: Internal problem executing expression in streaming mode
     */

    public static final String SXST0067 = "SXST0067";

    /**
     * SXST0068: This configuration does not allow streaming
     */

    public static final String SXST0068 = "SXST0068";

    /**
     * SXST0069: Exporting a stylesheet containing static references to XQuery functions
     */

    public static final String SXST0069 = "SXST0069";

    /**
     * SXST0070: Exporting a stylesheet containing static references to external Java objects
     */

    public static final String SXST0070 = "SXST0070";

    /**
     * SXST0071: Exporting a stylesheet containing static references to saxon:tabulate-maps instruction
     */

    public static final String SXST0071 = "SXST0071";


    /**
     * SXTA0001: unresolved type alias
     */
    public static final String SXTA0001 = "SXTA0001";

    /**
     * SXTM0001: tabulate-maps: selecting an item with no pedigree
     */
    public static final String SXTM0001 = "SXTM0001";


    /**
     * SXTT0001: field name not defined in tuple type
     */
    public static final String SXTT0001 = "SXTT0001";


    /**
     * SXUP0081: attempt to update a non-updateable node
     */

    public static final String SXUP0081 = "SXUP0081";

    /**
     * SXWN9000: miscellaneous warning message
     */

    public static final String SXWN9000 = "SXWN9000";

    /**
     * SXWN9001: a variable declaration with no following siblings has no effect
     */

    public static final String SXWN9001 = "SXWN9001";

    /**
     * SXWN9002: saxon:indent-spaces must be a positive integer
     */

    public static final String SXWN9002 = "SXWN9002";

    /**
     * SXWN9003: saxon:require-well-formed must be "yes" or "no"
     */

    public static final String SXWN9003 = "SXWN9003";

    /**
     * SXWN9004: saxon:next-in-chain cannot be specified dynamically
     */

    public static final String SXWN9004 = "SXWN9004";

    /**
     * SXWN9005: The 'default' attribute of saxon:collation no longer has any effect
     */

    public static final String SXWN9005 = "SXWN9005";

    /**
     * SXWN9006: No schema-location was specified, and no schema with the requested target namespace
     * is known, so the schema import was ignored
     */

    public static final String SXWN9006 = "SXWN9006";

    /**
     * SXWN9007: Cannot use reserved namespace in extension-element-prefixes
     */

    public static final String SXWN9007 = "SXWN9007";


    /**
     * SXWN9008: Saxon extension element not recognized because namespace not declared
     * in extension-element-prefixes
     */

    public static final String SXWN9008 = "SXWN9008";

    /**
     * SXWN9009: an empty xsl:for-each or xsl:for-each-group has no effect
     */

    public static final String SXWN9009 = "SXWN9009";

    /**
     * SXWN9010: saxon:recognize-binary must be "yes" or "no"
     */

    public static final String SXWN9010 = "SXWN9010";

    /**
     * SXWN9011: saxon:memo-function ignored under Saxon-HE
     */

    public static final String SXWN9011 = "SXWN9011";

    /**
     * SXWN9012: saxon:threads ignored when compiling with trace enabled
     */

    public static final String SXWN9012 = "SXWN9012";

    /**
     * SXWN9013: saxon:threads ignored when not running under Saxon-EE
     */

    public static final String SXWN9013 = "SXWN9013";

    /**
     * SXWN9014: xsl:function/@override is deprecated in 3.0
     */

    public static final String SXWN9014 = "SXWN9014";

    /**
     * SXWN9015: Pattern will never match anything
     */

    public static final String SXWN9015 = "SXWN9015";

    /**
     * SXWN9016: saxon:assign used with multi-threading enabled
     */

    public static final String SXWN9016 = "SXWN9016";

    /**
     * SXWN9017: saxon:copy-of copying accumulators pointlessly
     */

    public static final String SXWN9017 = "SXWN9017";

    /**
     * SXWN9018: warning during schema processing
     */

    public static final String SXWN9018 = "SXWN9018";


}

