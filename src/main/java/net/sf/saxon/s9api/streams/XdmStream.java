////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api.streams;

import net.sf.saxon.s9api.*;
import net.sf.saxon.tree.jiter.IterableUsingIteratorSupplier;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * <code>XdmStream</code> extends the capabilities of the standard JDK {@link Stream} class.
 *
 * <p>The extensions are:</p>
 * <ul>
 *    <li>Additional terminal operations are provided, allowing the results of the
 *           stream to be delivered for example as a <code>List&lt;XdmNode&gt;</code> or
 *           an <code>Optional&lt;XdmNode&gt;</code> more conveniently than using the general-purpose
 *           {@link Collector} interface.</li>
 *    <li>Many of these terminal operations are short-circuiting, that is, they
 *           stop processing input when no further input is required.</li>
 *    <li>The additional terminal operations throw a checked exception if a dynamic
 *           error occurs while generating the content of the stream.</li>
 * </ul>
 *
 * <p>The implementation is customized to streams of {@link XdmItem}s.</p>
 * <p>Note: This class is implemented by wrapping a base stream. Generally, the
 *           methods on this class delegate to the base stream; those methods that
 *           return a stream wrap the stream returned by the base class. The context object can be used by a terminal
 *           method on the XdmStream to signal to the originator of the stream
 *           that no further input is required.</p>
 *
 * @param <T> The type of items delivered by the stream.
 */

public class XdmStream<T extends XdmItem> implements Stream<T> {

    Stream<T> base;

    /**
     * Create an {@link XdmStream} from a general {@link Stream} that returns XDM items.
     * @param base the stream of items
     */

    public XdmStream(Stream<T> base) {
        this.base = base;
    }

    /**
     * Create an {@link XdmStream} consisting of zero or one items, supplied in the form
     * of an <code>Optional&lt;XdmItem&gt;</code>
     *
     * @param input the optional item
     */

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public XdmStream(Optional<T> input) {
        this.base = input.map(Stream::of).orElseGet(Stream::empty);
    }

    /**
     * Filter a stream of items, to create a new stream containing only those items
     * that satisfy a supplied condition.
     *
     * <p>For example, {@code body.select(child("*")).filter(n -&gt; n.getNodeName().getLocalName().startsWith("h"))}
     * returns a stream of all the child elements of <code>body</code> whose local name starts with "h".</p>
     * <p>Note: an alternative to filtering a stream is to use a {@link Step} that incorporates
     * a {@link Predicate}, for example {@code body.select(child("*").where(n -> n.getNodeName().getLocalName().startsWith("h")))}</p>
     * @param predicate the supplied condition. Any <code>Predicate</code> can be supplied,
     *                  but some particularly useful predicates are available by calling
     *                  static methods on the {@link Predicates} class, for example
     *                  <code>Predicates.empty(Steps.child("author"))</code>, which is true
     *                  for a node that has no child elements with local name "author".
     * @return the filtered stream
     */

    @Override
    public XdmStream<T> filter(Predicate<? super T> predicate) {
        return new XdmStream<>(base.filter(predicate));
    }

