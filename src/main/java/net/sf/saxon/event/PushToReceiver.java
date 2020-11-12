////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NoNamespaceName;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Push;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;

public class PushToReceiver implements Push {

    private ComplexContentOutputter out;
    private Configuration config;

    public PushToReceiver(Receiver out) {
        this.out = new ComplexContentOutputter(new RegularSequenceChecker(out, false));
        config = out.getPipelineConfiguration().getConfiguration();
    }

    @Override
    public Document document(boolean wellFormed) throws SaxonApiException {
        try {
            out.open();
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
        return new DocImpl(wellFormed);
    }

    private abstract class ContainerImpl implements Push.Container {

        private String defaultNamespace;
        private ElemImpl elementAwaitingClosure;
        private boolean closed;


        public ContainerImpl(String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
        }

        @Override
        public void setDefaultNamespace(String uri) {
            this.defaultNamespace = uri;
        }

        @Override
        public Element element(QName name) throws SaxonApiException {
            try {
                implicitClose();
                final FingerprintedQName fp = new FingerprintedQName(name.getStructuredQName(), config.getNamePool());
                out.startElement(fp, Untyped.getInstance(), Loc.NONE, ReceiverOption.NONE);
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
            return elementAwaitingClosure = new ElemImpl(defaultNamespace);
        }

        @Override
        public Element element(String name) throws SaxonApiException {
            try {
                implicitClose();
                final NodeName fp = defaultNamespace.isEmpty()
                        ? new NoNamespaceName(name)
                        : new FingerprintedQName("", defaultNamespace, name);
                out.startElement(fp, Untyped.getInstance(), Loc.NONE, ReceiverOption.NONE);
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
            return elementAwaitingClosure = new ElemImpl(defaultNamespace);
        }

        @Override
        public Container text(CharSequence value) throws SaxonApiException {
            try {
                implicitClose();
                if (value != null && value.length() > 0) {
                    out.characters(value, Loc.NONE, ReceiverOption.NONE);
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
                    out.comment(value, Loc.NONE, ReceiverOption.NONE);
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
                    out.processingInstruction(name, value, Loc.NONE, ReceiverOption.NONE);
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

        abstract void sendEndEvent() throws SaxonApiException;
    }

    private class DocImpl extends ContainerImpl implements Document {

        private final boolean wellFormed;
        private boolean foundElement = false;

        DocImpl(boolean wellFormed) throws SaxonApiException {
            super("");
            try {
                this.wellFormed = wellFormed;
                out.startDocument(ReceiverOption.NONE);
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
        void sendEndEvent() throws SaxonApiException {
            try {
                if (wellFormed && !foundElement) {
                    throw new SaxonApiException("A well-formed document must contain an element node");
                }
                out.endDocument();
                out.close();
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }
    }

    private class ElemImpl extends ContainerImpl implements Element {

        private boolean foundChild;

        ElemImpl(String defaultNamespace) {
            super(defaultNamespace);
        }

        @Override
        public Element attribute(QName name, String value) throws SaxonApiException {
            checkChildNotFound();
            try {
                if (value != null) {
                    final FingerprintedQName fp = new FingerprintedQName(name.getStructuredQName(), config.getNamePool());
                    out.attribute(fp, BuiltInAtomicType.UNTYPED_ATOMIC, value, Loc.NONE, ReceiverOption.NONE);
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
                    out.attribute(fp, BuiltInAtomicType.UNTYPED_ATOMIC, value, Loc.NONE, ReceiverOption.NONE);
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
                out.namespace(prefix, uri, ReceiverOption.NONE);
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
        void sendEndEvent() throws SaxonApiException {
            try {
                out.endElement();
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }


    }
}

