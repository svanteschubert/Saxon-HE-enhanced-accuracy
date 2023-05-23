////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A "Clause" refers specifically to one of the clauses of a FLWOR expression, for example the "for"
 * clause, the "let" clause, the "where" or "order by" clause. (The "return" clause, however, is not
 * modelled as a Clause).
 */
public abstract class Clause {

    public enum ClauseName { FOR, LET, WINDOW, GROUP_BY, COUNT, ORDER_BY, WHERE, TRACE, FOR_MEMBER}

    private Location location;
    private PackageData packageData;
    private boolean repeated;

    /**
     * Get the location, which can be used to determine
     * the system ID and line number of the clause
     *
     * @return the location
     */
    public Location getLocation() {
        return location == null ? Loc.NONE : location;
    }

    /**
     * Set the location, which can be used to determine
     * the system ID and line number of the clause
     *
     * @param locationId the location
     */

    public void setLocation(Location locationId) {
        this.location = locationId;
    }

    public void setPackageData(PackageData pd) {
        this.packageData = pd;
    }

    public PackageData getPackageData() {
        return packageData;
    }

    public Configuration getConfiguration() {
        return packageData.getConfiguration();
    }

    public void setRepeated(boolean repeated) {
        this.repeated = repeated;
    }

    public boolean isRepeated() {
        return repeated;
    }

    /**
     * Create a copy of this clause
     *
     * @param flwor the new FLWORExpression to contain the copied clause
     * @param rebindings
     * @return the copied clause
     */

    public abstract Clause copy(FLWORExpression flwor, RebindingMap rebindings);

    /**
     * Optimize any expressions contained within this clause
     *
     * @param visitor         the ExpressionVisitor, providing access to static context information
     * @param contextItemType the type of the context item
     * @throws XPathException if any error is detected
     */
    public void optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
    }

    /**
     * Type-check any expression contained within this clause
     *
     *
     * @param visitor the ExpressionVisitor, providing access to static context information
     * @param contextInfo static information about the dynamic context
     * @throws XPathException if any error is detected
     */

    public void typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
    }

    /**
     * Get a pull-mode tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context the dynamic evaluation context
     * @return the output tuple stream
     */

    public abstract TuplePull getPullStream(TuplePull base, XPathContext context);

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param output the destination for the result
     * @param context     the dynamic evaluation context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */

    public abstract TuplePush getPushStream(TuplePush destination, Outputter output, XPathContext context);

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     * @throws XPathException if any error is detected
     */

    public abstract void processOperands(OperandProcessor processor) throws XPathException;

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */

    public abstract void explain(ExpressionPresenter out) throws XPathException;

    /**
     * Get the variables bound by this clause
     *
     * @return the variable bindings
     */

    public LocalVariableBinding[] getRangeVariables() {
        return new LocalVariableBinding[0];
    }

    /**
     * Build a list of all references to a variables declared in this clause
     *
     * @param visitor the expression visitor
     * @param binding a variable declared in this clause
     * @param refs    the list of variable references, initially empty, to which the method will append
     */

    public void gatherVariableReferences(final ExpressionVisitor visitor, Binding binding, List<VariableReference> refs) {
    }

    /**
     * Determine whether the clause contains a reference to a local variable binding that cannot be inlined
     *
     * @param binding the binding for the local variable in question
     * @return true if this clause uses the variable in a way that does not permit inlining
     */

    public boolean containsNonInlineableVariableReference(Binding binding) {
        return false;
    }

    /**
     * Supply improved type information to the expressions that contain references to the variables declared in this clause
     *
     * @param visitor    the expression visitor
     * @param references the list of variable references
     * @param returnExpr the expression in the return clause
     */

    public void refineVariableType(final ExpressionVisitor visitor, List<VariableReference> references, Expression returnExpr) {
    }

    /**
     * Collect information about the navigation paths followed by this clause, for document projection purposes
     *
     * @param pathMap        the path map in which the data is to be collected
     * @param pathMapNodeSet the path map node set representing the paths to the context item
     */

    public abstract void addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet);

    /**
     * Get a keyword identifying what kind of clause this is
     *
     * @return the kind of clause
     */

    public abstract ClauseName getClauseKey();

    /**
     * Get a short string representation of the clause
     * @return a recognizable string
     */

    public String toShortString() {
        return toString();
    }

    /**
     * Get information for inclusion in trace output
     * @return a map containing the properties to be output
     */

    public Map<String, Object> getTraceInfo() {
        LocalVariableBinding[] vars = getRangeVariables();
        if (vars.length == 0) {
            return Collections.emptyMap();
        } else {
            Map<String, Object> info = new HashMap<>(1);
            info.put("var", "$" + vars[0].getVariableQName().getDisplayName());
            return info;
        }
    }
}


