////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NoNamespaceName;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.push.Container;
import net.sf.saxon.s9api.push.Document;
import net.sf.saxon.s9api.push.Element;
import net.sf.saxon.s9api.push.Push;
import net.sf.saxon.str.StringView;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;

public class PushToReceiver implements Push {

    private final ComplexContentOutputter cco;
    private final Configuration config;

    public PushToReceiver(Receiver cco) {
        this.cco = new ComplexContentOutputter(new RegularSequenceChecker(cco, false));
        config = cco.getPipelineConfiguration().getConfiguration();
    }

    @Override
    public Document document(boolean wellFormed) throws SaxonApiException {
        try {
            cco.open();
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
        return new DocImpl(cco, wellFormed);
    }

    private abstract class ContainerImpl implements Container {

        private ComplexContentOutputter cco;
        private String defaultNamespace;
        private ElemImpl elementAwaitingClosure;
        private boolean closed;


        public ContainerImpl(ComplexContentOutputter cco, String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
            this.cco = cco;
        }

        @Override
        public void setDefaultNamespace(String uri) {
            this.defaultNamespace = uri;
        }

        @Override
        public Element element(QName name) throws SaxonApiException {
            try {
                implicitClose();
                final FingerprintedQName fp = new FingerprintedQName(
                        name.getStructuredQName(), getConfiguration().getNamePool());
                getOutputter().startElement(fp, Untyped.getInstance(), Loc.NONE, ReceiverOption.NONE);
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
            return elementAwaitingClosure = new ElemImpl(getOutputter(), defaultNamespace);
        }

        @Override
        public Element element(String name) throws SaxonApiException {
            try {
                implicitClose();
                final NodeName fp = defaultNamespace.isEmpty()
                        ? new NoNamespaceName(name)
                        : new FingerprintedQName("", NamespaceUri.of(defaultNamespace), name);
                cco.startElement(fp, Untyped.getInstance(), Loc.NONE, ReceiverOption.NONE);
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
            return elementAwaitingClosure = new ElemImpl(getOutputter(), defaultNamespace);
        }

        @Override
        public Container text(CharSequence value) throws SaxonApiException {
            try {
                implicitClose();
                if (value != null && value.length() > 0) {
                    getOutputter().characters(StringView.of(value.toString()), Loc.NONE, ReceiverOption.NONE);
                }
                return this;
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }

        @Override
        public Container comment(CharSequence value) throws SaxonApiException {
            try {
                implicitClose();
                if (value != null) {
                    getOutputter().comment(StringView.of(value.toString()), Loc.NONE, ReceiverOption.NONE);
                }
                return this;
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }

        @Override
        public Container processingInstruction(String name, CharSequence value) throws SaxonApiException {
            try {
                implicitClose();
                if (value != null) {
                    getOutputter().processingInstruction(name, StringView.of(value.toString()), Loc.NONE, ReceiverOption.NONE);
                }
                return this;
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }


        @Override
        public void close() throws SaxonApiException {
            if (!closed) {
                implicitClose();
                sendEndEvent();
                closed = true;
            }
        }

        private void implicitClose() throws SaxonApiException {
            if (closed) {
                throw new SaxonApiException("The container has been closed");
            }
            if (elementAwaitingClosure != null) {
                elementAwaitingClosure.close();
                elementAwaitingClosure = null;
            }
        }

        abstract protected void sendEndEvent() throws SaxonApiException;

        protected ComplexContentOutputter getOutputter() {
            return cco;
        }

        protected Configuration getConfiguration() {
            return cco.getConfiguration();
        }
    }

    private class DocImpl extends ContainerImpl implements Document {

        private final boolean wellFormed;
        private boolean foundElement = false;

        DocImpl(ComplexContentOutputter cco, boolean wellFormed) throws SaxonApiException {
            super(cco, "");
            try {
                this.wellFormed = wellFormed;
                cco.startDocument(ReceiverOption.NONE);
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }

        @Override
        public Element element(QName name) throws SaxonApiException {
            if (wellFormed && foundElement) {
                throw new SaxonApiException("A well-formed document cannot have more than one element child");
            }
            foundElement = true;
            return super.element(name);
        }

        @Override
        public Element element(String name) throws SaxonApiException {
            if (wellFormed && foundElement) {
                throw new SaxonApiException("A well-formed document cannot have more than one element child");
            }
            foundElement = true;
            return super.element(name);
        }

        @Override
        public DocImpl text(CharSequence value) throws SaxonApiException {
            if (wellFormed && value != null && value.length() > 0) {
                throw new SaxonApiException("A well-formed document cannot contain text outside any element");
            }
            return (DocImpl)super.text(value);
        }

        @Override
        public Document comment(CharSequence value) throws SaxonApiException {
            return (Document)super.comment(value);
        }

        @Override
        public Document processingInstruction(String name, CharSequence value) throws SaxonApiException {
            return (Document)super.processingInstruction(name, value);
        }

        @Override
        protected void sendEndEvent() throws SaxonApiException {
            try {
                if (wellFormed && !foundElement) {
                    throw new SaxonApiException("A well-formed document must contain an element node");
                }
                getOutputter().endDocument();
                getOutputter().close();
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }
    }

    private class ElemImpl extends ContainerImpl implements Element {

        private boolean foundChild;

        ElemImpl(ComplexContentOutputter cco, String defaultNamespace) {
            super(cco, defaultNamespace);
        }

        @Override
        public Element attribute(QName name, String value) throws SaxonApiException {
            checkChildNotFound();
            try {
                if (value != null) {
                    final FingerprintedQName fp =
                            new FingerprintedQName(name.getStructuredQName(), getConfiguration().getNamePool());
                    getOutputter().attribute(fp, BuiltInAtomicType.UNTYPED_ATOMIC, value, Loc.NONE, ReceiverOption.NONE);
                }
                return this;
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }

        @Override
        public Element attribute(String name, String value) throws SaxonApiException {
            checkChildNotFound();
            try {
                if (value != null) {
                    final NodeName fp = new NoNamespaceName(name);
                    getOutputter().attribute(fp, BuiltInAtomicType.UNTYPED_ATOMIC, value, Loc.NONE, ReceiverOption.NONE);
                }
                return this;
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }

        @Override
        public Element namespace(String prefix, String uri) throws SaxonApiException {
            checkChildNotFound();
            try {
                getOutputter().namespace(prefix, NamespaceUri.of(uri), ReceiverOption.NONE);
                return this;
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }

        private void checkChildNotFound() throws SaxonApiException {
            if (foundChild) {
                throw new SaxonApiException("Attribute nodes must be attached to an element before any children");
            }
        }

        @Override
        public Element element(QName name) throws SaxonApiException {
            foundChild = true;
            return super.element(name);
        }

        @Override
        public Element element(String name) throws SaxonApiException {
            foundChild = true;
            return super.element(name);
        }

        @Override
        public Element text(CharSequence value) throws SaxonApiException {
            foundChild = true;
            return (Element)super.text(value);
        }

        @Override
        public Element comment(CharSequence value) throws SaxonApiException {
            foundChild = true;
            return (Element)super.comment(value);
        }

        @Override
        public Element processingInstruction(String name, CharSequence value) throws SaxonApiException {
            foundChild = true;
            return (Element)super.processingInstruction(name, value);
        }

        @Override
        protected void sendEndEvent() throws SaxonApiException {
            try {
                getOutputter().endElement();
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }


    }
}

