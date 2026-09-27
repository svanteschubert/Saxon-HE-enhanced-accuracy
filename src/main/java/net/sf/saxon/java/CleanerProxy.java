////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.java;

import net.sf.saxon.Configuration;

import java.lang.ref.Cleaner;

/**
 * This utility class exists in order to put all functionality relating to use of the Java 9 class
 * {@link java.lang.ref.Cleaner} in one place, to allow the product to continue to work with Java 8.
 * If no Cleaner is available (that is, if we're running under Java 8), everything should run fine
 * except that under rare conditions, resources will not be properly closed. This happens, for example,
 * if a collection() is bound to a lazily-evaluated variable, and the contents of the collection()
 * are not read to completion.
 *
 */

public class CleanerProxy {

    
    private final Cleaner cleaner;

    private CleanerProxy(Cleaner cleaner) {
        this.cleaner = cleaner;
    }

    public static CleanerProxy makeCleanerProxy(Configuration config) {
        try {
            Class.forName("java.lang.ref.Cleaner", true, config.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return new CleanerProxy(null);
        }
        Cleaner c = null;
        try {
            c = Cleaner.create();
        } catch (Exception e) {
            System.err.println("Warning: no Cleaner available (this is expected on Java 8)");
        }
        return new CleanerProxy(c);
    }

    public CleanableProxy registerCleanupAction(Object obj, Runnable runnable) {
        if (cleaner != null) {
            Cleaner.Cleanable cleanable = cleaner.register(obj, runnable);
            return new CleanableProxy(cleanable);
        } else {
            return null;
        }
    }


    public static class CleanableProxy {
        private final Cleaner.Cleanable cleanable;

        public CleanableProxy(Cleaner.Cleanable cleanable) {
            this.cleanable = cleanable;
        }

        public void clean() {
            cleanable.clean();
        }
    }
}
