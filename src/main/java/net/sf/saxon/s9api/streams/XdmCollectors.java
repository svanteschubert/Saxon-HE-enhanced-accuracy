////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.s9api.streams;

import net.sf.saxon.s9api.*;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * This class contains a number of static methods that deliver implementations of the {@link java.util.stream.Collector}
 * interface suitable for use with streams processing XDM nodes and other items.
 * <p>For example, the method {@code asNode} can be used in an expression such as
 * {@code XdmNode n = x.select(child("author")).collect(asNode())} to indicate that the content of the stream
 * delivered by the {@code select()} call is to be delivered as a single XdmNode object, and that an exception
 * should occur if the result is anything other than a single node.</p>
 * <p>Although these methods can be used directly as arguments to {@link Stream#collect}, it is usually more convenient
 * to use them indirectly, the form of terminal operations on the class {@link XdmStream}, which extends {@link Stream}.
 * So a more usual usage would be {@code XdmNode n = x.select(child("author")).asNode()}</p>
 */

public class XdmCollectors {

    /**
     * Unchecked exception that occurs when a collector method such as {@link #asAtomic} or {@link #asOptionalNode}
     * is called, and the sequence contains more than one item.
     */
    public static class MultipleItemException extends RuntimeException {}

    /**
     * Abstract superclass for collectors of XDM items
     * @param <R> the type of the result of the collector, for example {@code XdmNode} or <code>Optional&lt;XdmNode&gt;</code>
     * @param <I> the type of the items in the stream, for example {@code XdmNode} or {@code XdmAtomicValue}
     */

    private abstract static class XdmCollector<R, I extends XdmItem> implements Collector<XdmItem, List<I>, R> {

        protected void onEmpty() {
        }

        protected void onMultiple() {
        }

        protected I convert(XdmItem item) {
            //noinspection unchecked
            return (I)item;
        }

        protected abstract R makeResult(List<I> list);

        @Override
        public Supplier<List<I>> supplier() {
            return ArrayList::new;
        }

        @Override
        public BiConsumer<List<I>, XdmItem> accumulator() {
            return (list, next) -> {
                I item = convert(next);
                if (!list.isEmpty()) {
                    onMultiple();
                }
                list.add(item);
            };
        }

        @Override
        public BinaryOperator<List<I>> combiner() {
            return (list1, list2) -> {
                list1.addAll(list2);
                if (list1.size() > 1) {
                    onMultiple();
                }
                return list1;
            };
        }

        @Override
        public Function<List<I>, R> finisher() {
            return list -> {
                if (list.isEmpty()) {
                    onEmpty();
                }
                return makeResult(list);
            };
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }

    /**
     * This method provides a Collector that returns the content of a stream as an {@link XdmValue}
     * @return a collector that returns the single node delivered by the stream, or null if the stream is empty
     * @throws ClassCastException if the stream contains an item that is not a node
     */

    public static XdmCollector<XdmValue, XdmItem> asXdmValue() {
        return new XdmCollector<XdmValue, XdmItem>() {
            @Override
            protected XdmValue makeResult(List<XdmItem> list) {
                return new XdmValue(list);
            }
        };
    }

    /**
     * This method provides a Collector that returns the content of a stream as a single {@link XdmNode}.
     * @return a collector that returns the single node delivered by the stream
     * @throws NoSuchElementException if the stream is empty
     * @throws MultipleItemException if the stream contains more than one node
     * @throws ClassCastException if the stream contains an item that is not a node
     */

    public static XdmCollector<XdmNode, XdmNode> asNode() {
        return new XdmCollector<XdmNode, XdmNode>() {
            @Override
            protected void onEmpty() {
                throw new NoSuchElementException();
            }

            @Override
            protected void onMultiple() {
                throw new MultipleItemException();
            }

            @Override
            protected XdmNode convert(XdmItem item) {
                return (XdmNode)item; // Deliberate ClassCastException if not a node
            }

            @Override
            protected XdmNode makeResult(List<XdmNode> list) {
                return list.get(0);
            }
        };
    }

    /**
     * This method provides a Collector that returns the content of a stream as an optional {@link XdmNode}
     * (that is, as an instance of <code>Optional&lt;XdmNode&gt;</code>)
     *
     * @return a collector that returns the single node delivered by the stream, or
     * {@code Optional#empty()} if the stream is empty
     * @throws MultipleItemException  if the stream contains more than one node
     * @throws ClassCastException     if the stream contains an item that is not a node
     */

    public static XdmCollector<Optional<XdmNode>, XdmNode> asOptionalNode() {
        return new XdmCollector<Optional<XdmNode>, XdmNode>() {
            @Override
            protected void onEmpty() {
            }

            @Override
            protected void onMultiple() {
                throw new MultipleItemException();
            }

            @Override
            protected XdmNode convert(XdmItem item) {
                return (XdmNode) item; // Deliberate ClassCastException if not a node
            }

            @Override
            protected Optional<XdmNode> makeResult(List<XdmNode> list) {
                return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
            }
        };
    }

    /**
     * This method provides a Collector that returns the content of a stream as a list of {@link XdmNode} objects
     * (that is, as an instance of <code>List&lt;XdmNode&gt;</code>)
     *
     * @return a collector that returns the single node delivered by the stream, or null if the stream is empty
     * @throws ClassCastException    if the stream contains an item that is not a node
     */

    public static XdmCollector<List<XdmNode>, XdmNode> asListOfNodes() {
        return new XdmCollector<List<XdmNode>, XdmNode>() {
            @Override
            protected void onEmpty() {
            }

            @Override
            protected void onMultiple() {
            }

            @Override
            protected XdmNode convert(XdmItem item) {
                return (XdmNode) item; // Deliberate ClassCastException if not a node
            }

            @Override
            protected List<XdmNode> makeResult(List<XdmNode> list) {
                return list;
            }
        };
    }

    /**
     * This method provides a Collector that returns the content of a stream as a list of atomic values
     * (that is, as an instance of <code>List&lt;XdmAtomicValue&gt;</code>)
     *
     * @return a collector that returns the list of atomic values delivered by the stream
     * @throws ClassCastException if the stream contains an item that is not an atomic value
     */

    public static XdmCollector<List<XdmAtomicValue>, XdmAtomicValue> asListOfAtomic() {
        return new XdmCollector<List<XdmAtomicValue>, XdmAtomicValue>() {
            @Override
            protected void onEmpty() {
            }

            @Override
            protected void onMultiple() {
            }

            @Override
            protected XdmAtomicValue convert(XdmItem item) {
                return (XdmAtomicValue) item; // Deliberate ClassCastException if not an atomic value
            }

            @Override
            protected List<XdmAtomicValue> makeResult(List<XdmAtomicValue> list) {
                return list;
            }
        };
    }

    /**
     * This method provides a Collector that returns the content of a stream as an optional atomic value
     * (that is, as an instance of <code>Optional&lt;XdmAtomicValue&gt;</code>)
     *
     * @return a collector that returns the single atomic value delivered by the stream, or
     * {@link Optional#empty()} if the stream is empty
     * @throws ClassCastException if the stream contains an item that is not a node
     */

    public static XdmCollector<Optional<XdmAtomicValue>, XdmAtomicValue> asOptionalAtomic() {
        return new XdmCollector<Optional<XdmAtomicValue>, XdmAtomicValue>() {
            @Override
            protected void onEmpty() {
            }

            @Override
            protected void onMultiple() {
                throw new MultipleItemException();
            }

            @Override
            protected XdmAtomicValue convert(XdmItem item) {
                return (XdmAtomicValue) item; // Deliberate ClassCastException if not an atomic value
            }

            @Override
            protected Optional<XdmAtomicValue> makeResult(List<XdmAtomicValue> list) {
                return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
            }
        };
    }


    /**
     * This method provides a Collector that returns the content of a stream as a single atomic
     * value, that is, an instance of {@link XdmAtomicValue}.
     * The stream must deliver a single atomic value.
     *
     * @return a collector that returns the string value of the single item delivered by the stream
     * @throws NoSuchElementException if the stream is empty
     * @throws MultipleItemException  if the stream contains more than one item
     * @throws ClassCastException if the stream delivers an item that is not an atomic value
     */

    public static XdmCollector<XdmAtomicValue, XdmAtomicValue> asAtomic() {
        return new XdmCollector<XdmAtomicValue, XdmAtomicValue>() {
            @Override
            protected void onEmpty() {
                throw new NoSuchElementException();
            }

            @Override
            protected void onMultiple() {
                throw new MultipleItemException();
            }

            @Override
            protected XdmAtomicValue convert(XdmItem item) {
                return (XdmAtomicValue) item; // Deliberate ClassCastException if not an atomic value
            }

            @Override
            protected XdmAtomicValue makeResult(List<XdmAtomicValue> list) {
                return list.get(0);
            }
        };
    }

    /**
     * This method provides a Collector that returns the content of a stream as an optional String
     * (that is, as an instance of <code>Optional&lt;String&gt;</code>)
     * The stream must deliver either nothing, or a single {@code XdmItem}; the collector returns
     * the string value of that item.
     *
     * @return a collector that returns the string value of the single item delivered by the stream, if any
     * @throws MultipleItemException  if the stream contains more than one item
     * @throws UnsupportedOperationException  if the stream contains an item with no string value (such
     * as a function item or an element with element-only content)
     */

    public static XdmCollector<Optional<String>, XdmItem> asOptionalString() {
        return new XdmCollector<Optional<String>, XdmItem>() {

            @Override
            protected void onMultiple() {
                throw new MultipleItemException();
            }

            @Override
            protected Optional<String> makeResult(List<XdmItem> list) {
                return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0).getStringValue());
            }
        };
    }

    /**
     * This method provides a Collector that returns the content of a stream as an optional String
     * (that is, as an instance of <code>Optional&lt;String&gt;</code>)
     * The stream must deliver a single {@code XdmItem}; the collector returns the string value
     * of that item.
     *
     * @return a collector that returns the string value of the single item delivered by the stream, if any
     * @throws MultipleItemException if the stream contains more than one item
     * @throws NoSuchElementException  if the stream is empty
     * @throws UnsupportedOperationException  if the stream contains an item with no string value (such
     *      as a function item or an element with element-only content)
     */

    public static XdmCollector<String, XdmItem> asString() {
        return new XdmCollector<String, XdmItem>() {

            @Override
            protected void onEmpty() {
                throw new NoSuchElementException();
            }

            @Override
            protected void onMultiple() {
                throw new MultipleItemException();
            }

            @Override
            protected String makeResult(List<XdmItem> list) {
                return list.get(0).getStringValue();
            }
        };
    }

}

