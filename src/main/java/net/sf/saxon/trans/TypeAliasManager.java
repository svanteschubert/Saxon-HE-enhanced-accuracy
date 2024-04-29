////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.style.XSLItemType;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.type.ItemType;

import java.util.*;

/**
 * Manager for xsl:item-type declarations in a stylesheet.
 *
 * Language extension proposed for XSLT 4.0
 */
public class TypeAliasManager {

    public TypeAliasManager() {
    }


    // map of type aliases (Saxon extension)
    private final Map<StructuredQName, ComponentDeclaration> unresolvedDeclarations =
            new HashMap<>();

    private final Map<StructuredQName, ItemType> typeAliases = new HashMap<>();

    public void registerTypeAlias(StructuredQName name, ItemType type) {
        typeAliases.put(name, type);
        unresolvedDeclarations.remove(name);
    }

    public void processDeclaration(ComponentDeclaration declaration) throws XPathException {
        XSLItemType sta = (XSLItemType) declaration.getSourceElement();
        ItemType type = sta.tryToResolve();
        if (type != null) {
            registerTypeAlias(sta.getObjectName(), type);
        } else {
            unresolvedDeclarations.put(sta.getObjectName(), declaration);
        }
    }

    public void processAllDeclarations(List<ComponentDeclaration> topLevel) throws XPathException {
        for (ComponentDeclaration decl : topLevel) {
            if (decl.getSourceElement() instanceof XSLItemType) {
                processDeclaration(decl);
            }
        }
        int unresolved = unresolvedDeclarations.size();
        while (unresolved > 0) {
            Set<ComponentDeclaration> pending = new HashSet<>(unresolvedDeclarations.values());
            for (ComponentDeclaration decl : pending) {
                processDeclaration(decl);
            }
            if (unresolvedDeclarations.size() >= unresolved) {
                StringBuilder fsb = new StringBuilder(256);
                fsb.append("Cannot resolve all type aliases, because of missing or circular definitions. Unresolved names: ");
                for (StructuredQName name : unresolvedDeclarations.keySet()) {
                    fsb.append(name.getDisplayName());
                    fsb.append(' ');
                }
                throw new XPathException(fsb.toString(), SaxonErrorCode.SXTA0001);
            }
            unresolved = unresolvedDeclarations.size();
        }
    }

    public ItemType getItemType(StructuredQName alias) {
        return typeAliases.get(alias);
    }
}

