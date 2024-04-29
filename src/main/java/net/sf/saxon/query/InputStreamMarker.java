////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpReplaceBody;

import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * Allows the position of an input stream to be marked and reset.
 * This class provides an abstraction that can be mapped to the different
 * ways of doing this on Java and .NET
 */

@CSharpInjectMembers(code="private long markerPosition;")
public class InputStreamMarker {

    private final InputStream stream;

    /**
     * Create an <code>InputStreamMarker</code> for a supplied input stream
     * @param stream  the supplied input stream
     */
    public InputStreamMarker(InputStream stream) {
        this.stream = stream;
    }

    /**
     * Return this stream if its position can be marked, or an enclosing Stream if not
     * @param stream the supplied stream
     * @return either this stream if it is markable, or an equivalent markable stream if not
     */
    public static InputStream ensureMarkSupported(InputStream stream) {
        if (isMarkSupported(stream)) {
            return stream;
        } else {
            return new BufferedInputStream(stream);
        }
    }

    @CSharpReplaceBody(code = "return stream.CanSeek;")
    private static boolean isMarkSupported(InputStream stream) {
        return stream.markSupported();
    }

    @CSharpReplaceBody(code="return stream.CanSeek;")
    public boolean isMarkSupported() {
        return stream.markSupported();
    }

    @CSharpReplaceBody(code = "markerPosition = stream.Position;")
    public void mark(int readLimit) {
        stream.mark(readLimit);
    }

    @CSharpReplaceBody(code = "stream.Position = markerPosition;")
    public void reset() throws java.io.IOException {
        stream.reset();
    }
}

