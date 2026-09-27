// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.trans.UncheckedXPathException;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A wrapper around the Java cryptography classes to make a SHA-256 digest.
 * <p>This exists so that a common API can be implemented in Java and C#.</p>
 */
public class DigestMaker {
    private String hexDigest = null;
    private final MessageDigest digest;

    public DigestMaker() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            // never happens
            throw new UncheckedXPathException(ex);
        }
    }

    public void update(int value) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
    }

    public void update(String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    public String getDigest() {
        // You can only call .digest() once
        if (hexDigest == null) {
            // Careful, don't drop leading 0's on the floor...
            hexDigest = String.format("%064x", new BigInteger(1, digest.digest()));
        }
        return hexDigest;
    }
}
