////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.regex.RegexMatchHandler;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;

/**
 * Implements the fn:analyze-string function first defined in XPath 3.0.
 */
public class AnalyzeStringFn extends RegexFunction {

    private static class ResultNamesAndTypes {
        public NodeName resultName;
        public NodeName nonMatchName;
        public NodeName matchName;
        public NodeName groupName;
        public NodeName lookaheadGroupName;
        public NodeName groupNrName;
        public NodeName positionName;
        public NodeName valueName;
        public SchemaType resultType = Untyped.INSTANCE;
        public SchemaType nonMatchType = Untyped.INSTANCE;
        public SchemaType matchType = Untyped.INSTANCE;
        public SchemaType groupType = Untyped.INSTANCE;
        public SchemaType lookaheadGroupType = Untyped.INSTANCE;
        public SimpleType groupNrType = BuiltInAtomicType.UNTYPED_ATOMIC;
        public SimpleType positionType = BuiltInAtomicType.UNTYPED_ATOMIC;
        public SimpleType valueType = BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    private final ResultNamesAndTypes vocab = new ResultNamesAndTypes();

    @Override
    protected boolean allowRegexMatchingEmptyString() {
        return getRetainedStaticContext().getPackageData().getHostLanguageVersion() >= 40;
    }

    private synchronized void init(Configuration config, Schema schema) throws XPathException {
        vocab.resultName = new FingerprintedQName("", NamespaceUri.FN, "analyze-string-result");
        vocab.nonMatchName = new FingerprintedQName("", NamespaceUri.FN, "non-match");
        vocab.matchName = new FingerprintedQName("", NamespaceUri.FN, "match");
        vocab.groupName = new FingerprintedQName("", NamespaceUri.FN, "group");
        vocab.lookaheadGroupName = new FingerprintedQName("", NamespaceUri.FN, "lookahead-group");
        vocab.groupNrName = new NoNamespaceName("nr");
        vocab.positionName = new NoNamespaceName("position");
        vocab.valueName = new NoNamespaceName("value");

    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public NodeInfo call(XPathContext context, Sequence[] arguments) throws XPathException {
        Item inputItem = arguments[0].head();
        UnicodeString input;
        if (inputItem == null) {
            input = EmptyUnicodeString.getInstance();
        } else {
            input = inputItem.getUnicodeStringValue();
        }
        RegularExpression re = getRegularExpression(arguments, 1, 2);
        RegexIterator iter = re.analyze(input);

        init(context.getConfiguration(), null);

        final Builder builder = context.getController().makeBuilder();
        final ComplexContentOutputter out = new ComplexContentOutputter(builder);
        LocalRegexMatchHandler handler = new LocalRegexMatchHandler(out, vocab);
        builder.setBaseURI(getStaticBaseUriString());
        builder.setDurability(Durability.TEMPORARY);
        out.open();
        out.startElement(vocab.resultName, vocab.resultType, Loc.NONE, ReceiverOption.NONE);
        out.startContent();
        for (Item item; (item = iter.next()) != null; ) {
            if (iter.isMatching()) {
                out.startElement(vocab.matchName, vocab.matchType, Loc.NONE, ReceiverOption.NONE);
                out.startContent();
                iter.processMatchingSubstring(handler);
                out.endElement();
            } else {
                out.startElement(vocab.nonMatchName, vocab.nonMatchType, Loc.NONE, ReceiverOption.NONE);
                out.startContent();
                out.characters(item.getUnicodeStringValue(), Loc.NONE, ReceiverOption.NONE);
                out.endElement();
            }
        }

        out.endElement();
        out.close();
        return builder.getCurrentRoot();

    }

    private static class LocalRegexMatchHandler implements RegexMatchHandler {
        private final ComplexContentOutputter out;
        private final ResultNamesAndTypes vocab;

        public LocalRegexMatchHandler(ComplexContentOutputter out, ResultNamesAndTypes vocab) {
            this.out = out;
            this.vocab = vocab;
        }

        @Override
        public void characters(UnicodeString s) throws XPathException {
            out.characters(s, Loc.NONE, ReceiverOption.NONE);
        }

        @Override
        public void onGroupStart(int groupNumber) throws XPathException {
            out.startElement(vocab.groupName, vocab.groupType,
                             Loc.NONE, ReceiverOption.NONE);
            out.attribute(vocab.groupNrName, vocab.groupNrType, "" + groupNumber,
                          Loc.NONE, ReceiverOption.NONE);
            out.startContent();
        }

        @Override
        public void onGroupEnd(int groupNumber) throws XPathException {
            out.endElement();
        }

        /**
         * Method to be called when a lookahead captured group is encountered
         *
         * @param groupNumber the group number of the captured group
         * @param position    the position within the input string of the captured group
         * @param value       the captured substring
         */

        public void onLookaheadGroup(int groupNumber, int position, UnicodeString value) throws XPathException {
            out.startElement(vocab.lookaheadGroupName, vocab.lookaheadGroupType,
                             Loc.NONE, ReceiverOption.NONE);
            out.attribute(vocab.groupNrName, vocab.groupNrType, "" + groupNumber,
                          Loc.NONE, ReceiverOption.NONE);
            out.attribute(vocab.positionName, vocab.positionType, "" + position,
                          Loc.NONE, ReceiverOption.NONE);
            out.attribute(vocab.valueName, vocab.valueType, value.toString(),
                          Loc.NONE, ReceiverOption.NONE);
            out.startContent();
            out.endElement();
        }

    }

}

// Copyright (c) 2018-2026 Saxonica Limited
