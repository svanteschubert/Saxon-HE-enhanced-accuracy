/**
 * <p>This package contains an implementation of persistent immutable arrays.</p>
 * <p>("Persistent" seems to have acquired a new meaning. It no longer refers to
 * values that outlive the execution of the program that created them, but now refers
 * to data structures where modification actions leave the existing value unchanged.)</p>
 * <p>The first version of this package (released with Saxon 9.9.0.1) took code from
 * the PCollections library at https://github.com/hrldcpr/pcollections. In 9.9.1.1 this has
 * been replaced by a home-brew implementation written entirely by Saxonica.</p>
 * <p>The implementation uses a simple binary tree, in which the left-hand half of the
 * array is in one subtree, and the right-hand half in the other. The tree is kept
 * balanced as necessary. There are two special-case implementations for empty and
 * singleton trees.</p>
 */
package net.sf.saxon.ma.parray;
