////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api.streams;

import net.sf.saxon.s9api.*;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * XdmStream extends the capabilities of the standard JDK {@link Stream}.
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
     * that satisfy a supplied condition
     * @param predicate the supplied condition
     * @return the filtered stream
     */

    @Override
    public XdmStream<T> filter(Predicate<? super T> predicate) {
        return new XdmStream<>(base.filter(predicate));
    }

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        return base.map(mapper);
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return base.mapToInt(mapper);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return base.mapToLong(mapper);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return base.mapToDouble(mapper);
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return base.flatMap(mapper);
    }

    /**
     * Create a new {@link XdmStream} by applying a mapping function (specifically, a {@link Step})
     * to each item in the stream. The {@link Step} returns a sequence of items, which are inserted
     * into the result sequence in place of the original item.
     * @param mapper the mapping function
     * @param <U> the type of items returned by the mapping function
     * @return a new stream of items
     */

    public <U extends XdmItem> XdmStream<U> flatMapToXdm(Step<U> mapper) {
        return new XdmStream<>(base.flatMap(mapper));
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return base.flatMapToInt(mapper);
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return base.flatMapToLong(mapper);
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return base.flatMapToDouble(mapper);
    }

    @Override
    public XdmStream<T> distinct() {
        return new XdmStream<>(base.distinct());
    }

    @Override
    public XdmStream<T> sorted() {
        return new XdmStream<>(base.sorted());
    }

    @Override
    public XdmStream<T> sorted(Comparator<? super T> comparator) {
        return new XdmStream<>(base.sorted(comparator));
    }

    @Override
    public XdmStream<T> peek(Consumer<? super T> action) {
        return new XdmStream<>(base.peek(action));
    }

    @Override
    public XdmStream<T> limit(long maxSize) {
        return new XdmStream<>(base.limit(maxSize));
    }

    @Override
    public XdmStream<T> skip(long n) {
        return new XdmStream<>(base.skip(n));
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        base.forEach(action);
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        base.forEachOrdered(action);
    }

    @Override
    public Object[] toArray() {
        return base.toArray();
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        return base.toArray(generator);
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return base.reduce(identity, accumulator);
    }

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

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return base.min(comparator);
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return base.max(comparator);
    }

    @Override
    public long count() {
        return base.count();
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return base.anyMatch(predicate);
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return base.allMatch(predicate);
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return base.noneMatch(predicate);
    }

    @Override
    public Optional<T> findFirst() {
        return base.findFirst();
    }

    @Override
    public Optional<T> findAny() {
        return base.findAny();
    }

    @Override
    public Iterator<T> iterator() {
        return base.iterator();
    }

    @Override
    public Spliterator<T> spliterator() {
        return base.spliterator();
    }

    @Override
    public boolean isParallel() {
        return base.isParallel();
    }

    @Override
    public Stream<T> sequential() {
        return new XdmStream<>(base.sequential());
    }

    @Override
    public Stream<T> parallel() {
        return new XdmStream<>(base.parallel());
    }

    @Override
    public Stream<T> unordered() {
        return new XdmStream<>(base.unordered());
    }

    @Override
    public Stream<T> onClose(Runnable closeHandler) {
        return new XdmStream<>(base.onClose(closeHandler));
    }

    @Override
    public void close() {
        base.close();
    }

    /**
     * Return the result of the stream as an XdmValue. This is a terminal operation.
     * @return the contents of the stream, as an XdmValue.
     */

    public XdmValue asXdmValue() {
        return base.collect(XdmCollectors.asXdmValue());
    }

    /**
     * Return the result of the stream as a <code>List&lt;XdmItem&gt;</code>. This is a terminal operation.
     * @return the contents of the stream, as a <code>List&lt;XdmItem&gt;</code>.
     */

    public List<T> asList() {
        return base.collect(Collectors.toList());
    }

    /**
     * Return the result of the stream as a <code>List&lt;XdmNode&gt;</code>. This is a terminal operation.
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
     */

    public XdmStream<T> first() {
        Optional<T> result = base.findFirst();
        return new XdmStream<>(result);
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


