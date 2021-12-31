////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.Builder;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.gizmo.DefaultTalker;
import net.sf.saxon.gizmo.JLine2Talker;
import net.sf.saxon.gizmo.Talker;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.sxpath.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.*;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.*;

/**
 * A Gizmo is a simple tool for performing a particular easily specified transformation on an XML
 * document, for example deleting or renaming all the elements with a particular name. The class
 * offers a command-line interface.
 */

public class Gizmo {

    private Configuration config;
    private IndependentContext env;
    private DocumentImpl currentDoc;
    private boolean unsaved = false;
    private Map<StructuredQName, Sequence> variables = new HashMap<>();
    private Map<String, SubCommand> subCommands = new HashMap<>();
    private Talker talker;
    private boolean typed = false;
    private List<DocumentImpl> undoBuffer = new LinkedList<>();

    private static class SubCommand {
        String name;
        String helpText;
        Action action;
    }

    @FunctionalInterface
    private interface Action {
        void perform(StringBuffer subCommand) throws XPathException;
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
                   (cmd) -> list(new StringBuffer("distinct-values(//*!('/'||string-join(ancestor-or-self::*!name(),'/')))")));
        addCommand("precede",
                   "precede {expression} with {query} -- add result of query before each selected nodes",
                   (cmd) -> update(cmd, "precede"));
        addCommand("prefix",
                   "prefix {expression} with {query} -- add result of query as first child of each selected nodes",
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
                   "set {variable} {expression} -- set variable to value of expression",
                   this::set);
        addCommand("show",
                   "show {expression} -- display content of all selected nodes",
                   this::show);
        addCommand("strip",
                   "strip -- delete whitespace text nodes",
                   (cmd -> this.delete(new StringBuffer("//text()[not(normalize-space())]"))));
        addCommand("suffix",
                   "suffix {expression} with {query} -- add result of query as last child of each selected nodes",
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
        config.setConfigurationProperty(Feature.ALLOW_SYNTAX_EXTENSIONS, true);
        env = new IndependentContext(config);
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
                load(new StringBuffer(source));
            } catch (XPathException e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
        } else {
            try {
                String dummy = "<dummy/>";
                StreamSource ss = new StreamSource(new StringReader(dummy));
                ParseOptions options = new ParseOptions();
                options.setModel(TreeModel.LINKED_TREE);
                options.setLineNumbering(true);
                currentDoc = (DocumentImpl) config.buildDocumentTree(ss, options).getRootNode();
                typed = false;
            } catch (XPathException e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
        }

        env.declareNamespace("xml", NamespaceConstant.XML);
        env.declareNamespace("xsl", NamespaceConstant.XSLT);
        env.declareNamespace("saxon", NamespaceConstant.SAXON);
        env.declareNamespace("xs", NamespaceConstant.SCHEMA);
        env.declareNamespace("xsi", NamespaceConstant.SCHEMA_INSTANCE);
        env.declareNamespace("fn", NamespaceConstant.FN);
        env.declareNamespace("math", NamespaceConstant.MATH);
        env.declareNamespace("map", NamespaceConstant.MAP_FUNCTIONS);
        env.declareNamespace("array", NamespaceConstant.ARRAY_FUNCTIONS);
        env.declareNamespace("", "");

        env.setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.ANY_NAMESPACE);

