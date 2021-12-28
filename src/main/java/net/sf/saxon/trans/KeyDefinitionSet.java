////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.om.StructuredQName;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of xsl:key definitions in a stylesheet that share the same name
 */

public class KeyDefinitionSet {

    private StructuredQName keyName;
    private int keySetNumber;               // unique among the KeyDefinitionSets within a KeyManager
    private List<KeyDefinition> keyDefinitions;
    private String collationName;
    private boolean composite;
    private boolean backwardsCompatible;    // true if any of the keys is backwards compatible
    private boolean rangeKey;               // true if any of the keys is a range key
    private boolean reusable = true;        // true if indexes built for this key can be reused across transformations

    /**
     * Create a key definition set for keys sharing a given name
     *
     * @param keyName      the name of the key definitions in this set
     * @param keySetNumber a unique number identifying this key definition set
     */

    public KeyDefinitionSet(StructuredQName keyName, int keySetNumber) {
        this.keyName = keyName;
        this.keySetNumber = keySetNumber;
        keyDefinitions = new ArrayList<>(3);
    }

    /**
     * Add a key definition to this set of key definitions. The caller is responsible for ensuring that
     * all key definitions in a key definition set have the same name
     *
     * @param keyDef the key definition to be added
     * @throws XPathException if the key definition uses a different collation from others in the set
     */

    public void addKeyDefinition(/*@NotNull*/ KeyDefinition keyDef) throws XPathException {
        if (keyDefinitions.isEmpty()) {
            collationName = keyDef.getCollationName();
            composite = keyDef.isComposite();
        } else {
            if ((collationName == null && keyDef.getCollationName() != null) ||
                    (collationName != null && !collationName.equals(keyDef.getCollationName()))) {
                throw new XPathException("All keys with the same name must use the same collation", "XTSE1220");
            }
            if (keyDef.isComposite() != composite) {
                throw new XPathException("All keys with the same name must have the same value for @composite", "XTSE1222");
            }
            // ignore this key definition if it is a duplicate of another already present. This can happen when including
            // a stylesheet module more than once
            List<KeyDefinition> v = getKeyDefinitions();
            for (KeyDefinition other : v) {
                if (keyDef.getMatch().isEqual(other.getMatch()) &&
                        keyDef.getBody().isEqual(other.getBody())) {
                    return;
                }
            }
        }
        if (keyDef.isBackwardsCompatible()) {
            backwardsCompatible = true;
        }
        if (keyDef.isRangeKey()) {
            rangeKey = true;
        }
        keyDefinitions.add(keyDef);
    }

    /**
     * Get the name of the key definitions in this set (they all share the same name)
     *
     * @return the name of these key definitions
     */

    public StructuredQName getKeyName() {
        return keyName;
    }

    /**
     * Get the name of the collation used for this key
     *
     * @return the collation name (a URI)
     */

    public String getCollationName() {
        return collationName;
    }

    /**
     * Ask whether this is a composite key
     * @return true if the key is composite
     */

    public boolean isComposite() {
        return composite;
    }

    /**
     * Get the KeySet number. This uniquely identifies the KeyDefinitionSet within a KeyManager
     *
     * @return the unique number
     */

    public int getKeySetNumber() {
        return keySetNumber;
    }

    /**
     * Get the key definitions in this set
     *
     * @return the key definitions in this set
     */

    public List<KeyDefinition> getKeyDefinitions() {
        return keyDefinitions;
    }

    /**
     * Ask if the keys are to be evaluated in backwards compatible mode
     *
     * @return true if backwards compatibility is in force for at least one of the keys in the set
     */

    public boolean isBackwardsCompatible() {
        return backwardsCompatible;
    }

    /**
     * Ask if this is a range key
     *
     * @return true if any of the keys in the set is defined as a range key
     */

    public boolean isRangeKey() {
        return rangeKey;
    }

    /**
     * Say whether this key definition set is reusable across transformations, that is, if
     * the index built for a particular document is shareable across transformations. This will only
     * be the case if the key has no dependencies on global variables or parameters in the stylesheet
     * @param reusable set to true if the index can safely be shared and reused
     */

    public void setReusable(boolean reusable) {
        this.reusable = reusable;
    }

    /**
     * Ask whether this key definition set is reusable across transformations, that is, if
     * the index built for a particular document is shareable across transformations. This will only
     * be the case if the key has no dependencies on global variables or parameters in the stylesheet
     * @return true if the index can safely be shared and reused
     */

    public boolean isReusable() {
        return reusable;
    }
}

