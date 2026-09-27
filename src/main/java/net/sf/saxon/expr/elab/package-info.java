/**
 * <p>This package contains classes that implement the elaboration mechanism for expression
 * evaluation.</p>
 *
 * <p>The first time an expression is evaluated, an {@link net.sf.saxon.expr.elab.Elaborator} is
 * allocated. The elaborator contains a number of methods for evaluating the expression in different
 * contexts: notably a {@link net.sf.saxon.expr.elab.PullElaborator} which evaluates the expression
 * one item at a time by pull iteration, and a {@link net.sf.saxon.expr.elab.PushElaborator} that
 * evaluates it by incrementally writing results to a supplied {@link net.sf.saxon.event.Outputter}.
 * These elaborators are called once to obtain a Java function (generally in the form of a lambda
 * expression) to do the actual evaluation: the idea is that as much of the logic as possible is
 * performed only once, and its results are captured in the closure of the lambda expression.</p>
 */
package net.sf.saxon.expr.elab;

// Copyright (c) 2025-2026 Saxonica Limited.
