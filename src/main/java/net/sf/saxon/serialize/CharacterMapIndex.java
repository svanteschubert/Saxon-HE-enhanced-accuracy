////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

import java.util.*;

/**
 * This class represents a set of named character maps. Each character map in the set is identified by a unique
 * QName.
 */

public class CharacterMapIndex implements Iterable<CharacterMap> {

    private HashMap<StructuredQName, CharacterMap> index = new HashMap<>(10);

    public CharacterMapIndex() {
    }

    /**
     * Get the character map with a given name
     * @param name the name of the required character map
     * @return the requested character map if present, or null otherwise
     */

    public CharacterMap getCharacterMap(StructuredQName name) {
        return index.get(name);
    }

    /**
     * Add a character map with a given name
     * @param name the name of the character map
     * @param charMap the character map to be added
     */

    public void putCharacterMap(StructuredQName name, CharacterMap charMap) {
        index.put(name, charMap);
    }

    /**
     * Get an iterator over all the character maps in the index
     * @return an iterator over the character maps
     */

    @Override
    public Iterator<CharacterMap> iterator() {
        return index.values().iterator();
    }

    /**
     * Ask if the character map index is empty
     * @return true if the character map index contains no character maps
     */

    public boolean isEmpty() {
        return index.isEmpty();
    }

    /**
     * Copy this character map index
     * @return a new character map index with the same contents. The character maps themselves are
     * immutable and do not need to be copied
     */

    public CharacterMapIndex copy() {
        CharacterMapIndex copy = new CharacterMapIndex();
        copy.index = new HashMap<>(this.index);
        return copy;
    }

    /**
     * Make a CharacterMapExpander to handle the character map definitions in the serialization
     * properties.
     * <p>This method is intended for internal use only.</p>
     *
     * @param useMaps the expanded use-character-maps property: a space-separated list of names
     *                of character maps to be used, each one expressed as an expanded-QName in Clark notation
     *                (that is, {uri}local-name).
     * @param next    the next receiver in the pipeline
     * @param sf      the SerializerFactory - used to create a CharacterMapExpander. This callback
     *                is provided so that a user-defined SerializerFactory can customize the result of this function,
     *                for example by returning a subclass of the standard CharacterMapExpander.
     * @return a CharacterMapExpander if one is required, or null if not (for example, if the
     *         useMaps argument is an empty string).
     * @throws net.sf.saxon.trans.XPathException
     *          if a name in the useMaps property cannot be resolved to a declared
     *          character map.
     */

    public CharacterMapExpander makeCharacterMapExpander(
            String useMaps, Receiver next, SerializerFactory sf) throws XPathException {
        CharacterMapExpander characterMapExpander = null;
        List<CharacterMap> characterMaps = new ArrayList<CharacterMap>(5);
        StringTokenizer st = new StringTokenizer(useMaps, " \t\n\r", false);
        while (st.hasMoreTokens()) {
            String expandedName = st.nextToken();
            StructuredQName qName = StructuredQName.fromClarkName(expandedName);
            CharacterMap map = getCharacterMap(qName);
            if (map == null) {
                throw new XPathException("Character map '" + expandedName + "' has not been defined", "SEPM0016");
            }
            characterMaps.add(map);
        }
        if (!characterMaps.isEmpty()) {
            characterMapExpander = sf.newCharacterMapExpander(next);
            if (characterMaps.size() == 1) {
                characterMapExpander.setCharacterMap(characterMaps.get(0));
            } else {
                characterMapExpander.setCharacterMap(new CharacterMap(characterMaps));
            }
        }
        return characterMapExpander;
    }

}

