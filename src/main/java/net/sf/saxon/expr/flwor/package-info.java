/**
 * <p>This package contains classes responsible for evaluation of FLWOR expressions.</p>
 * <p>FLWOR expressions are implemented as a pipeline of clauses, much as described in the specification.
 * The pipeline can be evaluated in push or pull mode: in push mode, the supplier of tuples activates the consumer
 * of tuples when a tuple is ready to be processed, while in pull mode, the consumer of tuples calls the supplier
 * to request the next tuple. In both cases the "tuple" is not actually passed as an argument or result of this call,
 * but is represented by the state of local variables in the XPathContext stack on completion of the call. The only
 * time tuples are represented as real objects is when the processing is not pipelined, for example when tuples need
 * to be sorted or grouped.</p>
 * <p>Simple "for" and "let" expressions do not use this mechanism: instead, they are compiled to a
 * {@link net.sf.saxon.expr.ForExpression} or {@link net.sf.saxon.expr.LetExpression}.</p>
 */
package net.sf.saxon.expr.flwor;