        System.out.println("Saxon Gizmo " + Version.getProductVersion());
        executeCommands(talker, interactive);
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
                return new DefaultTalker(new FileInputStream(new File(script)), System.out);
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
                    if (unsaved && !remainder.equals("now")) {
                        while (true) {
                            String answer = talker.exchange("Quit without saving? (Y|N)");
                            if (answer.equalsIgnoreCase("y")) {
                                break;
                            } else if (answer.equalsIgnoreCase("n")) {
                                quit = false;
                                break;
                            } else {
                                //continue;
                            }
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
                        System.out.println("Unknown command " + keyword + " (Use 'quit' to exit)");
                        help(new StringBuffer("?"));
                    } else {
                        throw new XPathException("\"Unknown command \" + cmd + \"");
                    }
                } else {
                    cmd.action.perform(new StringBuffer(remainder));
                }

            } catch (XPathException e) {
                System.out.println(e.getErrorCodeLocalPart() + ": " + e.getMessage());
                if (interactive) {
                    //System.out.print("|>");
                } else {
                    System.exit(2);
                }
            }
        }
    }

    private void help(StringBuffer command) {
        String cmd = command == null ? null : command.toString().trim();
        if (cmd == null || cmd.isEmpty() || cmd.equals("help") || cmd.equals("?")) {
            System.out.println("Commands available:");
            List<String> commands = new ArrayList<>(subCommands.keySet());
            commands.sort(null);
            for (String c : commands) {
                System.out.println("  " + subCommands.get(c).helpText);
            }
        } else {
            SubCommand entry = subCommands.get(cmd);
            if (entry == null) {
                help(null);
            } else {
                System.out.println(entry.helpText);
            }
        }
    }

    /**
     * Read an XPath expression from a supplied input string, execute the expression,
     * and return an iterator over the result. As a side-effect, modify the supplied
     * StringBuffer so it contains whatever remains after parsing the expression.
     *
     * @param selection the input buffer, which is modified as a side-effect
     * @return an iterator over the results of the expression
     * @throws XPathException if evaluation of the expression fails
     */
    private SequenceIterator getSelectedItems(StringBuffer selection, int terminator) throws XPathException {
        XPathExpression expr = getExpression(selection, terminator);
        return evaluateExpression(expr, currentDoc);
    }

    private XPathExpression getExpression(StringBuffer selection, int terminator) throws XPathException {
        XPathEvaluator evaluator = new XPathEvaluator(config);
        evaluator.setStaticContext(env);
        for (StructuredQName var : variables.keySet()) {
            env.declareVariable(var);
        }
        XPathParser scanner = config.newExpressionParser("XP", false, 31);
        scanner.parse(selection.toString(), 0, terminator, env);
        int endPoint = scanner.getTokenizer().currentTokenStartOffset;
        XPathExpression expr = evaluator.createExpression(selection.substring(0, endPoint));
        selection.replace(0, endPoint, "");
        return expr;
    }

    private XQueryExpression getQuery(StringBuffer query) throws XPathException {
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
        for (Map.Entry<StructuredQName, Sequence> var : variables.entrySet()) {
            XPathVariable v = env.getExternalVariable(var.getKey());
            context.setVariable(v, var.getValue());
        }
        return expr.iterate(context);
    }

    private SequenceIterator evaluateQuery(XQueryExpression expr, Item contextItem) throws XPathException {
        DynamicQueryContext context = new DynamicQueryContext(config);
        context.setContextItem(contextItem);
        return expr.iterator(context);
    }

    private StructuredQName getQName(String in) throws XPathException {
        return StructuredQName.fromLexicalQName(in, false, true, env.getNamespaceResolver());
    }

    private void needCurrentDoc() throws XPathException {
        if (currentDoc == null) {
            throw new XPathException("No source document available");
        }
    }

    private void saveCurrentDoc() throws XPathException {
        Builder builder = new LinkedTreeBuilder(config.makePipelineConfiguration());
        currentDoc.copy(builder, CopyOptions.ALL_NAMESPACES, Loc.NONE);
        final DocumentImpl copy = (DocumentImpl) builder.getCurrentRoot();
        undoBuffer.add(currentDoc);
        currentDoc = copy;
        if (undoBuffer.size() > 20) {
            undoBuffer.remove(0);
        }
    }

    private void copy(StringBuffer buffer) throws XPathException {
        needCurrentDoc();
        SequenceIterator iter = getSelectedItems(buffer, Token.EOF);
        Builder builder = new LinkedTreeBuilder(config.makePipelineConfiguration());
        builder.open();
        builder.startDocument(0);
        Item item;
        while ((item = iter.next()) != null) {
            if (item instanceof NodeInfo) {
                ((NodeInfo) item).copy(builder, CopyOptions.ALL_NAMESPACES, Loc.NONE);
            } else {
                throw new XPathException("Selected item is not a node");
            }
        }
        currentDoc = (DocumentImpl) builder.getCurrentRoot();
        unsaved = true;
    }

    private void delete(StringBuffer buffer) throws XPathException {
        needCurrentDoc();
        saveCurrentDoc();
        GroundedValue all = getSelectedItems(buffer, Token.EOF).materialize();
        for (Item item : all.asIterable()) {
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

    private static String[] keywords = new String[] {
        "ancestor::", "ancestor-or-self::", "array", "attribute", "cast as", "castable as", "child::",
        "comment()", "descendant::", "descendant-or-self::", "document-node()", "element()", "else",
        "empty-sequence()", "every", "except", "following::", "following-sibling::", "function",
        "instance of", "intersect", "item()", "namespace::", "namespace-node()", "node()", "parent::",
        "preceding::", "preceding-sibling::", "processing-instruction()", "return", "satisfies",
        "schema-attribute", "schema-element", "self::", "some", "text()", "then", "treat as",
        "union"    
    };

    private void load(StringBuffer source) throws XPathException {
        String fileName = source.toString();
        fileName = fileName.replaceFirst("^~", System.getProperty("user.home"));
        StreamSource ss = new StreamSource(new File(fileName));
        ParseOptions options = new ParseOptions();
        options.setModel(TreeModel.LINKED_TREE);
        options.setLineNumbering(true);
        currentDoc = (DocumentImpl) config.buildDocumentTree(ss, options).getRootNode();
        typed = false;
        Set<String> names = new HashSet<>();
        NodeInfo element;
        AxisIterator allElements = currentDoc.iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
        while ((element = allElements.next()) != null) {
            names.add(element.getLocalPart());
            NodeInfo att;
            AxisIterator allAtts = element.iterateAxis(AxisInfo.ATTRIBUTE);
            while ((att = allAtts.next()) != null) {
                names.add("@" + att.getLocalPart());
            }
        }
        List<String> sortedNames = new ArrayList<>(names);
        sortedNames.addAll(Arrays.asList(keywords));
        Collections.sort(sortedNames);
        talker.setAutoCompletion(sortedNames);
    }

    private void call(StringBuffer source) throws XPathException {
        try {
            InputStream is = new FileInputStream(source.toString());
            DefaultTalker talker = new DefaultTalker(is, new PrintStream(System.out));
            executeCommands(talker, false);
        } catch (FileNotFoundException e) {
            throw new XPathException("Script not found: " + e.getMessage());
        }
    }

    private void namespace(StringBuffer buffer) throws XPathException {
        int ws = buffer.indexOf(" ");
        if (ws < 0) {
            throw new XPathException("No namespace prefix supplied");
        }
        String prefix = buffer.substring(0, ws).trim();
        String uri = buffer.substring(ws).trim();
        env.declareNamespace(prefix, uri);
    }

    private void rename(StringBuffer buffer) throws XPathException {
        needCurrentDoc();
        saveCurrentDoc();
        SequenceIterator iter = getSelectedItems(buffer, Token.AS);
        buffer.replace(0, 3, "");
        XPathExpression renamer = getExpression(buffer, Token.EOF);
        Item item;
        while ((item = iter.next()) != null) {
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
                ((MutableNodeInfo) item).rename(new FingerprintedQName(newQName, config.getNamePool()));
            } else {
                throw new XPathException("Selected item is not a renameable node");
            }
        }
    }

    private void replace(StringBuffer buffer) throws XPathException {
        needCurrentDoc();
        saveCurrentDoc();
        SequenceIterator iter = getSelectedItems(buffer, Token.WITH);
        buffer.replace(0, 5, "");
        XQueryExpression replacement = getQuery(buffer);
        Item item;
        while ((item = iter.next()) != null) {
            if (item instanceof MutableNodeInfo) {
                MutableNodeInfo target = (MutableNodeInfo) item;
                GroundedValue newValue = evaluateQuery(replacement, item).materialize();
                if (newValue instanceof AtomicValue) {
                    Orphan orphan = new Orphan(config);
                    orphan.setNodeKind(Type.TEXT);
                    orphan.setStringValue(newValue.getStringValue());
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
                                switch (((NodeInfo)it).getNodeKind()) {
                                    case Type.ATTRIBUTE:
                                        throw new XPathException("Cannot replace non-attribute with attribute");
                                    case Type.NAMESPACE:
                                        throw new XPathException("Cannot replace non-namespace node with namespace node");
                                    case Type.DOCUMENT:
                                        for (NodeInfo kid : ((NodeInfo)it).children()) {
                                            newChildren.add(kid);
                                        }
                                        break;
                                    default:
                                        newChildren.add((NodeInfo) it);
                                }
                            } else if (it instanceof AtomicValue) {
                                Orphan orphan = new Orphan(config);
                                orphan.setNodeKind(Type.TEXT);
                                orphan.setStringValue(it.getStringValue());
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
                        if (newValue.getLength() == 0) {
                            // no further action
                        } else if (newValue.getLength() == 1 &&
                                newValue.itemAt(0) instanceof NodeInfo &&
                                ((NodeInfo)newValue.itemAt(0)).getNodeKind() == Type.ATTRIBUTE) {
                            NodeInfo att = ((NodeInfo) newValue.itemAt(0));
                            ((MutableNodeInfo) target.getParent()).addAttribute(
                                    NameOfNode.makeName(att), (SimpleType)att.getSchemaType(), att.getStringValueCS(), 0);
                        } else {
                            throw new XPathException("Replacement for an attribute must be an attribute");
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

    private void undo(StringBuffer buffer) throws XPathException {
        int len= undoBuffer.size();
        if (len > 0) {
            currentDoc = undoBuffer.remove(len - 1);
        } else {
            throw new XPathException("Nothing to undo");
        }
    }

    private void update(StringBuffer buffer, String where) throws XPathException {
        needCurrentDoc();
        saveCurrentDoc();
        SequenceIterator iter = getSelectedItems(buffer, Token.WITH);
        buffer.replace(0, 5, "");
        XQueryExpression newContent = getQuery(buffer);
        Item item;
        while ((item = iter.next()) != null) {
            if (item instanceof MutableNodeInfo) {
                MutableNodeInfo target = (MutableNodeInfo) item;
                GroundedValue newValue = evaluateQuery(newContent, item).materialize();
                if (newValue instanceof AtomicValue && where.equals("content")) {
                    target.replaceStringValue(((AtomicValue) newValue).getStringValueCS());
                } else {
                    List<NodeInfo> replacement = new ArrayList<>();
                    List<NodeInfo> replacementAtts = new ArrayList<>();
                    for (Item it : newValue.asIterable()) {
                        if (it instanceof NodeInfo) {
                            switch (((NodeInfo) it).getNodeKind()) {
                                case Type.ATTRIBUTE:
                                    replacementAtts.add((NodeInfo) it);
                                    break;
                                case Type.NAMESPACE:
                                    throw new XPathException("Cannot replace namespace nodes");
                                case Type.DOCUMENT:
                                    for (NodeInfo kid : ((NodeInfo) it).children()) {
                                        replacement.add(kid);
                                    }
                                    break;
                                default:
                                    replacement.add((NodeInfo) it);

                            }
                        } else if (it instanceof AtomicValue) {
                            Orphan orphan = new Orphan(config);
                            orphan.setNodeKind(Type.TEXT);
                            orphan.setStringValue(it.getStringValue());
                            replacement.add(orphan);
                        }
                    }
                    if (!replacementAtts.isEmpty() && !(where.equals("prefix") || where.equals("update"))) {
                        throw new XPathException("Cannot supply attributes for " + where + " command (use 'prefix')");
                    }
                    if (!replacement.isEmpty()) {
                        NodeInfo[] childArray = replacement.toArray(new NodeInfo[0]);
                        switch (where) {
                            case "content":
                                target.replace(childArray, true);
                                break;
                            case "precede":
                                target.insertSiblings(childArray, true, true);
                                break;
                            case "follow":
                                target.insertSiblings(childArray, false, true);
                                break;
                            case "prefix":
                                target.insertChildren(childArray, true, true);
                                break;
                            case "suffix":
                            default:
                                target.insertChildren(childArray, false, true);
                        }

                    }

                    for (NodeInfo att : replacementAtts) {
                        final NodeName attName = NameOfNode.makeName(att);
                        NodeInfo existing = target.iterateAxis(AxisInfo.ATTRIBUTE,
                                                               new NameTest(Type.ATTRIBUTE, attName, config.getNamePool())).next();
                        if (existing != null) {
                            target.removeAttribute(existing);
                        }
                        target.addAttribute(attName, BuiltInAtomicType.UNTYPED_ATOMIC, att.getStringValue(), 0);
                    }
                }
            } else {
                throw new XPathException("Selected item is not a mutable node");
            }
        }
        unsaved = true;
    }


    private void save(StringBuffer buffer) throws XPathException {
        needCurrentDoc();
        Whitespace.Tokenizer tokens = new Whitespace.Tokenizer(buffer);
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
                } else {
                    //continue;
                }
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
                System.out.println("Unrecognized output property '" + prop);
            }
        }
        Receiver s = config.getSerializerFactory().getReceiver(out, props);
        s.open();
        currentDoc.copy(s, CopyOptions.ALL_NAMESPACES, Loc.NONE);
        s.close();
        System.out.println("Written to " + new File(fileName.getStringValue()).getAbsolutePath());
        unsaved = false;
    }

    private void schema(StringBuffer buffer) throws XPathException {
        if (!config.isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION)) {
            throw new XPathException("Schema processing is not supported in this Saxon configuration");
        }
        String fileName = buffer.toString();
        fileName = fileName.replaceFirst("^~", System.getProperty("user.home"));
        config.loadSchema(new File(fileName).getAbsoluteFile().toURI().toString());
    }

    private void set(StringBuffer buffer) throws XPathException {
        int ws = buffer.indexOf("=");
        if (ws < 0 || ws == buffer.length() - 1) {
            throw new XPathException("Format: set name = value");
        }
        String varName = buffer.substring(0, ws).trim();
        if (varName.startsWith("$")) {
            varName = varName.substring(1);
        }
        DocumentImpl saved = currentDoc;

        GroundedValue value = getSelectedItems(new StringBuffer(buffer.substring(ws + 1)), Token.EOF).materialize();
        if (varName.equals(".")) {
            saveCurrentDoc();
            if (value.getLength() == 1 && value.itemAt(0) instanceof DocumentImpl) {
                currentDoc = (DocumentImpl) value.itemAt(0);
            } else {
                try {
                    Builder builder = new LinkedTreeBuilder(config.makePipelineConfiguration());
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

    private void validate(StringBuffer buffer) throws XPathException {
        if (!config.isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION)) {
            throw new XPathException("Schema processing is not supported in this Saxon configuration");
        }
        needCurrentDoc();
        saveCurrentDoc();
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        Builder builder = new LinkedTreeBuilder(pipe);
        builder.open();
        ParseOptions options = new ParseOptions();
        options.setSchemaValidationMode(Validation.STRICT);
        Receiver val = config.getDocumentValidator(builder, currentDoc.getSystemId(), options, Loc.NONE);
        currentDoc.copy(val, CopyOptions.ALL_NAMESPACES, Loc.NONE);
        builder.close();
        currentDoc = (DocumentImpl)builder.getCurrentRoot();
        unsaved = true;
        typed = true;
    }

    private void list(StringBuffer buffer) throws XPathException {
        needCurrentDoc();
        SequenceIterator iter = getSelectedItems(buffer, Token.EOF);
        GroundedValue value = iter.materialize();
        int size = value.getLength();
        if (size != 1) {
            System.out.println("Found " + size + " items");
        }

        for (Item item : value.asIterable()) {
            if (item instanceof NodeInfo) {
                int lineNumber = ((NodeInfo) item).getLineNumber();
                String prefix = lineNumber >= 0 ? ("Line " + lineNumber + ": ") : "";
                System.out.println(prefix + Navigator.getPath(((NodeInfo) item)));
            } else {
                System.out.println(item.getStringValue());
            }
        }
    }

    private void show(StringBuffer buffer) throws XPathException {
        needCurrentDoc();
        if (buffer.toString().trim().isEmpty()) {
            buffer = new StringBuffer(".");
        }
        SequenceIterator iter = getSelectedItems(buffer, Token.EOF);
        GroundedValue value = iter.materialize();
        int size = value.getLength();
        if (size != 1) {
            System.out.println("Found " + size + " items");
        }
        for (Item item : value.asIterable()) {
            if (item instanceof NodeInfo) {
                System.out.println(QueryResult.serialize((NodeInfo) item));
            } else if (item instanceof AtomicValue) {
                System.out.println(item.getStringValue());
            } else {
                StringWriter sw = new StringWriter();
                SerializationProperties props = new SerializationProperties();
                props.setProperty("method", "adaptive");
                Receiver r = config.getSerializerFactory().getReceiver(new StreamResult(sw), props);
                r.append(item);
                System.out.println(sw.toString());
            }
        }
    }

    private void transform(StringBuffer buffer) throws XPathException {
        try {
            needCurrentDoc();
            saveCurrentDoc();
            String fileName = buffer.toString();
            fileName = fileName.replaceFirst("^~", System.getProperty("user.home"));
            StreamSource ss = new StreamSource(new File(fileName));
            Templates templates = new TransformerFactoryImpl(config).newTemplates(ss);
            Transformer transformer = templates.newTransformer();
            Builder result = new LinkedTreeBuilder(config.makePipelineConfiguration());
            result.open();
            transformer.transform(currentDoc, result);
            result.close();
            currentDoc = (DocumentImpl)result.getCurrentRoot();
        } catch (TransformerException e) {
            throw XPathException.makeXPathException(e);
        }
    }


}

