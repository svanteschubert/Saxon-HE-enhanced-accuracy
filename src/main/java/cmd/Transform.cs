using System;

namespace Saxon.Cmd
{

    /**
     * This class provides the command line interface for the .NET executable
     */

    public class DotNetTransform : net.sf.saxon.Transform
    {

        // This class has no static dependency on the Saxon-SA DLL. If schema-aware
        // processing is required, the DLL is loaded dynamically. This has changed
        // with IKVM 0.32.0.0, it is now done by calling Class.forName() using the
        // asssembly-qualified name of the class as the class name.

        //private static string saxonSaClassName =
        //    DotNetPlatform.getSaxonSaFullyQualifiedClassName();


        /**
         * Private constructor, ensuring the class can only be used via its "Main" method.
         */

        private DotNetTransform()
        {
            // Ensure the extended character sets in charsets.jar are loaded
            GC.KeepAlive(typeof(sun.nio.cs.ext.ExtendedCharsets));
        }

        /**
         * Set the configuration in the TransformerFactory. This is designed to be
         * overridden in a subclass
         * @param schemaAware
         */

        public override void setFactoryConfiguration(bool schemaAware, String className)
        {
            base.setFactoryConfiguration(schemaAware, null);
        }

        /**
         * Entry point for use from the .NET command line
         * @param args command line arguments
         * @throws java.lang.Exception
         */

        public static void Main(String[] args)
        {
            new DotNetTransform().doTransform(args, "Transform");
        }

    }
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//