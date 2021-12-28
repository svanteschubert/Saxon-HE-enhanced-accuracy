////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.StructuredQName;

/**
 * This class represents the type of an external object returned by
 * an extension function, or supplied as an external variable/parameter.
 */
public abstract class ExternalObjectType extends AnyExternalObjectType {

    /**
     * Get the name of this type.
     *
     * @return the fully qualified name of the Java or .NET class.
     */

    public abstract String getName();

    /**
     * Get the target namespace of this type. For Java this is always {@link net.sf.saxon.lib.NamespaceConstant#JAVA_TYPE}.
     * For .net it is always {@link net.sf.saxon.lib.NamespaceConstant#DOT_NET_TYPE}
     *
     * @return the target namespace of this type definition.
     */

    public abstract String getTargetNamespace();

    /**
     * Get the name of this type
     * @return a name whose namespace indicates the space of Java or .net classes, and whose local name
     * is derived from the fully qualified name of the Java or .net class
     */

    public abstract StructuredQName getTypeName();

    /**
     * Ask whether this is a plain type (a type whose instances are always atomic values)
     * @return false. External object types are not considered to be atomic types
     */

    @Override
    public final boolean isPlainType() {
        return false;
    }


}
