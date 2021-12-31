/**
 * <p>This package provides glue classes that enable Saxon to process a source
 * document supplied as a DOM tree in the form of a DOMSource object; it also provides
 * classes that present a DOM view of Saxon's native tree structures.</p>
 * <p>The native Saxon tree structures (the linked tree and tiny tree) do not
 * implement DOM interfaces directly. However, Saxon supports the DOM at two levels:</p>
 * <ul>
 * <li><p>The input to a transformation or query may be supplied in the form of
 * a <code>DOMSource</code> (which contains a DOM document). Saxon is capable of either performing the
 * query or transformation on the DOM <i>in situ</i>, by wrapping the DOM nodes in
 * a layer that make them appear to be Saxon nodes, or of converting the DOM to Saxon's native tree
 * implementation. </p></li>
 * <li><p>It is possible for a transformation or query to call extension functions
 * that use DOM interfaces to access a Saxon tree. If the Saxon tree is in fact a wrapper
 * around the DOM, then extension functions will be presented with the underlying
 * DOM nodes. In other cases, Saxon adds a wrapper to the native Saxon nodes to make
 * them implement the DOM interfaces.</p>
 * <p>Note that Saxon's tree structures are immutable. Updating interfaces
 * in the DOM API are therefore not supported.</p></li>
 * </ul>
 * <p>The classes {@link net.sf.saxon.dom.NodeWrapper} and {@link net.sf.saxon.dom.DocumentWrapper} implement the Saxon
 * interface
 * {@link net.sf.saxon.om.NodeInfo} on top of an underlying DOM
 * <code>Node</code> or <code>Document</code> object. This enables XPath expressions to be executed
 * directly against
 * the DOM.</p>
 * <p>The classes {@link net.sf.saxon.dom.NodeOverNodeInfo},
 * {@link net.sf.saxon.dom.DocumentOverNodeInfo}, and the like do the converse:
 * they provide a DOM wrapper over a native Saxon node.</p>
 * <p><b>Note that using the DOM with Saxon is considerably less efficient than using
 * Saxon's native tree implementations, the TinyTree and the LinkedTree.
 * The DOM should be used only where there is some good reason, e.g. where other parts
 * of the application have to use DOM interfaces.</b></p>
 * <p>Saxon doesn't stop you modifying the contents of the DOM in the course of a
 * transformation (for example, from an extension function, or in a different thread)
 * but the consequences of doing so are unpredictable.</p>
 */
package net.sf.saxon.dom;
