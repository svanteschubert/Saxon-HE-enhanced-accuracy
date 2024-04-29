////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpReplaceBody;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An integer that can be incremented atomically with thread safety
 */

@CSharpInjectMembers(code="private long counter = 0;")
public class AtomicCounter {

    // Note, this class is extracted into a separate module to allow different Java and C# implementations

    private AtomicLong counter;


    public AtomicCounter(int initialValue) {
        init(initialValue);
    }

    @CSharpReplaceBody(code = "counter = initialValue;")
    private void init(int initialValue) {
        counter = new AtomicLong(initialValue);
    }

    @CSharpReplaceBody(code = "return System.Threading.Interlocked.Increment(ref counter);")
    public long getAndIncrement() {
        return counter.getAndIncrement();
    }

    public long incrementAndGet() {
        return getAndIncrement() + 1L;
    }

    @CSharpReplaceBody(code="return counter;")
    public long get() {
        return counter.get();
    }

}

