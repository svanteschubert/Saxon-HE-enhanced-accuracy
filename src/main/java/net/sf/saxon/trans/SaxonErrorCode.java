////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
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
     * SXSQ0005: Warning JDBC is not thread safe
     */

    public static final String SXSQ0005 = "SXSQ0005";

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
     * SXLM0001: stylesheet or query appears to be looping/recursing indefinitely
     */

    public static final String SXLM0001 = "SXLM0001";

    /**
     * SXLM0002: Max length of sequence exceeded
     */

    public static final String SXLM0002 = "SXLM0002";


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
     * SXPK0005: unresolved component reference in SEF file
     */

    public static final String SXPK0005 = "SXPK0005";

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
     * SXST0072: Exporting a stylesheet containing extensions instruction
     */

    public static final String SXST0072 = "SXST0072";
    
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
     * SXUP0081: attempt to update a non-updatable node
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

    /**
     * SXWN9019: stylesheet module included or imported more than once
     */

    public static final String SXWN9019 = "SXWN9019";

    /**
     * SXWN9020: unrecognized XSLT version
     */

    public static final String SXWN9020 = "SXWN9020";

    /**
     * SXWN9021: extension attribute ignored because not recognized in this Saxon version
     */

    public static final String SXWN9021 = "SXWN9021";

    /**
     * SXWN9022: warning returned by regular expression compiler
     */

    public static final String SXWN9022 = "SXWN9022";

    /**
     * SXWN9023: mode="#current" specified when not inside xsl:template
     */

    public static final String SXWN9023 = "SXWN9023";

    /**
     * SXWN9024: Fallback to non-streamed execution
     */

    public static final String SXWN9024 = "SXWN9024";

    /**
     * SXWN9025: Comparison will always be false
     */

    public static final String SXWN9025 = "SXWN9025";

    /**
     * SXWN9026: The only value that can pass type checking is an empty sequence
     */

    public static final String SXWN9026 = "SXWN9026";

    /**
     * SXWN9027: Expression is valid statically, but will always fail if executed
     */

    public static final String SXWN9027 = "SXWN9027";

    /**
     * SXWN9028: XPath Construct A/[XYZ] is probably not intended: try A/*[XYZ]
     */

    public static final String SXWN9028 = "SXWN9028";

    /**
     * SXWN9029: xsl:on-empty/xsl:on-non-empty in this context has no effect
     */

    public static final String SXWN9029 = "SXWN9029";

    /**
     * SXWN9030: creating an attribute or namespace is likely to fail because children have already been created
     */

    public static final String SXWN9030 = "SXWN9030";

    /**
     * SXWN9031: lax validation has no effect because there is no element/attribute declaration in the schema
     */

    public static final String SXWN9031 = "SXWN9031";

    /**
     * SXWN9032: Function result should be computed using xsl:sequence, not xsl:value-of
     */

    public static final String SXWN9032 = "SXWN9032";

    /**
     * SXWN9033: Value of sort key doesn't depend on the context item
     */

    public static final String SXWN9033 = "SXWN9033";

    /**
     * SXWN9034: Cannot resolve relative collation URI
     */

    public static final String SXWN9034 = "SXWN9034";

    /**
     * SXWN9035: Concatenation operator ('||') used with boolean operands
     */

    public static final String SXWN9035 = "SXWN9035";

    /**
     * SXWN9036: Suspicious use of curly braces in xsl:analyze-string/@regex
     */

    public static final String SXWN9036 = "SXWN9036";

    /**
     * SXWN9037: Result of evaluation will always be an empty sequence
     */

    public static final String SXWN9037 = "SXWN9037";

    /**
     * SXWN9038: Field name not defined in record type
     */

    public static final String SXWN9038 = "SXWN9038";

    /**
     * SXWN9039: Value will always be a singleton; occurrence indicator has no effect
     */

    public static final String SXWN9039 = "SXWN9039";

    /**
     * SXWN9040: Possible confusion between language keyword and element name
     */

    public static final String SXWN9040 = "SXWN9040";

    /**
     * SXWN9041: An attribute node cannot have a complex type
     */

    public static final String SXWN9041 = "SXWN9041";

    /**
     * SXWN9042: Unrecognized or invalid extension in Saxon namespace
     */

    public static final String SXWN9042 = "SXWN9042";

    /**
     * SXWN9043: Invalid or unrecognized serialization property
     */

    public static final String SXWN9043 = "SXWN9043";



    /**
     * SXWN9045: non-streamed input supplied for a streamable stylesheet
     */

    public static final String SXWN9045 = "SXWN9045";

    /**
     * SXWN9046: predicate [0] selects nothing
     */

    public static final String SXWN9046 = "SXWN9046";

    /**
     * SXWN9047: dynamic error evaluating expression used in XSD type alternative
     */

    public static final String SXWN9047 = "SXWN9047";

    /**
     * SXWN9048: An xs:ID element at the outermost level has no effect
     */

    public static final String SXWN9048 = "SXWN9048";

    /**
     * SXWN9049: Requested XQuery version not supported (request ignored)
     */

    public static final String SXWN9049 = "SXWN9049";

    /**
     * SXWN9050: Invalid document excluded from collection
     */

    public static final String SXWN9050 = "SXWN9050";

    /**
     * SXWN9051: Expression not guaranteed to have an EBV
     */

    public static final String SXWN9051 = "SXWN9051";

    /**
     * 4.0 incompatibility, union pattern whose branches have differing priority
     */

    public static final String SXWN9052 = "SXWN9052";

    /**
     * SXWN9053: saxon:as is deprecated in Saxon 13
     */

    public static final String SXWN9053 = "SXWN9053";

    /**
     * SXWN9054: saxon:capture is deprecated in Saxon 13
     */

    public static final String SXWN9054 = "SXWN9054";

    /**
     * SXWN9054: saxon:composite is ignored and deprecated in Saxon 13
     */

    public static final String SXWN9055 = "SXWN9055";


    /**
     * SXSD1000: unknown attribute group
     */
    public static final String SXSD1000 = "SXSD1000";

    /**
     * SXSD1001: unknown attribute declaration
     */
    public static final String SXSD1001 = "SXSD1001";

    /**
     * SXSD1002: invalid substitution group membership
     */
    public static final String SXSD1002 = "SXSD1002";
    /**
     * SXSD1003: unknown element declaration
     */
    public static final String SXSD1003 = "SXSD1003";

    /**
     * SXSD1004: field declaration may select no nodes, or multiple nodes
     */
    public static final String SXSD1004 = "SXSD1004";
    /**
     * SXSD1005: field declaration selects a node that cannot be atomized
     */
    public static final String SXSD1005 = "SXSD1005";
    /**
     * SXSD1006: unknown named model group
     */
    public static final String SXSD1006 = "SXSD1006";
    /**
     * SXSD1007: missing component in schema
     */
    public static final String SXSD1007 = "SXSD1007";
    /**
     * SXSD1008: unknown schema type
     */
    public static final String SXSD1008 = "SXSD1008";
    /**
     * SXSD1009: constraints in derived type may not be compatible with constraints in the base type
     */
    public static final String SXSD1009 = "SXSD1009";
    /**
     * SXSD1010: enumeration value is not a valid instance of the type
     */
    public static final String SXSD1010 = "SXSD1010";
    /**
     * SXSD1011: type of local element is not derived from type of global element
     */
    public static final String SXSD1011 = "SXSD1011";
    /**
     * SXSD1012: minOccurs/maxOccurs limits adjusted because out of supported range
     */
    public static final String SXSD1012 = "SXSD1012";
    /**
     * SXSD1014: unrecognized schema versioning attribute
     */
    public static final String SXSD1014 = "SXSD1014";
    /**
     * SXSD1015: use="prohibited" on an attribute group has no effect
     */
    public static final String SXSD1015 = "SXSD1015";
    /**
     * SXSD1016: redefined component is in the wrong schema module
     */
    public static final String SXSD1016 = "SXSD1016";
    /**
     * SXSD1017: target of xs:override is not a valid schema
     */
    public static final String SXSD1017 = "SXSD1017";




}

