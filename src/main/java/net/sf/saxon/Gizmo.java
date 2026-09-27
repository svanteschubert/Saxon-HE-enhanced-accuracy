////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.*;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.Tokenizer;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.gizmo.DefaultTalker;
import net.sf.saxon.gizmo.JLine2Talker;
import net.sf.saxon.gizmo.Talker;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.str.StringView;
import net.sf.saxon.sxpath.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Schema;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.gnode.NamedXNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.*;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

/**
 * A Gizmo is a simple tool for performing a particular easily specified transformation on an XML
 * document, for example deleting or renaming all the elements with a particular name. The class
 * offers a command-line interface.
 */

public class Gizmo {

    private final Configuration config;
    private final IndependentContext env;
    private DocumentImpl currentDoc;
    private boolean unsaved = false;
    private final Map<StructuredQName, GroundedValue> variables = new HashMap<>();
    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final Talker talker;
    private boolean typed = false;
    private final List<DocumentImpl> undoBuffer = new LinkedList<>();
    private PrintStream sysOut = System.out;
    private Schema currentSchema = null;

    private static class SubCommand {
        String name;
        String helpText;
        Action action;
    }

    @FunctionalInterface
    private interface Action {
        void perform(StringBuilder subCommand) throws XPathException;
    }

    private void addCommand(String name, String helpText, Action action) {
        SubCommand c = new SubCommand();
        c.name = name;
        c.helpText = helpText;
        c.action = action;
        subCommands.put(name, c);
    }

    private void initSubCommands() {
        addCommand("call",
                   "call {filename} -- execute script from a file",
                   this::call);
        addCommand("copy",
                   "copy {expression} -- make deep copy of all selected nodes",
                   this::copy);
        addCommand("delete",
                   "delete {expression} -- delete all selected nodes, with their content",
                   this::delete);
        addCommand("follow",
                   "follow {expression} with {query} -- add result of query after each selected node",
                   (cmd) -> update(cmd, "follow"));
        addCommand("help",
                   "help {keyword} -- help on a specific command, or '?' for all commands",
                   this::help);
        addCommand("list",
                   "list {expression} -- display paths of selected nodes",
                   this::list);
        addCommand("load",
                   "load {fileName} -- load new source document from file",
                   this::load);
        addCommand("namespace",
                   "namespace {prefix} {uri} -- bind namespace prefix to URI",
                   this::namespace);
        addCommand("paths",
                   "paths -- display all distinct element paths in the document",
                   (cmd) -> list(new StringBuilder("distinct-values(//*!('/'||string-join(ancestor-or-self::*!name(),'/')))")));
        addCommand("precede",
                   "precede {expression} with {query} -- add result of query before each selected node",
                   (cmd) -> update(cmd, "precede"));
        addCommand("prefix",
                   "prefix {expression} with {query} -- add result of query as first child of each selected node",
                   (cmd) -> update(cmd, "prefix"));
        addCommand("quit",
                   "quit [now] -- stop Gizmo",
                   (cmd) -> {throw new RuntimeException();});
        addCommand("rename",
                   "rename {expression-1} as {expression-2} -- change the name of selected nodes",
                   this::rename);
        addCommand("replace",
                   "replace {expression} with {query} -- replace selected nodes with result of query",
                   this::replace);
        addCommand("save",
                   "save {filename} {output-property=value}... -- save current document to file",
                   this::save);
        addCommand("schema",
                   "schema {filename} -- load XSD 1.1 schema for use in validation",
                   this::schema);
        addCommand("set",
                   "set {variable} = {expression} -- set variable to value of expression",
                   this::set);
        addCommand("show",
                   "show {expression} -- display value of expression",
                   this::show);
        addCommand("strip",
                   "strip -- delete whitespace text nodes",
                   (cmd -> this.delete(new StringBuilder("//text()[not(normalize-space())]"))));
        addCommand("suffix",
                   "suffix {expression} with {query} -- add result of query as last child of each selected node",
                   (cmd) -> update(cmd, "suffix"));
        addCommand("transform",
                   "transform {filename} -- transform current document using stylesheet in named file",
                   this::transform);
        addCommand("undo",
                   "undo -- revert the most recent changes",
                   this::undo);
        addCommand("update",
                   "update {expression} with {query} -- replace content of selected nodes with result of query",
                   (cmd) -> update(cmd, "content"));
        addCommand("validate",
                   "validate -- validate against loaded schema and/or xsi:schemaLocation",
                   this::validate);
        addCommand("?", "", this::help);
    }

    public static void main(String[] args) {
        new Gizmo(args);
    }

