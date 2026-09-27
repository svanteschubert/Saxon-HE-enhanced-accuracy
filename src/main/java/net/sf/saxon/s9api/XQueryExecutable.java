////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.value.Cardinality;

/**
 * An {@code XQueryExecutable} represents the compiled form of a query.
 * To execute the query, it must first be loaded to form an {@link net.sf.saxon.s9api.XQueryEvaluator}.
 * <p>An {@code XQueryExecutable} is immutable, and therefore thread-safe.
 * It is simplest to use the {@link #load()} method to load a new {@link XQueryEvaluator}
 * each time the query is to be run.
 * However, the {@code XQueryEvaluator} is serially reusable within a single thread. </p>
 * <p>An {@code XQueryExecutable} is created by using one of the <code>compile</code> methods on the
 * {@link net.sf.saxon.s9api.XQueryCompiler} class.</p>
 *
 * @since 9.0
 */
@CSharpModifiers(code = {"internal"})
public class XQueryExecutable {

    Processor processor;
    XQueryExpression exp;

    protected XQueryExecutable(Processor processor, XQueryExpression exp) {
        this.processor = processor;
        this.exp = exp;
    }

    /**
     * Load the query to prepare it for execution.
     *
     * @return An {@link XQueryEvaluator}. The returned {@code XQueryEvaluator} can be used to set up the
     *         dynamic context for query evaluation, and to run the query.
     */

    public XQueryEvaluator load() {
        return new XQueryEvaluator(processor, exp);
    }

    /**
     * Get the {@link ItemType} of the items in the result of the query, as determined by static analysis. This
     * is the most precise ItemType that the processor is able to determine from static examination of the
     * query; the actual items in the query result are guaranteed to belong to this ItemType or to a subtype
     * of this ItemType.
     *
     * @return the statically-determined ItemType of the result of the query
     * @since 9.1
     */

    public ItemType getResultItemType() {
        net.sf.saxon.type.ItemType it = exp.getExpression().getItemType();
        return new ConstructedItemType(it, processor.getUnderlyingConfiguration());
    }

    /**
     * Get the statically-determined cardinality of the result of the query. This is the most precise cardinality
     * that the processor is able to determine from static examination of the query.
     *
     * @return the statically-determined cardinality of the result of the query
     * @since 9.1
     */

    /*@NotNull*/
    public OccurrenceIndicator getResultCardinality() {
        int card = exp.getExpression().getCardinality();
        return Cardinality.getOccurrenceIndicatorForCardinality(card);
    }

    /**
     * Ask whether the query is an updating query: that is, whether it returns a Pending Update List
     * rather than a Value. Note that queries using the XUpdate copy-modify syntax are not considered
     * to be updating queries.
     *
     * @return true if the query is an updating query, false if not
     * @since 9.1
     */

    public boolean isUpdateQuery() {
        return exp.isUpdateQuery();
    }

    /**
     * Produce a diagnostic representation of the compiled query, in XML form.
     * <p><i>The detailed form of this representation is not stable (or even documented).</i></p>
     *
     * @param destination the destination for the XML document containing the diagnostic representation
     *                    of the compiled query
     * @throws SaxonApiException if an error is detected
     * @since 9.6
     */

    public void explain(Destination destination) throws SaxonApiException {
        Configuration config = processor.getUnderlyingConfiguration();
        try {
            PipelineConfiguration pipe = config.makePipelineConfiguration();
            exp.explain(new ExpressionPresenter(config, destination.getReceiver(pipe, config.obtainDefaultSerializationProperties())));
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the underlying implementation object representing the compiled query. This provides
     * an escape hatch into lower-level APIs. The object returned by this method may change from release
     * to release.
     *
     * @return the underlying implementation of the compiled query
     */

    public XQueryExpression getUnderlyingCompiledQuery() {
        return exp;
    }
}