    /**
     * Returns a stream consisting of the results of applying the given
     * function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * <p>For example, <code>n.select(child(*)).map(c -&gt; c.getNodeName().getLocalName())</code>
     * returns a stream delivering the local names of the element children of <code>n</code>,
     * as instances of <code>java.lang.String</code>.
     * Note the result is a {@link Stream}, not an {@link XdmStream}.</p>
     *
     * @param <R>    The element type of the new stream
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        return base.map(mapper);
    }

    /**
     * Returns an {@code IntStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>For example, <code>n.select(child(*)).map(c -&gt; c.getStringValue().length())</code>
     * returns a stream delivering the lengths of the string-values of the element children of <code>n</code>.
     * Note the result is a {@link Stream}, not an {@link XdmStream}.</p>
     *
     * <p>This is an <a href="package-summary.html#StreamOps">
     * intermediate operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return base.mapToInt(mapper);
    }

    /**
     * Returns a {@code LongStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return base.mapToLong(mapper);
    }

    /**
     * Returns a {@code DoubleStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return base.mapToDouble(mapper);
    }

    /**
     * Returns a stream consisting of the results of replacing each element of
     * this stream with the contents of a mapped stream produced by applying
     * the provided mapping function to each element.  Each mapped stream is
     * {@link java.util.stream.BaseStream#close() closed} after its contents
     * have been placed into this stream.  (If a mapped stream is {@code null}
     * an empty stream is used, instead.)
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.</p>
     *
     * <p>Note: The {@code flatMap()} operation has the effect of applying a one-to-many
     *     transformation to the elements of the stream, and then flattening the
     *     resulting elements into a new stream. This corresponds to the action
     *     of the "!" operator in XPath.</p>
     *
     * @param <R>    The element type of the new stream
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces a stream
     *               of new values
     * @return the new stream
     */
    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return base.flatMap(mapper);
    }

    /**
     * Create a new {@link XdmStream} by applying a mapping function (specifically, a {@link Step})
     * to each item in the stream. The {@link Step} returns a sequence of items, which are inserted
     * into the result sequence in place of the original item.
     * <p>This method is similar to {@link #flatMap}, but differs in that it returns an {@link XdmStream},
     * making additional methods available.</p>
     * <p>Note: {@link XdmValue#select} is implemented using this method, and in practice it is
     * usually clearer to use that method directly. For example {@code node.select(child("*")).flatMapToXdm(child(*))}
     * can be written as {@code node.select(child("*").then(child("*"))}. Both expressions return a stream containing
     * all the grandchildren elements of {@code node}. The same result can be achieved more concisely by writing
     * {@code node.select(path("*", "*"))}</p>
     * @param mapper the mapping function
     * @param <U> the type of items returned by the mapping function
     * @return a new stream of items
     * @since 9.9
     */

    public <U extends XdmItem> XdmStream<U> flatMapToXdm(Step<U> mapper) {
        return new XdmStream<>(base.flatMap(mapper));
    }

    /**
     * Create a new {@link XdmStream} by applying a mapping function (specifically, a {@link Step})
     * to each item in the stream. The {@link Step} returns a sequence of items, which are inserted
     * into the result sequence in place of the original item.
     * <p>This method is similar to {@link #flatMap}, but differs in that it returns an {@link XdmStream},
     * making additional methods available.</p>
     * <p>Note: {@link XdmValue#select} is implemented using this method, and in practice it is
     * usually clearer to use that method directly. For example {@code node.select(child("*")).flatMapToXdm(child(*))}
     * can be written as {@code node.select(child("*").then(child("*"))}. Both expressions return a stream containing
     * all the grandchildren elements of {@code node}. The same result can be achieved more concisely by writing
     * {@code node.select(path("*", "*"))}</p>
     * @param mapper the mapping function
     * @param <U> the type of items returned by the mapping function
     * @return a new stream of items
     * @since 13. The method is introduced as a synonym of {@link #flatMapToXdm(Step)}
     */

    public <U extends XdmItem> XdmStream<U> select(Step<U> mapper) {
        return new XdmStream<>(base.flatMap(mapper));
    }

    /**
     * Select children of the nodes in this stream. This is a shortcut for
     * <code>select(Steps.child(nodeName))</code>; it returns an <code>XdmStream</code>
     * containing all the child elements of nodes in this <code>XdmStream</code> that have
     * local name <code>nodeName</code>; or if <code>nodeName</code> is <code>"*"</code>,
     * then all child elements regardless of their name.
     *
     * @param nodeName the local name of the required child elements, or <code>"*"</code>
     *                 to select all element children
     * @since 13
     */

    public XdmStream<XdmNode> select(String nodeName) {
        return select(Steps.child(nodeName));
    }

    /**
     * Select children of the nodes in this stream. This is a shortcut for
     * <code>select(Steps.child(uri, localName))</code>; it returns an <code>XdmStream</code>
     * containing all the child elements of nodes in this <code>XdmValue</code> that have
     * local name <code>localName</code> and namespace URI <code>uri</code>.
     *
     * @param uri       the namespace URI of the child elements to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the child elements to be selected
     * @since 13
     */
    public XdmStream<XdmNode> select(String uri, String localName) {
        return select(Steps.child(uri, localName));
    }

    /**
     * Select children of the nodes in this stream. This is a shortcut for
     * <code>select(Steps.child(name))</code>; it returns an <code>XdmStream</code>
     * containing all the child elements of nodes in this <code>XdmValue</code> that have
     * the name <code>name</code>.
     *
     * @param name the name of the child elements to be selected
     * @since 13
     */
    public XdmStream<XdmNode> select(QName name) {
        return select(Steps.child(name));
    }


    /**
     * Returns an {@code IntStream} consisting of the results of replacing each
     * element of this stream with the contents of a mapped stream produced by
     * applying the provided mapping function to each element.  Each mapped
     * stream is {@link java.util.stream.BaseStream#close() closed} after its
     * contents have been placed into this stream.  (If a mapped stream is
     * {@code null} an empty stream is used, instead.)
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces a stream
     *               of new values
     * @return the new stream
     * @see #flatMap(Function)
     */

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return base.flatMapToInt(mapper);
    }

    /**
     * Returns an {@code LongStream} consisting of the results of replacing each
     * element of this stream with the contents of a mapped stream produced by
     * applying the provided mapping function to each element.  Each mapped
     * stream is {@link java.util.stream.BaseStream#close() closed} after its
     * contents have been placed into this stream.  (If a mapped stream is
     * {@code null} an empty stream is used, instead.)
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces a stream
     *               of new values
     * @return the new stream
     * @see #flatMap(Function)
     */

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return base.flatMapToLong(mapper);
    }

    /**
     * Returns an {@code DoubleStream} consisting of the results of replacing
     * each element of this stream with the contents of a mapped stream produced
     * by applying the provided mapping function to each element.  Each mapped
     * stream is {@link java.util.stream.BaseStream#close() closed} after its
     * contents have placed been into this stream.  (If a mapped stream is
     * {@code null} an empty stream is used, instead.)
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces a stream
     *               of new values
     * @return the new stream
     * @see #flatMap(Function)
     */

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return base.flatMapToDouble(mapper);
    }

    /**
     * Returns a stream consisting of the distinct items present in this stream. Items are compared
     * using {@link XdmItem#equals}. This means that two <code>XdmNode</code> objects
     * are compared by node identity (so two separate nodes are distinct even if they have the same
     * name and the same content).
     * @return the new stream, obtained by applying {@link Stream#distinct()} to the underlying stream
     */
    @Override
    public XdmStream<T> distinct() {
        return new XdmStream<>(base.distinct());
    }

    /**
     * Returns a stream consisting of the elements of this stream, in sorted order.
     * <p>Note, this method is unlikely to be useful, because most <code>XdmItem</code> values do
     * not implement {@link Comparable}.</p>
     * @return the new stream, obtained by applying {@link Stream#sorted()} to the underlying stream
     */
    @Override
    public XdmStream<T> sorted() {
        return new XdmStream<>(base.sorted());
    }

    /**
     * Returns a stream consisting of the elements of this stream, in sorted order using a supplied {@link Comparator}.
     * @return the new stream, obtained by applying {@link Stream#sorted(Comparator)} to the underlying stream
     */
    @Override
    public XdmStream<T> sorted(Comparator<? super T> comparator) {
        return new XdmStream<>(base.sorted(comparator));
    }

    /**
     * Returns the supplied stream, while invoking a supplied action on each element of the stream as it is
     * processed.
     * <p>This method is designed primarily for debugging, to allow the contents of a stream to be monitored.</p>
     * @param action the (non-interfering) action to be performed on each element of the stream
     * @return the supplied stream, unchanged.
     */
    @Override
    public XdmStream<T> peek(Consumer<? super T> action) {
        return new XdmStream<>(base.peek(action));
    }

    /**
     * Returns a stream containing the initial items of this stream, up to a maximum size
     * @param maxSize the maximum number of items to be included in the returned stream
     * @return a stream containing the initial items of this stream, up to the specified limit
     */

    @Override
    public XdmStream<T> limit(long maxSize) {
        return new XdmStream<>(base.limit(maxSize));
    }

    /**
     * Returns a stream containing the items of this stream after skipping a specified
     * number of items.
     * @param n the number of items at the start of the stream to be omitted from the result stream
     * @return the result stream, comprising the input stream except for its first <code>n</code> items.
     */

    @Override
    public XdmStream<T> skip(long n) {
        return new XdmStream<>(base.skip(n));
    }

    /**
     * Performs a given action once for each item of the stream, in non-deterministic order
     * @param action the action to be performed on each item
     */

    @Override
    public void forEach(Consumer<? super T> action) {
        base.forEach(action);
    }

    /**
     * Performs a given action once for each item of the stream, in the order in which the items appear
     * @param action the action to be performed on each item
     */

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        base.forEachOrdered(action);
    }

    /**
     * Returns an array containing the items in this stream
     * @return an array containing all the items in the stream
     */

    @Override
    public Object[] toArray() {
        return base.toArray();
    }

    /**
     * Returns an array containing the items in this stream, using a supplied function
     * to generate the array; this allows the type of the returned array to be controlled.
     * @param generator a function that takes an integer as argument and returns an array
     *                  of the given length
     * @return an array containing all the items in the stream; the type of the array is determined
     * by the generator function
     */

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        return base.toArray(generator);
    }

    /**
     * Performs a reduction or fold operation on the items in the stream.
     * <p>For example, given a sequence of elements of the form {@code <change year="2020" rise="1.03"/>}
     * the accumulated rise over a number of years may be computed as</p>
     * <pre>{@code
     *    changes.stream().select(attribute("rise"))
     *      .map(a->a.getTypedValue().getDoubleValue())
     *      .reduce(1, (x, y) -> x*y)
     * }</pre>
     * @param identity an initial value of an accumulator variable. This must be an identity value
     *                 for the supplied accumulator function (so if F is the accumulator function,
     *                 <code>F(identity, V)</code> returns <code>V</code> for any value of <code>V</code>).
     * @param accumulator the accumulator function: this takes the old value of the accumulator variable
     *                    and the current item from the stream as arguments, and returns a
     *                    new value for the accumulator variable. This function must be associative, that is,
     *                    <code>F(A, F(B, C))</code> must always give the same result as <code>F(F(A, B), C))</code>
     * @return the final value of the accumulator variable after all items have been processed.
     */

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return base.reduce(identity, accumulator);
    }

    /**
     * Calls {@link Stream#reduce(BinaryOperator) on the underlying stream}
     * @param accumulator an associative, stateless, non-interfering value operating
     *                    on two items in the stream
     * @return the result of the delegated call
     */

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        return base.reduce(accumulator);
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        return base.reduce(identity, accumulator, combiner);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        return base.collect(supplier, accumulator, combiner);
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return base.collect(collector);
    }

    /**
     * Returns the minimum item in the stream of items, comparing them using the supplied {@link Comparator}.
     * @param comparator the comparator to be used for comparing items
     * @return the minimum value, or {@link Optional#empty()} if the stream is empty.
     */

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return base.min(comparator);
    }

    /**
     * Returns the maximum item in the stream of items, comparing them using the supplied {@link Comparator}.
     *
     * @param comparator the comparator to be used for comparing items
     * @return the maximum value, or {@link Optional#empty()} if the stream is empty.
     */

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return base.max(comparator);
    }

    /**
     * Returns the number of items in the stream
     * @return the length of the stream
     */
    @Override
    public long count() {
        return base.count();
    }

    /**
     * Returns true if any item in the stream matches a supplied predicate
     * @param predicate the predicate to be applied to each item
     * @return true if an item is found for which the predicate function returns true
     */
    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return base.anyMatch(predicate);
    }

    /**
     * Returns true if every item in the stream matches a supplied predicate
     *
     * @param predicate the predicate to be applied to each item
     * @return true if the predicate function returns true for every item in the stream
     */
    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return base.allMatch(predicate);
    }

    /**
     * Returns true if no item in the stream matches a supplied predicate
     *
     * @param predicate the predicate to be applied to each item
     * @return true if the predicate function returns false for every item in the stream
     */
    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return base.noneMatch(predicate);
    }

    /**
     * Returns the first item in the stream, or {@link Optional#empty()} if the stream is empty
     * @return the first item in the stream
     */

    @Override
    public Optional<T> findFirst() {
        return base.findFirst();
    }

    /**
     * Returns an item in the stream, chosen arbitrarily, or {@link Optional#empty()} if the stream is empty
     *
     * @return an arbitrary item from the stream
     */

    @Override
    public Optional<T> findAny() {
        return base.findAny();
    }

    /**
     * Get an iterator over the items in the stream
     * @return an iterator over all the items, in order
     */

    @Override
    public Iterator<T> iterator() {
        return base.iterator();
    }

    /**
     * Get a Spliterator over the items in the stream
     *
     * @return a Spliterator over all the items, in order
     */

    @Override
    public Spliterator<T> spliterator() {
        return base.spliterator();
    }

    /**
     * Ask whether this stream will be evaluated in parallel
     * @return true if execution is in parallel
     */

    @Override
    public boolean isParallel() {
        return base.isParallel();
    }

    /**
     * Returns an equivalent stream that will be evaluated sequentially
     *
     * @return an equivalent sequential stream
     */

    @Override
    public Stream<T> sequential() {
        return new XdmStream<>(base.sequential());
    }

    /**
     * Returns an equivalent stream that will (where possible and appropriate) be evaluated in parallel
     *
     * @return an equivalent parallel stream
     */

    @Override
    public Stream<T> parallel() {
        return new XdmStream<>(base.parallel());
    }

    /**
     * Returns an equivalent stream that offers no guarantee of retaining the order of items
     * @return an equivalent stream without guaranteed order
     */

    @Override
    public Stream<T> unordered() {
        return new XdmStream<>(base.unordered());
    }

    /**
     * Returns an equivalent stream with a specified handler to be called when the stream is exhausted
     * @param closeHandler the code to be executed when the stream is closed
     * @return a stream that returns the same items, augmented with a close handler
     */

    @Override
    public Stream<T> onClose(Runnable closeHandler) {
        return new XdmStream<>(base.onClose(closeHandler));
    }

    /**
     * Close the stream
     */

    @Override
    public void close() {
        base.close();
    }

    /**
     * Return the contents of the stream as an XdmValue. This is a terminal operation.
     * @return the contents of the stream, as an XdmValue.
     */

    public XdmValue asXdmValue() {
        return base.collect(XdmCollectors.asXdmValue());
    }

    /**
     * Return the contents of the stream as a <code>List&lt;XdmItem&gt;</code>. This is a terminal operation.
     * @return the contents of the stream, as a <code>List&lt;XdmItem&gt;</code>.
     */

    public List<T> asList() {
        return base.collect(Collectors.toList());
    }

    /**
     * Return the contents of the stream as an <code>Iterable&lt;XdmItem&gt;</code>, allowing
     * its use in a for-each loop. This is a terminal operation.
     *
     * @return the contents of the stream, as a <code>Tterable&lt;XdmItem&gt;</code>
     * @since 13.0
     */

    public Iterable<T> asIterable() {
        return new IterableUsingIteratorSupplier<>(this::iterator);
    }

    /**
     * Return the result of the stream as a {@code List<XdmNode>}. This is a terminal operation.
     * <p>Node: the method makes it convenient to process the contents of a stream using a for-each
     * loop, for example:</p>
     * <pre>{@code
     *     for (XdmNode n : start.select(child().where(isText())).asList()) {
     *          process(n)
     *     }
     * }</pre>
     * <p>A more idiomatic style, however, is to use the {@link #forEach} method:</p>
     * <p>{@code start.select(child().where(isText())).forEach(n -> process(n))}</p>
     * @return the list of nodes delivered by the stream
     * @throws ClassCastException    if the stream contains an item that is not a node
     */

    public List<XdmNode> asListOfNodes() {
        return base.collect(XdmCollectors.asListOfNodes());
    }

    /**
     * Return the result of the stream as an <code>Optional&lt;XdmNode&gt;</code>. This is a terminal operation.
     * @return the single node delivered by the stream, or absent if the stream is empty
     * @throws XdmCollectors.MultipleItemException  if the stream contains more than one node
     * @throws ClassCastException if the stream contains an item that is not a node

     */

    public Optional<XdmNode> asOptionalNode() {
        return base.collect(XdmCollectors.asOptionalNode());
    }

    /**
     * Return the result of the stream as an {@link XdmNode}. This is a terminal operation.
     * @return the single node delivered by the stream
     * @throws ClassCastException if the stream contains an item that is not a node
     * @throws XdmCollectors.MultipleItemException if the stream contains
     * more than one item
     * @throws NoSuchElementException if the stream is empty
     */

    public XdmNode asNode() {
        return base.collect(XdmCollectors.asNode());
    }

    /**
     * Return the result of the stream as a <code>List&lt;XdmAtomicValue&gt;</code>. This is a terminal operation.
     *
     * @return the list of atomic values delivered by the stream
     * @throws ClassCastException if the stream contains an item that is not an atomic value
     */

    public List<XdmAtomicValue> asListOfAtomic() {
        return base.collect(XdmCollectors.asListOfAtomic());
    }

    /**
     * Return the result of the stream as an <code>Optional&lt;XdmAtomicValue&gt;</code>. This is a terminal operation.
     *
     * @return the string value of the single item delivered by the stream, or absent if the stream is empty
     * @throws XdmCollectors.MultipleItemException if the stream contains more than one item
     * @throws ClassCastException                  if the stream contains an item that is not an atomic value
     */

    public Optional<XdmAtomicValue> asOptionalAtomic() {
        return base.collect(XdmCollectors.asOptionalAtomic());
    }

    /**
     * Return the result of the stream as an {@link XdmAtomicValue}. This is a terminal operation.
     *
     * @return the string value of the single item delivered by the stream, or a zero-length string
     * if the stream is empty
     * @throws ClassCastException                  if the stream contains an item that is not atomic
     * @throws XdmCollectors.MultipleItemException if the stream contains more than one item
     * @throws NoSuchElementException if the stream is empty
     */

    public XdmAtomicValue asAtomic() {
        return base.collect(XdmCollectors.asAtomic());
    }


    /**
     * Return the result of the stream as an <code>Optional&lt;String&gt;</code>. This is a terminal operation.
     *
     * @return the string value of the single item delivered by the stream, or absent if the stream is empty
     * @throws XdmCollectors.MultipleItemException if the stream contains more than one item
     * @throws UnsupportedOperationException if the stream contains an item that has no string value,
     * for example a function item
     */

    public Optional<String> asOptionalString() {
        return base.collect(XdmCollectors.asOptionalString());
    }

    /**
     * Return the result of the stream as an {@link String}. This is a terminal operation.
     *
     * @return the string value of the single item delivered by the stream
     * @throws UnsupportedOperationException if the stream contains an item that has no string value,
     * for example a function item
     * @throws XdmCollectors.MultipleItemException if the stream contains more than one item
     * @throws NoSuchElementException if the stream is empty
     */

    public String asString() {
        return base.collect(XdmCollectors.asString());
    }

    /**
     * Return the first item of this stream, if there is one, discarding the remainder.
     * This is a short-circuiting operation similar to {@link #findFirst}, but it returns
     * <code>XdmStream&lt;T&gt;</code> rather than <code>Optional&lt;T&gt;</code> so that further operations such
     * as {@code atomize()} can be applied, and so that a typed result can be returned
     * using a method such as {@link #asOptionalNode()} or {@link #asOptionalString()}
     * @return a stream containing the first item in this stream
     */

    public XdmStream<T> first() {
        Optional<T> result = base.findFirst();
        return new XdmStream<>(result);
    }

    /**
     * Return the first item of this stream, if there is one, discarding the remainder;
     * return null if the stream is empty.
     * This is a short-circuiting operation similar to {@link #findFirst}
     * @return the first item in this stream, or null if the stream is empty.
     */

    public T firstItem() {
        return base.findFirst().orElse(null);
    }

    /**
     * Return true if the stream is non-empty.
     * This is a short-circuiting terminal operation.
     * @return true if at least one item is present in the stream.
     */

    public boolean exists() {
        Optional<T> result = base.findFirst();
        return result.isPresent();
    }

    /**
     * Return the last item of this stream, if there is one, discarding the remainder.
     * This is a short-circuiting operation similar to {@link #first}; it returns
     * <code>XdmStream&lt;T&gt;</code> rather than <code>Optional&lt;T&gt;</code> so that further operations such
     * {@code atomize()} can be applied, and so that a typed result can be returned
     * using a method such as {@link #asOptionalNode()} or {@link #asOptionalString()}
     * @return a stream containing only the last item in the stream, or an empty stream
     * if the input is empty.
     */

    public XdmStream<T> last() {
        Optional<T> result = base.reduce((first, second) -> second);
        return new XdmStream<>(result);
    }

    /**
     * Return the last item of this stream, if there is one, discarding the remainder;
     * return null if the stream is empty.
     * This is a short-circuiting operation similar to {@link #lastItem()}.
     *
     * @return the last item in the stream, or null if the input is empty.
     */

    public T lastItem() {
        return base.reduce((first, second) -> second).orElse(null);
    }

    /**
     * Return the item at a given position in the stream. This is a short-circuiting terminal operation.
     * @param position the required position; items in the stream are numbered from zero.
     * @return the item at the given position if there is one; otherwise, <code>Optional.empty()</code>
     */

    public Optional<T> at(int position) {
        return base.skip(position).findFirst();
    }

    /**
     * Return the items at a given range of positions in the stream. For example, subStream(0, 3) returns
     * the first three items in the stream.
     * This is a short-circuiting terminal operation.
     *
     * @param start the position of the first required item; items in the stream are numbered from zero.
     * @param end the position immediately after the last required item.
     * @return a stream containing those items whose zero-based position is greater-than-or-equal-to start, and
     * less-than end. No error occurs if either start or end is out of range, or if end is less than start.
     */

    public XdmStream<T> subStream(int start, int end) {
        if (start < 0) {
            start = 0;
        }
        if (end <= start) {
            return new XdmStream<>(Stream.empty());
        }
        return new XdmStream<>(base.skip(start).limit(end - start));
    }

    /**
     * Experimental method to return the content of a stream up to the first item
     * that satisfies a given predicate, including that item
     * @param predicate a condition that determines when the stream should stop
     * @return a stream containing all items in the base stream up to and including
     * the first item that satisfies a given predicate.
     */

    public XdmStream<T> untilFirstInclusive(Predicate<? super XdmItem> predicate) {
        Stream<T> stoppable = base.peek(item -> {
            if (predicate.test(item)) {
                base.close();
            }
        });
        return new XdmStream<>(stoppable);
    }

    /**
     * Experimental method to return the content of a stream up to the first item
     * that satisfies a given predicate, excluding that item
     *
     * @param predicate a condition that determines when the stream should stop
     * @return a stream containing all items in the base stream up to the item immediately before
     * the first item that satisfies a given predicate.
     */

    public XdmStream<T> untilFirstExclusive(Predicate<? super XdmItem> predicate) {
        Stream<T> stoppable = base.peek(item -> {
            if (predicate.test(item)) {
                base.close();
            }
        });
        return new XdmStream<>(stoppable.filter(predicate.negate()));
    }
    
}