    public Gizmo(String[] args) {
        initSubCommands();
        config = Configuration.newConfiguration();
        boolean allow40 = config.isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY);
        if (allow40) {
            config.setConfigurationProperty(Feature.ALLOW_SYNTAX_EXTENSIONS, true);
        }
        env = new IndependentContext(config);
        env.setXPathLanguageLevel(allow40 ? 40 : 31);
        String source = null;
        String script = null;
        boolean interactive = true;
        for (String arg : args) {
            if (arg.startsWith("-s:")) {
                source = arg.substring(3);
            }
            if (arg.startsWith("-q:")) {
                script = arg.substring(3);
                interactive = false;
            }
        }
        talker = initTalker(script);
        //System.err.println("Using term=" + talker.getClass().getCanonicalName());

        List<String> sortedNames = new ArrayList<>(Arrays.asList(keywords));
        Collections.sort(sortedNames);
        talker.setAutoCompletion(sortedNames);
        
        if (source != null) {
            try {
                load(new StringBuilder(source));
            } catch (XPathException e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
        } else {
            try {
                String dummy = "<dummy/>";
                StreamSource ss = new StreamSource(new StringReader(dummy));
                ParseOptions options = new ParseOptions()
                        .withModel(TreeModel.LINKED_TREE)
                        .withLineNumbering(true);
                currentDoc = (DocumentImpl) config.buildDocumentTree(ss, options).getRootNode();
                typed = false;
            } catch (XPathException e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
        }

        env.declareNamespace("xml", NamespaceUri.XML);
        env.declareNamespace("xsl", NamespaceUri.XSLT);
        env.declareNamespace("saxon", NamespaceUri.SAXON);
        env.declareNamespace("xs", NamespaceUri.SCHEMA);
        env.declareNamespace("xsi", NamespaceUri.SCHEMA_INSTANCE);
        env.declareNamespace("fn", NamespaceUri.FN);
        env.declareNamespace("math", NamespaceUri.MATH);
        env.declareNamespace("map", NamespaceUri.MAP_FUNCTIONS);
        env.declareNamespace("array", NamespaceUri.ARRAY_FUNCTIONS);
        env.declareNamespace("", NamespaceUri.NULL);

        env.setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.ANY_NAMESPACE);

        sysOut.println("Saxon Gizmo " + Version.getProductVersion());
        executeCommands(talker, interactive);
    }

