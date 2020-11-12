////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api.streams;

import net.sf.saxon.s9api.XdmItem;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A {@code Step} is a function that can be applied to an item
 * to return a stream of items.
 * @param <T> the type of {@link XdmItem} that is returned by the step. (Note that we
 *           don't parameterize Steps by the type of input item).
 * @see Steps
 */

public abstract class Step<T extends XdmItem>
        implements Function<XdmItem, Stream<? extends T>> {

    /**
     * Obtain a {@link Step} that filters the results of this Step using a supplied predicate.
     * <p>For example, {@code CHILD.where(isText())} returns a Step whose effect is
     * to select the text node children of a supplied element or document node.</p>
     * @param predicate the predicate which will be applied to the results of this step
     * @return a new Step (that is, a function from one Stream of items to another) that
     * filters the results of this step by selecting only the items that satisfy the predicate.
     */

    public Step<T> where(Predicate<? super T> predicate) {
        Step<T> base = this;
        return new Step<T>() {
            @Override
            public Stream<? extends T> apply(XdmItem item) {
                return base.apply(item).filter(predicate);
            }
        };
    }

    /**
     * Obtain a {@link Step} that concatenates the results of this Step with the result of another
     * Step applied to the same input item.
     * <p>For example, {@code attribute().cat(child())} returns a step whose effect is
     * to select the attributes of a supplied element followed by its children.</p>
     *
     * @param other the step whose results will be concatenated with the results of this step
     * @return a new Step (that is, a function from one Stream of items to another) that
     * concatenates the results of applying this step to the input item, followed by the
     * results of applying the other step to the input item.
     */

    public Step<T> cat(Step<T> other) {
        Step<T> base = this;
        return new Step<T>() {
            @Override
            public Stream<T> apply(XdmItem item) {
                return Stream.concat(base.apply(item), other.apply(item));
            }
        };
    }

    /**
     * Obtain a step that selects the first item in the results of this step
     *
     * @return a new Step (that is, a function from one Stream of items to another) that
     * filters the results of this step by selecting only the first item.
     */

    public Step<T> first() {
        final Step<T> base = this;
        return new Step<T>() {
            @Override
            public Stream<? extends T> apply(XdmItem item) {
                return base.apply(item).limit(1);
            }
        };
    }

    /**
     * Obtain a step that selects the last item in the results of this step
     *
     * @return a new Step (that is, a function from one Stream of items to another) that
     * filters the results of this step by selecting only the last item.
     */

    public Step<T> last() {
        final Step<T> base = this;
        return new Step<T>() {
            @Override
            public Stream<? extends T> apply(XdmItem item) {
                return base.apply(item).reduce((first, second) -> second)
                        .map(Stream::of).orElseGet(Stream::empty);
            }
        };
    }

    /**
     * Obtain a step that selects the Nth item in the results of this step
     *
     * @param index the zero-based index of the item to be selected
     * @return a new Step (that is, a function from one Stream of items to another) that
     * filters the results of this step by selecting only the items that satisfy the predicate.
     */

    public Step<T> at(long index) {
        final Step<T> base = this;
        return new Step<T>() {
            @Override
            public Stream<? extends T> apply(XdmItem item) {
                return base.apply(item).skip(index).limit(1);
            }
        };
    }


    /**
     * Obtain a step that combines the results of this step with the results of another step
     *
     * @param next the step which will be applied to the results of this step
     * @return a new Step (that is, a function from one Stream of items to another) that
     * performs this step and the next step in turn. The result is equivalent to the Java {code flatMap()}
     * function or the XPath {code !} operator: there is no sorting of nodes into document order, and
     * no elimination of duplicates.
     */

    public <U extends XdmItem> Step<U> then(Step<U> next) {
        Step<T> me = this;
        return new Step<U>() {
            @Override
            public Stream<? extends U> apply(XdmItem item) {
                return me.apply(item).flatMap(next);
            }
        };
    }
}

