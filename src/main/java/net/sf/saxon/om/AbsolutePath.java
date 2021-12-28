////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.trans.Err;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents the path from the root of an XDM tree to a specific node, as a sequence of (name, position) pairs
 */

public class AbsolutePath {

    private List<PathElement> path;
    private String systemId;


    /**
     * Create an absolute path given a list of path elements
     *
     * @param path the list of path elements, starting from the root. It is not necessary to include a path element
     *             for the document node.
     */

    public AbsolutePath(List<PathElement> path) {
        this.path = new ArrayList<>(path);
    }

    /**
     * Append an attribute name to the path
     * @param attributeName the name of the attribute
     */

    public void appendAttributeName(NodeName attributeName) {
        if (!path.isEmpty()) {
            PathElement last = path.get(path.size()-1);
            if (last.getNodeKind() == Type.ATTRIBUTE) {
                path.remove(path.size()-1);
            }
        }
        PathElement att = new PathElement(Type.ATTRIBUTE, attributeName, 1);
        path.add(att);
    }

    /**
     * Create an absolute path given a Node
     * @param node the node whose path is required
     */

    public static AbsolutePath pathToNode(NodeInfo node) {
        LinkedList<PathElement> list = new LinkedList<>();
        while (node != null && node.getNodeKind() != Type.DOCUMENT) {
            PathElement pe = new PathElement(node.getNodeKind(), NameOfNode.makeName(node), Navigator.getNumberSimple(node, null));
            list.addFirst(pe);
            node = node.getParent();
        }
        return new AbsolutePath(list);
    }

    /**
     * Get a string representing the path using namespace prefixes to represent QNames
     *
     * @return the path in the form <code>/prefix:local[n]/prefix:local[m]/...</code>
     */

    public String getPathUsingPrefixes() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C256);
        for (AbsolutePath.PathElement pe : path) {
            fsb.cat('/');
            pe.toString(fsb, 'p');
        }
        return fsb.toString();
    }

    /**
     * Get a string representing the path using namespace URIs to represent QNames
     *
     * @return the path in the form <code>/Q{uri}local[n]/Q{uri}local[m]/...</code>
     */

    public String getPathUsingUris() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C256);
        for (AbsolutePath.PathElement pe : path) {
            fsb.cat('/');
            pe.toString(fsb, 'u');
        }
        return fsb.toString();
    }

    /**
     * Get a string representing the path using abbreviated namespace URIs to represent QNames
     *
     * @return the path in the form <code>/Q{uri}local[n]/Q{uri}local[m]/...</code>, with the URIs shortened
     */

    public String getPathUsingAbbreviatedUris() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C256);
        for (AbsolutePath.PathElement pe : path) {
            fsb.cat('/');
            pe.toString(fsb, 's');
        }
        return fsb.toString();
    }

    @Override
    public String toString() {
        return getPathUsingUris();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AbsolutePath && obj.toString().equals(toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getSystemId() {
        return systemId;
    }


    /**
     * Inner class representing one step in the path
     */

    public static class PathElement {
        int nodeKind;
        NodeName name;
        int index;

        /**
         * Create a path element
         *
         * @param nodeKind the kind of node
         * @param name     the name of the node
         * @param index    the position of the node relative to siblings of the same node kind and name.
         *                 The value -1 indicates "not known", which will typically be the case for streamed nodes.
         */
        public PathElement(int nodeKind, NodeName name, int index) {
            this.nodeKind = nodeKind;
            this.name = name;
            this.index = index;
        }

        /**
         * Get the node kind
         *
         * @return the node kind, as a constant from {@link net.sf.saxon.type.Type}
         */
        public int getNodeKind() {
            return nodeKind;
        }

        /**
         * Get the name of the node
         *
         * @return the node name
         */

        public NodeName getName() {
            return name;
        }

        /**
         * Get the position of the node
         *
         * @return the position relative to siblings of the same node kind and name.
         * The value -1 indicates "not known", which will typically be the case for streamed nodes.
         */

        public int getIndex() {
            return index;
        }

        /**
         * Get a string representation of the path
         *
         * @param fsb    buffer into which the string representation will be written
         * @param option for representing namespaces:
         *               'p': use namepace prefix. 'u': use full URI. 's': use abbreviated URI
         */

        public void toString(FastStringBuffer fsb, char option) {
            switch (nodeKind) {
                case Type.DOCUMENT:
                    fsb.append("(/)");
                    break;
                case Type.ATTRIBUTE:
                    fsb.cat('@');
                    if (!name.getURI().isEmpty()) {
                        if (option == 'u') {
                            fsb.append("Q{");
                            fsb.append(name.getURI());
                            fsb.append("}");
                        } else if (option == 'p') {
                            String prefix = name.getPrefix();
                            if (!prefix.isEmpty()) {
                                fsb.append(prefix);
                                fsb.cat(':');
                            }
                        } else if (option == 's') {
                            fsb.append("Q{");
                            fsb.append(Err.abbreviateURI(name.getURI()));
                            fsb.append("}");
                        }
                    }
                    fsb.append(getName().getLocalPart());
                    break;
                case Type.ELEMENT:
                    if (option == 'u') {
                        fsb.append("Q{");
                        fsb.append(name.getURI());
                        fsb.append("}");
                    } else if (option == 'p') {
                        String prefix = name.getPrefix();
                        if (!prefix.isEmpty()) {
                            fsb.append(prefix);
                            fsb.cat(':');
                        }
                    } else if (option == 's') {
                        if (!name.getURI().isEmpty()) {
                            fsb.append("Q{");
                            fsb.append(Err.abbreviateURI(name.getURI()));
                            fsb.append("}");
                        }
                    }
                    fsb.append(name.getLocalPart());
                    appendPredicate(fsb);
                    break;
                case Type.TEXT:
                    fsb.append("text()");
                    break;
                case Type.COMMENT:
                    fsb.append("comment()");
                    appendPredicate(fsb);
                    break;
                case Type.PROCESSING_INSTRUCTION:
                    fsb.append("processing-instruction(");
                    fsb.append(name.getLocalPart());
                    fsb.append(")");
                    appendPredicate(fsb);
                    break;
                case Type.NAMESPACE:
                    fsb.append("namespace::");
                    if (name.getLocalPart().isEmpty()) {
                        fsb.append("*[Q{" + NamespaceConstant.FN + "}local-name()=\"\"]");
                    } else {
                        fsb.append(name.getLocalPart());
                    }
                    break;
                default:
            }
        }

        private void appendPredicate(FastStringBuffer fsb) {
            int index = getIndex();
            if (index != -1) {
                fsb.cat('[');
                fsb.append(getIndex() + "");
                fsb.cat(']');
            }
        }
    }




}

