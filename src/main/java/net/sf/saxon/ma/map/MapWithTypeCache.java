// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.type.Affinity;
import net.sf.saxon.type.PlainType;
import net.sf.saxon.type.Subsumption;
import net.sf.saxon.value.SequenceType;

public abstract class MapWithTypeCache extends MapItem {

    protected PlainType knownKeyType;
    protected SequenceType knownValueType;

    protected void setKnownType(PlainType keyType, SequenceType valueType) {
        this.knownKeyType = keyType;
        this.knownValueType = valueType;
    }

    /**
     * Ask whether the map conforms to a given map type. This implementation caches the given type to
     * avoid a complete scan of the map entries in cases where the type has already been established;
     * this also involves passing on available type information when new maps are created using put()
     * and remove() (which is done in subclasses).
     *
     * @param keyType   the required keyType
     * @param valueType the required valueType
     * @return true if the map conforms to the required type
     */
    public boolean conforms(PlainType keyType, SequenceType valueType) {

        if (knownKeyType != null) {
            Affinity affinityKeyRelationship = Subsumption.computeRelationship(keyType, knownKeyType);
            boolean ok = affinityKeyRelationship == Affinity.SAME_TYPE || affinityKeyRelationship == Affinity.SUBSUMED_BY;
            if (ok) {
                Affinity valueTypeRelationship = Subsumption.sequenceTypeRelationship(valueType, knownValueType);
                ok = valueTypeRelationship == Affinity.SAME_TYPE || valueTypeRelationship == Affinity.SUBSUMED_BY;
            }

            if (ok) {
                return true;
            }

        }
        for (KeyValuePair pair : keyValuePairs()) {
            if (!keyType.matches(pair.key())) {
                return false;
            }
            if (!valueType.matches(pair.value())) {
                return false;
            }
        }
        knownKeyType = keyType;
        knownValueType = valueType;
        return true;

        // TODO: the logic could be improved here. If the key type matches and the value type doesn't, then we don't need
        // to test all the keys; and vice versa. Also, if we find that the value conforms to a subtype of the known type,
        // we could record a new known type.
    }

}