    /**
     * Diagnostic interface
     */
    public Gizmo() {
        initSubCommands();
        talker = null;
        config = Configuration.newConfiguration();
        config.setConfigurationProperty(Feature.ALLOW_SYNTAX_EXTENSIONS, true);
        env = new IndependentContext(config);
        env.setXPathLanguageLevel(40);

        env.declareNamespace("xml", NamespaceUri.XML);
        env.declareNamespace("xsl", NamespaceUri.XSLT);
        env.declareNamespace("saxon", NamespaceUri.SAXON);
        env.declareNamespace("xs", NamespaceUri.SCHEMA);
        env.declareNamespace("xsi", NamespaceUri.SCHEMA_INSTANCE);
        env.declareNamespace("fn", NamespaceUri.FN);
        env.declareNamespace("math", NamespaceUri.MATH);
        env.declareNamespace("map", NamespaceUri.MAP_FUNCTIONS);
        env.declareNamespace("array", NamespaceUri.ARRAY_FUNCTIONS);
        env.declareNamespace("", NamespaceUri.NULL);

        env.setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.ANY_NAMESPACE);

    }

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set the current document (for testing purposes)
     * @param doc the current document
     */
    public void setCurrentDoc(DocumentImpl doc) {
        this.currentDoc = doc;
    }

    /**
     * Get the current document (for testing purposes)
     * @return the current document
     */
    public DocumentImpl getCurrentDoc() {
        return currentDoc;
    }

    /**
     * Execute a test command
     * @param command the command to be executed
     * @return the output of the command, if any
     * @throws XPathException if the command execution fails
     */
    public String executeTestCommand(String command) throws XPathException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        sysOut = new PrintStream(outStream);
        int space = command.indexOf(' ');
        if (space < 0) {
            space = command.length();
        }
        String keyword = command.substring(0, space);
        String remainder = command.substring(space).trim();
        SubCommand cmd = subCommands.get(keyword);
        if (cmd == null) {
            throw new XPathException("\"Unknown command \" + cmd + \"");
        } else {
            cmd.action.perform(new StringBuilder(remainder));
        }
        return outStream.toString(StandardCharsets.UTF_8);
    }

    protected Talker initTalker(String script) {
        if (script == null) {
            try {
                return new JLine2Talker();
            } catch (IOException e) {
                System.err.println(e.getMessage());
                System.exit(2);
                return null;
            }
        } else {
            try {
                return new DefaultTalker(new FileInputStream(script), sysOut);
            } catch (FileNotFoundException e) {
                System.err.println(e.getMessage());
                System.exit(2);
                return null;
            }
        }
    }

    private void executeCommands(Talker talker, boolean interactive) {
        int emptyLines = 0;
        while (true) {
            try {
                String command = talker.exchange("");
                if (command.isEmpty() && !interactive) {
                    return;
                }
                while (command.isEmpty()) {
                    if (emptyLines++ > 2) {
                        command = talker.exchange("To exit, type 'quit'. For help, type 'help'");
                    }
                }
                emptyLines = 0;
                int space = command.indexOf(' ');
                if (space < 0) {
                    space = command.length();
                }
                String keyword = command.substring(0, space);
                String remainder = command.substring(space).trim();
                if (keyword.equals("quit")) {
                    boolean quit = true;
                    if (interactive && unsaved && !remainder.equals("now")) {
                        while (true) {
                            String answer = talker.exchange("Quit without saving? (Y|N)");
                            if (answer.equalsIgnoreCase("y")) {
                                break;
                            } else if (answer.equalsIgnoreCase("n")) {
                                quit = false;
                                break;
                            }
                            // continue
                        }
                    }
                    if (quit) {
                        break;
                    } else {
                        continue;
                    }
                }
                SubCommand cmd = subCommands.get(keyword);
                if (cmd == null) {
                    if (interactive) {
                        sysOut.println("Unknown command " + keyword + " (Use 'quit' to exit)");
                        help(new StringBuilder("?"));
                    } else {
                        throw new XPathException("\"Unknown command \" + cmd + \"");
                    }
                } else {
                    try {
                        cmd.action.perform(new StringBuilder(remainder));
                    } catch (UncheckedXPathException e) {
                        throw e.getXPathException();
                    }
                }

            } catch (XPathException e) {
                sysOut.println(e.showErrorCode() + ": " + e.getMessage());
                if (!interactive) {
                    System.exit(2);
                }
            }
        }
    }

    private void help(StringBuilder command) {
        String cmd = command == null ? null : command.toString().trim();
        if (cmd == null || cmd.isEmpty() || cmd.equals("help") || cmd.equals("?")) {
            sysOut.println("Commands available:");
            List<String> commands = new ArrayList<>(subCommands.keySet());
            commands.sort(null);
            for (String c : commands) {
                sysOut.println("  " + subCommands.get(c).helpText);
            }
        } else {
            SubCommand entry = subCommands.get(cmd);
            if (entry == null) {
                help(null);
            } else {
                sysOut.println(entry.helpText);
            }
        }
    }

    /**
     * Read an XPath expression from a supplied input string, execute the expression,
     * and return an iterator over the result. As a side effect, modify the supplied
     * StringBuilder so that it contains whatever remains after parsing the expression.
     *
     * @param selection the input buffer, which is modified as a side effect
     * @return an iterator over the results of the expression
     * @throws XPathException if evaluation of the expression fails
     */
    private SequenceIterator getSelectedItems(StringBuilder selection, Predicate<Tokenizer> finished) throws XPathException {
        XPathExpression expr = getExpression(selection, finished);
        return evaluateExpression(expr, currentDoc);
    }

    private List<NodeInfo> listOfSelectedItems(StringBuilder selection, Predicate<Tokenizer> terminator) throws XPathException {
        // Gather all the items first, then delete them. See bug 5106
        List<NodeInfo> nodes = new ArrayList<>();
        Item node;
        SequenceIterator iter = getSelectedItems(selection, terminator);
        while ((node = iter.next()) != null) {
            nodes.add((NodeInfo) node);
        }
        return nodes;
    }

    private XPathExpression getExpression(StringBuilder selection, Predicate<Tokenizer> terminator) throws XPathException {
        XPathEvaluator evaluator = new XPathEvaluator(config);
        evaluator.setStaticContext(env);
        for (StructuredQName var : variables.keySet()) {
            env.declareVariable(var);
        }
        XPathParser scanner = config.newExpressionParser("XP", false, env);
        scanner.parse(selection.toString(), 0, terminator, env);
        int endPoint = scanner.getOffsetAfterParsing();
        XPathExpression expr = evaluator.createExpression(selection.substring(0, endPoint));
        selection.replace(0, endPoint, "");
        return expr;
    }

    private XQueryExpression getQuery(StringBuilder query) throws XPathException {
        StaticQueryContext sqc = config.makeStaticQueryContext(true);
        for (StructuredQName var : variables.keySet()) {
            sqc.declareGlobalVariable(var, SequenceType.ANY_SEQUENCE, variables.get(var), false);
        }
        if (typed) {
            sqc.setSchemaAware(true);
        }
        Iterator<String> prefixes = env.iteratePrefixes();
        while (prefixes.hasNext()) {
            String prefix = prefixes.next();
            sqc.declareNamespace(prefix, env.getURIForPrefix(prefix, true));
        }
        return sqc.compileQuery(query.toString());
    }

    private SequenceIterator evaluateExpression(XPathExpression expr, Item contextItem) throws XPathException {
        XPathDynamicContext context = expr.createDynamicContext(contextItem);
        for (Map.Entry<StructuredQName, GroundedValue> var : variables.entrySet()) {
            XPathVariable v = env.getExternalVariable(var.getKey());
            context.setVariable(v, var.getValue());
        }
        return expr.iterate(context);
    }

    private SequenceIterator evaluateQuery(XQueryExpression expr, Item contextItem) throws XPathException {
        DynamicQueryContext context = new DynamicQueryContext(config);
        context.setContextValue(contextItem);
        return expr.iterator(context);
    }

    private StructuredQName getQName(String in) throws XPathException {
        return StructuredQName.fromLexicalQName(in, false, StructuredQName.QUPL, env.getNamespaceResolver());
    }

    private void needCurrentDoc() throws XPathException {
        if (currentDoc == null) {
            throw new XPathException("No source document available");
        }
    }

    private void saveCurrentDoc() throws XPathException {
        Builder builder = new LinkedTreeBuilder(config.makePipelineConfiguration(), Durability.MUTABLE);
        currentDoc.copy(builder, CopyOptions.ALL_NAMESPACES, Loc.NONE);
        final DocumentImpl copy = (DocumentImpl) builder.getCurrentRoot();
        undoBuffer.add(currentDoc);
        currentDoc = copy;
        if (undoBuffer.size() > 20) {
            undoBuffer.remove(0);
        }
    }

    private void copy(StringBuilder buffer) throws XPathException {
        needCurrentDoc();
        SequenceIterator iter = getSelectedItems(buffer, Tokenizer.END_OF_INPUT);
        Builder builder = new LinkedTreeBuilder(config.makePipelineConfiguration(), Durability.MUTABLE);
        builder.open();
        builder.startDocument(0);
        for (Item item; (item = iter.next()) != null; ) {
            if (item instanceof NodeInfo) {
                ((NodeInfo) item).copy(builder, CopyOptions.ALL_NAMESPACES, Loc.NONE);
            } else {
                throw new XPathException("Selected item is not a node");
            }
        }
        currentDoc = (DocumentImpl) builder.getCurrentRoot();
        unsaved = true;
    }

    private void dropElementIndexes() {
        currentDoc.resetIndexes();
    }

    private void delete(StringBuilder buffer) throws XPathException {
        needCurrentDoc();
        saveCurrentDoc();
        // Gather all the items first, then delete them. See bug 5106
        List<NodeInfo> nodes = listOfSelectedItems(buffer, Tokenizer.END_OF_INPUT);
        // Drop element indexes
        dropElementIndexes();
        for (Item item : nodes) {
            if (item instanceof MutableNodeInfo) {
                ((MutableNodeInfo) item).delete();
                unsaved = true;
            } else if (item instanceof NamespaceNode) {
                NodeInfo parent = ((NamespaceNode) item).getParent();
                if (parent instanceof MutableNodeInfo) {
                    try {
                        ((MutableNodeInfo) parent).removeNamespace(((NamespaceNode)item).getLocalPart());
                    } catch (Exception e) {
                        throw new XPathException("Cannot remove namespace: " + e.getMessage());
                    }
                }
                unsaved = true;
            } else {
                throw new XPathException("Selected item is not a mutable node");
            }
        }
    }

    private final static String[] keywords = new String[] {
        "ancestor::", "ancestor-or-self::", "array", "attribute", "cast as", "castable as", "child::",
        "comment()", "descendant::", "descendant-or-self::", "document-node()", "element()", "else",
        "empty-sequence()", "every", "except", "following::", "following-sibling::", "function",
        "instance of", "intersect", "item()", "namespace::", "namespace-node()", "node()", "parent::",
        "preceding::", "preceding-sibling::", "processing-instruction()", "return", "satisfies",
        "schema-attribute", "schema-element", "self::", "some", "text()", "then", "treat as",
        "union"    
    };

    private void load(StringBuilder source) throws XPathException {
        String fileName = source.toString();
        fileName = fileName.replaceFirst("^~", System.getProperty("user.home"));
        StreamSource ss = new StreamSource(new File(fileName));
        ParseOptions options = new ParseOptions()
                .withModel(TreeModel.LINKED_TREE)
                .withLineNumbering(true);
        currentDoc = (DocumentImpl) config.buildDocumentTree(ss, options).getRootNode();
        typed = false;
        Set<String> names = new HashSet<>();
        NodeInfo element;
        SequenceIterator allElements = currentDoc.iterateDescendantAxis(NodeKindType.ELEMENT);
        while ((element = (NodeInfo)allElements.next()) != null) {
            names.add(element.getLocalPart());
            NodeInfo att;
            SequenceIterator allAtts = element.iterateAttributeAxis(AnyGNode.TEST);
            while ((att = (NodeInfo)allAtts.next()) != null) {
                names.add("@" + att.getLocalPart());
            }
        }
        List<String> sortedNames = new ArrayList<>(names);
        sortedNames.addAll(Arrays.asList(keywords));
        Collections.sort(sortedNames);
        talker.setAutoCompletion(sortedNames);
    }

    private void call(StringBuilder source) throws XPathException {
        try {
            InputStream is = new FileInputStream(source.toString());
            DefaultTalker talker = new DefaultTalker(is, new PrintStream(sysOut));
            executeCommands(talker, false);
        } catch (FileNotFoundException e) {
            throw new XPathException("Script not found: " + e.getMessage());
        }
    }

    private void namespace(StringBuilder buffer) throws XPathException {
        int ws = buffer.indexOf(" ");
        if (ws < 0) {
            throw new XPathException("No namespace prefix supplied");
        }
        String prefix = buffer.substring(0, ws).trim();
        String uri = buffer.substring(ws).trim();
        env.declareNamespace(prefix, NamespaceUri.of(uri));
    }

    private void rename(StringBuilder buffer) throws XPathException {
        needCurrentDoc();
        saveCurrentDoc();
        List<NodeInfo> nodes = listOfSelectedItems(buffer, Tokenizer.finishOnKeyword("as"));
        dropElementIndexes();
        buffer.replace(0, 3, "");
        XPathExpression renamer = getExpression(buffer, Tokenizer.END_OF_INPUT);
        for (NodeInfo item : nodes) {
            if (item instanceof MutableNodeInfo) {
                Item newName = evaluateExpression(renamer, item).next();
                StructuredQName newQName;
                if (newName instanceof QNameValue) {
                    newQName = ((QNameValue) newName).getStructuredQName();
                } else if (newName instanceof AtomicValue) {
                    newQName = getQName(newName.getStringValue());
                } else {
                    throw new XPathException("New name must evaluate to a string or QName");
                }
                ((MutableNodeInfo) item).rename(new FingerprintedQName(newQName, config.getNamePool()), true);
            } else {
                throw new XPathException("Selected item is not a renameable node");
            }
        }
    }

    private void replace(StringBuilder buffer) throws XPathException {
        needCurrentDoc();
        saveCurrentDoc();
        List<NodeInfo> nodes = listOfSelectedItems(buffer, Tokenizer.finishOnKeyword("with"));
        dropElementIndexes();
        buffer.replace(0, 5, "");
        XQueryExpression replacement = getQuery(buffer);
        for (NodeInfo item : nodes) {
            if (item instanceof MutableNodeInfo target) {
                GroundedValue newValue = SequenceTool.toGroundedValue(evaluateQuery(replacement, item));
                if (newValue instanceof AtomicValue) {
                    Orphan orphan = new Orphan(config);
                    orphan.setNodeKind(Type.TEXT);
                    orphan.setStringValue(((AtomicValue)newValue).getUnicodeStringValue());
                    newValue = orphan;
                }
                switch (target.getNodeKind()) {
                    case Type.DOCUMENT:
                        throw new XPathException("Cannot replace a document node");
                    case Type.ELEMENT:
                    case Type.COMMENT:
                    case Type.TEXT:
                    case Type.PROCESSING_INSTRUCTION:

                        List<NodeInfo> newChildren = new ArrayList<>();
                        for (Item it : newValue.asIterable()) {
                            if (it instanceof NodeInfo) {
                                switch (((NodeInfo) it).getNodeKind()) {
                                    case Type.ATTRIBUTE ->
                                            throw new XPathException("Cannot replace non-attribute with attribute");
                                    case Type.NAMESPACE ->
                                            throw new XPathException("Cannot replace non-namespace node with namespace node");
                                    case Type.DOCUMENT -> {
                                        for (NodeInfo kid : ((NodeInfo) it).children()) {
                                            newChildren.add(kid);
                                        }
                                    }
                                    default -> newChildren.add((NodeInfo) it);
                                }
                            } else if (it instanceof AtomicValue) {
                                Orphan orphan = new Orphan(config);
                                orphan.setNodeKind(Type.TEXT);
                                orphan.setStringValue(it.getUnicodeStringValue());
                                newChildren.add(orphan);
                            }
                        }
                        if (!newChildren.isEmpty()) {
                            NodeInfo[] childArray = newChildren.toArray(new NodeInfo[0]);
                            target.replace(childArray, true);
                        }
                        break;
                    case Type.ATTRIBUTE:
                        ((MutableNodeInfo) target.getParent()).removeAttribute(target);
                        if (newValue.getLength() > 0) {
                            if (newValue.getLength() == 1 &&
                                    newValue.itemAt(0) instanceof NodeInfo att &&
                                    ((NodeInfo) newValue.itemAt(0)).getNodeKind() == Type.ATTRIBUTE) {
                                ((MutableNodeInfo) target.getParent()).addAttribute(
                                        NameOfNode.makeName(att), (SimpleType) att.getSchemaType(), att.getStringValue(), 0, true);
                            } else {
                                throw new XPathException("Replacement for an attribute must be an attribute");
                            }
                        }
                    case Type.NAMESPACE:
                    default:
                        throw new XPathException("Cannot replace a namespace node");

                }
            } else {
                throw new XPathException("Selected item is not a mutable node");
            }
        }
        unsaved = true;
    }

    private void undo(StringBuilder buffer) throws XPathException {
        int len= undoBuffer.size();
        if (len > 0) {
            currentDoc = undoBuffer.remove(len - 1);
        } else {
            throw new XPathException("Nothing to undo");
        }
    }

    private void update(StringBuilder buffer, String where) throws XPathException {
        needCurrentDoc();
        saveCurrentDoc();
        List<NodeInfo> nodes = listOfSelectedItems(buffer, Tokenizer.finishOnKeyword("with"));
        dropElementIndexes();
        buffer.replace(0, "with".length(), "");
        XQueryExpression newContent = getQuery(buffer);
        for (NodeInfo item : nodes) {
            if (item instanceof MutableNodeInfo target) {
                GroundedValue newValue = SequenceTool.toGroundedValue(evaluateQuery(newContent, item));
                if (newValue instanceof AtomicValue && where.equals("content")) {
                    target.replaceStringValue(((AtomicValue) newValue).getUnicodeStringValue());
                } else {
                    List<NodeInfo> replacement = new ArrayList<>();
                    List<NodeInfo> replacementAtts = new ArrayList<>();
                    for (Item it : newValue.asIterable()) {
                        if (it instanceof NodeInfo) {
                            switch (((NodeInfo) it).getNodeKind()) {
                                case Type.ATTRIBUTE -> replacementAtts.add((NodeInfo) it);
                                case Type.NAMESPACE -> throw new XPathException("Cannot replace namespace nodes");
                                case Type.DOCUMENT -> {
                                    for (NodeInfo kid : ((NodeInfo) it).children()) {
                                        replacement.add(kid);
                                    }
                                }
                                default -> replacement.add((NodeInfo) it);
                            }
                        } else if (it instanceof AtomicValue) {
                            Orphan orphan = new Orphan(config);
                            orphan.setNodeKind(Type.TEXT);
                            orphan.setStringValue(it.getUnicodeStringValue());
                            replacement.add(orphan);
                        }
                    }
                    if (!replacementAtts.isEmpty() && !(where.equals("prefix") || where.equals("update"))) {
                        throw new XPathException("Cannot supply attributes for " + where + " command (use 'prefix')");
                    }
                    if (!replacement.isEmpty()) {
                        // Bug 6489 - copy the replacement elements
                        NodeInfo[] childArray = copyNodes(config, replacement);
                        switch (where) {
                            case "content" -> target.replace(childArray, true);
                            case "precede" -> target.insertSiblings(childArray, true, true);
                            case "follow" -> target.insertSiblings(childArray, false, true);
                            case "prefix" -> target.insertChildren(childArray, true, true);
                            default /* , "suffix" */ -> target.insertChildren(childArray, false, true);
                        }

                    }

                    for (NodeInfo att : replacementAtts) {
                        final NodeName attName = NameOfNode.makeName(att);
                        NodeInfo existing = (NodeInfo)target.iterateAttributeAxis(
                                NamedXNodeType.make(Type.ATTRIBUTE, att.getNamespaceUri(), att.getLocalPart(), config)).next();
                        if (existing != null) {
                            target.removeAttribute(existing);
                        }
                        target.addAttribute(attName, BuiltInAtomicType.UNTYPED_ATOMIC, att.getStringValue(), 0, true);
                    }
                }
            } else {
                throw new XPathException("Selected item is not a mutable node");
            }
        }
        unsaved = true;
    }

    private NodeInfo[] copyNodes(Configuration config, Iterable<NodeInfo> supplied) throws XPathException {
        // Make copies of the inserted nodes, and convert them to use the same object model implementation
        // as the node into which they are being inserted

        PipelineConfiguration pipe = config.makePipelineConfiguration();
        LinkedTreeBuilder builder = new LinkedTreeBuilder(pipe, Durability.MUTABLE);
        builder.setAllocateSequenceNumbers(false);


        TreeReceiver out = new TreeReceiver(builder);
        out.setPipelineConfiguration(pipe);

        out.open();
        out.startDocument(ReceiverOption.NONE);

        int copyNs = ReceiverOption.ALL_NAMESPACES;
        for (NodeInfo n : supplied) {
            out.append(n, Loc.NONE, copyNs);
        }

        out.endDocument();
        out.close();

        NodeInfo root = builder.getCurrentRoot();
        SequenceIterator top = root.iterateChildAxis(AnyGNode.TEST);
        SequenceExtent extent = SequenceExtent.from(top);
        int len = extent.getLength();
        NodeInfo[] array = new NodeInfo[len];
        for (int i = 0; i < len; i++) {
            array[i] = (NodeInfo) extent.itemAt(i);
        }
        return array;
    }

    private void save(StringBuilder buffer) throws XPathException {
        needCurrentDoc();
        Whitespace.Tokenizer tokens = new Whitespace.Tokenizer(StringView.tidy(buffer.toString()));
        StringValue fileName = tokens.next();
        if (fileName == null) {
            throw new XPathException("No file name supplied");
        }
        File dest = new File(fileName.getStringValue());
        if (dest.exists()) {
            while (true) {
                String answer = talker.exchange("Overwrite existing file? (Y|N)");
                if (answer.equalsIgnoreCase("y")) {
                    break;
                } else if (answer.equalsIgnoreCase("n")) {
                    return;
                }
                //continue;
            }
        }
        StreamResult out = new StreamResult(dest);
        SerializationProperties props = new SerializationProperties();
        StringValue prop;
        while ((prop = tokens.next()) != null) {
            try {
                String[] parts = prop.getStringValue().split("=");
                props.setProperty(parts[0].trim(), parts[1].trim());
            } catch (Exception e) {
                sysOut.println("Unrecognized output property '" + prop);
            }
        }
        Receiver s = config.getSerializerFactory().getReceiver(out, props);
        s.open();
        currentDoc.copy(s, CopyOptions.ALL_NAMESPACES, Loc.NONE);
        s.close();
        sysOut.println("Written to " + new File(fileName.getStringValue()).getAbsolutePath());
        unsaved = false;
    }

    private void schema(StringBuilder buffer) throws XPathException {
        if (!config.isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION)) {
            throw new XPathException("Schema processing is not supported in this Saxon configuration");
        }
        String fileName = buffer.toString();
        fileName = fileName.replaceFirst("^~", System.getProperty("user.home"));
        currentSchema = config.loadSchema(new File(fileName).getAbsoluteFile().toURI().toString());
    }

    private void set(StringBuilder buffer) throws XPathException {
        int ws = buffer.indexOf("=");
        if (ws < 0 || ws == buffer.length() - 1) {
            throw new XPathException("Format: set name = value");
        }
        String varName = buffer.substring(0, ws).trim();
        if (varName.startsWith("$")) {
            // forgive use of a leading '$'
            varName = varName.substring(1);
        }
        if (varName.endsWith(":")) {
            // forgive use of `:=` in place of `=`
            varName = varName.substring(0, varName.length() - 1).trim();
        }
        DocumentImpl saved = currentDoc;

        GroundedValue value = SequenceTool.toGroundedValue(
                getSelectedItems(new StringBuilder(buffer.substring(ws + 1)), Tokenizer.END_OF_INPUT));
        if (varName.equals(".")) {
            saveCurrentDoc();
            if (value.getLength() == 1 && value.itemAt(0) instanceof DocumentImpl) {
                currentDoc = (DocumentImpl) value.itemAt(0);
            } else {
                try {
                    Builder builder = new LinkedTreeBuilder(config.makePipelineConfiguration(), Durability.MUTABLE);
                    ComplexContentOutputter cco = new ComplexContentOutputter(builder);
                    cco.open();
                    cco.startDocument(0);
                    for (Item it : value.asIterable()) {
                        cco.append(it);
                    }
                    cco.endDocument();
                    cco.close();
                    currentDoc = (DocumentImpl) builder.getCurrentRoot();
                } catch (XPathException e) {
                    throw new XPathException("Cannot save the value as a document (" + e.getMessage() + ")");
                }
            }

        } else {
            StructuredQName name = getQName(varName);
            variables.put(name, value);
            currentDoc = saved;
        }
    }

    private void validate(StringBuilder buffer) throws XPathException {
        if (!config.isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION)) {
            throw new XPathException("Schema processing is not supported in this Saxon configuration");
        }
        if (currentSchema == null) {
            currentSchema = config.emptySchema();
        }
        needCurrentDoc();
        saveCurrentDoc();
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        Builder builder = new LinkedTreeBuilder(pipe, Durability.MUTABLE);
        builder.open();
        ParseOptions options = new ParseOptions().withSchemaValidationMode(Validation.STRICT);
        Receiver val = currentSchema.getDocumentValidator(builder, currentDoc.getSystemId(), options, Loc.NONE);
        currentDoc.copy(val, CopyOptions.ALL_NAMESPACES, Loc.NONE);
        builder.close();
        currentDoc = (DocumentImpl)builder.getCurrentRoot();
        unsaved = true;
        typed = true;
    }

    private void list(StringBuilder buffer) throws XPathException {
        needCurrentDoc();
        SequenceIterator iter = getSelectedItems(buffer, Tokenizer.END_OF_INPUT);
        GroundedValue value = SequenceTool.toGroundedValue(iter);
        int size = value.getLength();
        if (size != 1) {
            sysOut.println("Found " + size + " items");
        }

        for (Item item : value.asIterable()) {
            if (item instanceof NodeInfo) {
                int lineNumber = ((NodeInfo) item).getLineNumber();
                String prefix = lineNumber >= 0 ? ("Line " + lineNumber + ": ") : "";
                sysOut.println(prefix + Navigator.getPath(((NodeInfo) item)));
            } else {
                sysOut.println(item.getUnicodeStringValue());
            }
        }
    }

    private void show(StringBuilder buffer) throws XPathException {
        needCurrentDoc();
        if (buffer.toString().trim().isEmpty()) {
            buffer = new StringBuilder(".");
        }
        SequenceIterator iter = getSelectedItems(buffer, Tokenizer.END_OF_INPUT);
        GroundedValue value = SequenceTool.toGroundedValue(iter);
        int size = value.getLength();
        if (size != 1) {
            sysOut.println("Found " + size + " items");
        }
        for (Item item : value.asIterable()) {
            if (item instanceof NodeInfo) {
                sysOut.println(QueryResult.serialize((NodeInfo) item));
            } else if (item instanceof AtomicValue) {
                sysOut.println(item.getUnicodeStringValue());
            } else {
                StringWriter sw = new StringWriter();
                SerializationProperties props = new SerializationProperties();
                props.setProperty("method", "adaptive");
                props.setProperty("indent", "yes");
                PipelineConfiguration pipe = config.makePipelineConfiguration();
                pipe.setHostLanguage(HostLanguage.XPATH, 40);
                Receiver r = config.getSerializerFactory().getReceiver(new StreamResult(sw), props, pipe);
                r.append(item);
                sysOut.println(sw);
            }
        }
    }

    private void transform(StringBuilder buffer) throws XPathException {
        try {
            needCurrentDoc();
            saveCurrentDoc();
            String fileName = buffer.toString();
            fileName = fileName.replaceFirst("^~", System.getProperty("user.home"));
            StreamSource ss = new StreamSource(new File(fileName));
            Templates templates = new TransformerFactoryImpl(config).newTemplates(ss);
            Transformer transformer = templates.newTransformer();
            Builder result = new LinkedTreeBuilder(config.makePipelineConfiguration(), Durability.MUTABLE);
            result.open();
            transformer.transform(currentDoc.asActiveSource(), result);
            result.close();
            currentDoc = (DocumentImpl)result.getCurrentRoot();
        } catch (TransformerException e) {
            throw XPathException.makeXPathException(e);
        }
    }


}

