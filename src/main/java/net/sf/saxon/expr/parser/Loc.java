////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.s9api.Location;
import org.xml.sax.Locator;

import javax.xml.transform.SourceLocator;

/**
 * Class to hold details of the location of an expression, of an error in a source file, etc.
 * The object is immutable. Previous names: ExpressionLocation, ExplicitLocation.
 */

public class Loc implements Location {

    private String systemId;
    private int lineNumber;
    private int columnNumber = -1;

    public static Loc NONE = new Loc(null, -1, -1);

    /**
     * Create an ExpressionLocation, taking the data from a supplied JAXP SourceLocator
     *
     * @param loc the JAXP SourceLocator
     */

    public Loc(SourceLocator loc) {
        systemId = loc.getSystemId();
        lineNumber = loc.getLineNumber();
        columnNumber = loc.getColumnNumber();
    }

    /**
     * Create an ExpressionLocation, taking the data from a supplied SAX Locator
     *
     * @param loc the SAX Locator
     */

    public static Loc makeFromSax(Locator loc) {
        return new Loc(loc.getSystemId(), loc.getLineNumber(), loc.getColumnNumber());
    }

    /**
     * Create an ExpressionLocation corresponding to a given module, line number, and column number
     *
     * @param systemId     the module URI
     * @param lineNumber   the line number (starting at 1; -1 means unknown)
     * @param columnNumber the column number (starting at 1; -1 means unknown)
     */

    public Loc(String systemId, int lineNumber, int columnNumber) {
        this.systemId = systemId;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    /**
     * Get the system ID (the module URI)
     *
     * @return the system ID
     */

    @Override
    public String getSystemId() {
        return systemId;
    }

    /**
     * Get the Public ID
     *
     * @return always null in this implementation
     */

    /*@Nullable*/
    @Override
    public String getPublicId() {
        return null;
    }

    /**
     * Get the line number
     *
     * @return the line number
     */

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Get the column number
     *
     * @return the column number
     */

    @Override
    public int getColumnNumber() {
        return columnNumber;
    }

    /**
     * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
     * should not be saved for later use. The result of this operation holds the same location information,
     * but in an immutable form.
     */
    @Override
    public Location saveLocation() {
        return this;
    }

    /**
     * Ask whether this is an "unknown location"
     */

    public static boolean isUnknown(Location location) {
        return location == null || (location.getSystemId() == null || location.getSystemId().isEmpty())
                && location.getLineNumber() == -1;
    }


}
