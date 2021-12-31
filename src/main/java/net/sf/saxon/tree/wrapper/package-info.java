/**
 * <p>This package defines a number of implementations of "virtual nodes" implemented as wrappers around other nodes.</p>
 * <p>The {@link net.sf.saxon.tree.wrapper.SpaceStrippedNode} class provides a virtual tree in which whitespace text nodes
 * in the underlying real
 * tree are ignored.</p>
 * <p>The {@link net.sf.saxon.tree.wrapper.TypeStrippedNode} class provides a virtual tree in which type annotations in the
 * underlying real tree
 * are ignored.</p>
 * <p>The {@link net.sf.saxon.tree.wrapper.VirtualCopy} class provides a tree that is the same as the underlying tree in
 * everything except
 * node identity.</p>
 */
package net.sf.saxon.tree.wrapper;
