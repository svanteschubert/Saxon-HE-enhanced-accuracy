////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.tiny.TinyBuilderCondensed;

/**
 * A TreeModel represents an implementation of the Saxon NodeInfo interface, which itself
 * is essentially an implementation of the XDM model defined in W3C specifications (except
 * that Saxon's NodeInfo understands the 13 XPath axes, rather than merely supporting
 * parent and child properties).
 * <p>This class serves two purposes: it acts as a factory for obtaining a Builder which
 * can be used to build trees using this tree model; and it provides static constants
 * that can be used to identify the built-in tree models.</p>
 */

public abstract class TreeModel {

    /**
     * The TinyTree implementation. This is normally the default implementation
     * of the tree model.
     */
    public final static TreeModel TINY_TREE = new TinyTree();

    /**
     * The CondensedTinyTree implementation. This is a variant of the TinyTree that
     * uses less memory but takes a little longer to build. Run-time performance
     * is the same as the TinyTree.
     */
    public final static TreeModel TINY_TREE_CONDENSED = new TinyTreeCondensed();

    /**
     * The LinkedTree. This takes more memory than the TinyTree, but offers flexibility
     * for storing user-defined data in each element node; it is also mutable, supporting
     * XQuery Update
     */
    public final static TreeModel LINKED_TREE = new LinkedTree();

    /**
     * Make a Builder, which can then be used to construct an instance of this tree model
     * from a stream of events
     *
     * @param pipe A PipelineConfiguration, which can be constructed using the method
     *             {@link net.sf.saxon.Configuration#makePipelineConfiguration()}.
     * @return a newly created Builder
     */

    public abstract Builder makeBuilder(PipelineConfiguration pipe);

    /**
     * Get the integer constant used to identify this tree model in some legacy interfaces
     *
     * @return an integer constant used to identify the model, for example {@link Builder#TINY_TREE}
     */

    public int getSymbolicValue() {
        return Builder.UNSPECIFIED_TREE_MODEL;
    }

    /**
     * Get the tree model corresponding to a given integer constant
     *
     * @param symbolicValue typically one of the constants {@link Builder#TINY_TREE},
     *                      {@link Builder#TINY_TREE_CONDENSED}, {@link Builder#LINKED_TREE}.
     * @return the corresponding TreeModel
     */

    public static TreeModel getTreeModel(int symbolicValue) {
        switch (symbolicValue) {
            case Builder.TINY_TREE:
                return TreeModel.TINY_TREE;
            case Builder.TINY_TREE_CONDENSED:
                return TreeModel.TINY_TREE_CONDENSED;
            case Builder.LINKED_TREE:
                return TreeModel.LINKED_TREE;
            default:
                throw new IllegalArgumentException("tree model " + symbolicValue);
        }
    }

    /**
     * Ask whether this tree model supports updating (that is, whether the nodes
     * in the constructed tree will implement {@link MutableNodeInfo}, which is necessary
     * if they are to support XQuery Update. This method can be overridden in subclasses;
     * the default implementation returns false.
     *
     * @return true if the tree model implementation supports updating, that is, if its
     *         nodes support the MutableNodeInfo interface.
     */

    public boolean isMutable() {
        return false;
    }

    /**
     * Ask whether this tree model supports the use of type annotations on element and attribute
     * nodes. If false, all nodes in this tree are untyped (for elements) or untypedAtomic (for attributes).
     * The default implementation returns false.
     *
     * @return true if type annotations other than xs:untyped and xs:untypedAtomic are supported.
     */

    public boolean isSchemaAware() {
        return false;
    }

    /**
     * Get a name that identifies the tree model
     *
     * @return an identifying name for the tree model
     */

    public String getName() {
        return toString();
    }

    private static class TinyTree extends TreeModel {

        @Override
        public Builder makeBuilder(PipelineConfiguration pipe) {
            TinyBuilder builder = new TinyBuilder(pipe);
            builder.setStatistics(pipe.getConfiguration().getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
            return builder;
        }

        @Override
        public int getSymbolicValue() {
            return Builder.TINY_TREE;
        }

        @Override
        public boolean isSchemaAware() {
            return true;
        }

        @Override
        public String getName() {
            return "TinyTree";
        }
    }

    private static class TinyTreeCondensed extends TreeModel {
        @Override
        public Builder makeBuilder(PipelineConfiguration pipe) {
            TinyBuilderCondensed tbc = new TinyBuilderCondensed(pipe);
            tbc.setStatistics(pipe.getConfiguration().getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
            return tbc;
        }

        @Override
        public int getSymbolicValue() {
            return Builder.TINY_TREE_CONDENSED;
        }

        @Override
        public boolean isSchemaAware() {
            return true;
        }

        @Override
        public String getName() {
            return "TinyTreeCondensed";
        }
    }

    private static class LinkedTree extends TreeModel {

        /*@NotNull*/
        @Override
        public Builder makeBuilder(PipelineConfiguration pipe) {
            return new LinkedTreeBuilder(pipe);
        }

        @Override
        public int getSymbolicValue() {
            return Builder.LINKED_TREE;
        }

        @Override
        public boolean isSchemaAware() {
            return true;
        }

        @Override
        public String getName() {
            return "LinkedTree";
        }
    }

}

