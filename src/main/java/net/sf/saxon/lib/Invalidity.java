////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.om.AbsolutePath;
import net.sf.saxon.om.NodeInfo;

import javax.xml.transform.SourceLocator;

/**
 * An {@code Invalidity} is a validation error, that is, a failure of the instance document
 * to conform to the schema being used for validation.
 *
 * <p>The class extends {@link SourceLocator}, which makes location information available (for example,
 * a line and column number). This information will generally be the location of the error as it appears
 * in error reports intended for a human reader. It is not necessarily the same as the location of the
 * invalid node. For example, if the schema type for element A does not allow a child element named B,
 * but in the instance document there is an A element with a B child, then the location information
 * may relate to element B, whereas it is A that is invalid.</p>
 */
public interface Invalidity extends SourceLocator {

    /**
     * Get the part number of the XSD schema specification containing the constraint that
     * has been violated
     * @return 1 (XSD part 1, structures) or 2 (XSD part 2, data types)
     */

    int getSchemaPart();

    /**
     * Get the name of the constraint that has been violated
     * @return the name of the constraint as defined in the XSD specification, for example "cvc-datatype-valid"
     */

    String getConstraintName();

    /**
     * Get the clause number of the rule that has been violated
     * @return the clause number as a string, for example "3.4.1"
     */

    String getConstraintClauseNumber();

    /**
     * Get the constraint name and clause in the format defined in XML Schema Part 1 Appendix C (Outcome Tabulations).
     * This mandates the format validation-rule-name.clause-number
     *
     * @return the constraint reference, for example "cos-ct-extends.1.2"; or null if the reference
     * is not known.
     */
    String getConstraintReference();

    /**
     * Get the node that was found to be invalid (if any). This will be available only when
     * validating an input document supplied in the form of an in-memory tree
     * @return the invalid node, if it is known, or null
     */

    NodeInfo getInvalidNode();

    /**
     * Get a hierarchic path giving the logical position in the instance document where the
     * validation error was found
     * @return a path to the location in the document where the error was found. This is not necessarily
     * the same thing as the node that is invalid: for example if the schema type for element A does
     * not allow a child element named B, but in the instance document there is an A element with a B
     * child, then the path returned may be the path to B, whereas it is A that is invalid.
     */

    AbsolutePath getPath();

    /**
     * Get a hierarchic path giving the node being validated (the validation root). If validation
     * is invoked from XSLT this might be a node selected by the instruction {@code <xsl:copy-of select="X"
     * validation="strict"/>}; in XQuery it might be the target of a {@code validate} expression.
     * @return a path to the location in the document
     */

    AbsolutePath getContextPath();

    /**
     * Get the text of a message explaining what is wrong
     * @return a human-readable message explaining the validity error
     */

    String getMessage();

    /**
     * Get the error code associated with the validity error. This is relevant only when validation
     * is run from within XSLT or XQuery, which define different error codes for validition errors
     * @return the error code associated with the error, if any. The error is returned as a simple
     * string if it is in the standard error namespace, or as an EQName (that is Q{uri}local) otherwise.
     */

    String getErrorCode();
}

