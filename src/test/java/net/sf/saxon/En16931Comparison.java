package net.sf.saxon;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Compares pinned, unchanged EN16931 XSLT in separate stock/fork JVMs.
 * Invoked by En16931ComparisonIT through the en16931-comparison Maven profile.
 * Uses only JDK APIs. No spreadsheet or additional Java library is required.
 */
public class En16931Comparison {
    private static final String COMMIT = "a519ba02a59e2775436428f57ee96899feb1da8c";
    private static final String RELEASE = "validation-1.3.16";
    private static final String STOCK_HASH = "258fb4788b8e1bd986f9aed14269669412da88c7bb289b747878d4353f6168aa";
    private static final Map<String, String> XSLT_HASHES = Map.of(
            "ubl", "39f9d282867f1a49e7708d9e29a53da89643e1ee56f10cec1ebcf1277595fcbd",
            "cii", "0b234dea2bbfee739b7761e607a992c17fab88773014ef56355b6158cfb1cc53");
    private static final Map<String, String> NS = Map.of(
            "ubl", "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
            "cbc", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
            "cac", "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
            "rsm", "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
            "ram", "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
            "udt", "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100",
            "svrl", "http://purl.oclc.org/dsdl/svrl");
    private static final XPath XPATH = newXPath();
    private final Path root;
    private final Path fixtures;
    private final Path output;
    private final Path cache;
    private final Path logs;
    private final Path reports;
    private final Path inputs;
    private final Path java;

    private record Asset(String name, String url, String sha256) { }
    private record InvoiceCase(String name, Path path, List<String> expectedFailures) { }
    private record Svrl(int firedRules, List<Map<String, Object>> failures,
                        List<Map<String, Object>> successfulReports) { }
    private record Line(String name, BigDecimal quantity, String unit, BigDecimal price,
                        BigDecimal baseQuantity, BigDecimal rate, BigDecimal amount) { }
    private record Tax(BigDecimal rate, BigDecimal basis, BigDecimal amount) { }
    private record Invoice(String id, String issueDate, String currency, List<Line> lines,
                           List<Tax> taxes, List<BigDecimal> totals, BigDecimal vat) {
        Map<String, Object> values() {
            return object("id", id, "issue_date", issueDate, "currency", currency,
                    "lines", lines.stream().map(l -> List.of(l.name, l.quantity, l.unit,
                            l.price, l.baseQuantity, l.rate, l.amount)).toList(),
                    "vat_breakdowns", taxes.stream().map(t -> List.of(t.rate, t.basis, t.amount)).toList(),
                    "totals", totals, "vat", vat);
        }
    }

    private En16931Comparison(Path root, Path output) throws Exception {
        this.root = root.toRealPath();
        this.output = output.toAbsolutePath().normalize();
        fixtures = this.root.resolve("src/test/resources/en16931");
        cache = this.output.resolve("cache");
        logs = this.output.resolve("logs");
        reports = this.output.resolve("svrl");
        inputs = this.output.resolve("invoices");
        for (Path path : List.of(cache, logs, reports, inputs)) {
            Files.createDirectories(path);
        }
        // Use the same JDK for the integration test and both engine subprocesses.
        java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
    }

    static void compare(Path root, Path output, Path forkJar) throws Exception {
        require(Runtime.version().feature() >= 25, "JDK 25 or newer is required.");
        new En16931Comparison(root, output).compare(forkJar);
    }

    private void compare(Path fork) throws Exception {
        // Remove a previous summary so a failed invocation cannot leave a stale PASS report.
        Files.deleteIfExists(output.resolve("summary.json"));
        Invoice invoice = baseline("ubl");
        require(invoice.equals(baseline("cii")), "UBL and CII baseline business values differ.");
        run(logs.resolve("java-version.log"), java, "-version");
        require(Files.isRegularFile(fork), "Fork JAR does not exist: " + fork);
        Path dependencies = cache.resolve("stock-dependencies");
        // Maven copies these separately from the project's test classpath.
        Path stock = dependencies.resolve("Saxon-HE-13.0.jar");
        verifyHash(stock, STOCK_HASH);
        require(!digest(fork).equals(digest(stock)), "The fork and stock JARs are identical.");
        // Select exact names so old cached versions cannot enter either engine's classpath.
        List<Path> support = List.of(dependencies.resolve("xmlresolver-6.0.23.jar"),
                dependencies.resolve("xmlresolver-6.0.23-data.jar"));
        Map<String, Path> engines = new LinkedHashMap<>();
        engines.put("stock", stock);
        engines.put("enhanced", fork);
        Map<String, String> classpaths = new LinkedHashMap<>();
        Map<String, Object> engineDetails = new LinkedHashMap<>();
        for (var entry : engines.entrySet()) {
            List<Path> jars = new ArrayList<>();
            jars.add(entry.getValue());
            jars.addAll(support);
            classpaths.put(entry.getKey(), jars.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
            engineDetails.put(entry.getKey(), object("jar", entry.getValue().getFileName().toString(),
                    "sha256", digest(entry.getValue())));
        }
        Map<String, Object> supportDetails = new LinkedHashMap<>();
        for (Path jar : support) {
            supportDetails.put(jar.getFileName().toString(), digest(jar));
        }
        Path upstream = assets();
        Map<String, Path> schemas = Map.of(
                "ubl", cache.resolve("xsd/maindoc/UBL-Invoice-2.1.xsd"),
                "cii", upstream.resolve("cii/schema/D16B SCRDM (Subset)/uncoupled clm/CII/uncefact/data/standard/CrossIndustryInvoice_100pD16B.xsd"));
        List<Object> validations = new ArrayList<>();
        Map<String, Object> probes = new LinkedHashMap<>();
        List<String> unexpected = new ArrayList<>();
        Map<String, Object> summary = object("release", RELEASE, "commit", COMMIT,
                "baseline_financial_checks", "PASS (independent Java BigDecimal calculation)",
                "baseline_business_values", invoice.values(),
                "java", Files.readString(logs.resolve("java-version.log")).trim(),
                "xslt_sha256", XSLT_HASHES, "engines", engineDetails, "support_jars", supportDetails,
                "official_validation", validations, "numeric_probes", probes);
        for (String syntax : List.of("ubl", "cii")) {
            Path stylesheet = upstream.resolve(syntax + "/xslt/EN16931-" + syntax.toUpperCase(Locale.ROOT) + "-validation.xslt");
            verifyHash(stylesheet, XSLT_HASHES.get(syntax));
            List<InvoiceCase> cases = variants(syntax);
            validateSchema(schemas.get(syntax), cases, logs.resolve("xsd-" + syntax + ".log"));
            for (InvoiceCase test : cases) {
                for (var engine : classpaths.entrySet()) {
                    String key = test.name + "-" + syntax + "-" + engine.getKey();
                    Path svrlPath = reports.resolve(key + ".xml");
                    Files.deleteIfExists(svrlPath);
                    run(logs.resolve(key + ".log"), java, "-cp", engine.getValue(), "net.sf.saxon.Transform",
                            "-t", "-s:" + test.path, "-xsl:" + stylesheet, "-o:" + svrlPath);
                    Svrl svrl = readSvrl(svrlPath);
                    List<String> ids = svrl.failures.stream().map(f -> (String) f.get("id")).sorted().toList();
                    boolean matches = ids.equals(test.expectedFailures.stream().sorted().toList())
                            && svrl.successfulReports.isEmpty();
                    validations.add(object("case", test.name, "syntax", syntax, "engine", engine.getKey(),
                            "xsd", "PASS", "status", ids.isEmpty() ? "PASS" : "FAIL",
                            "fired_rules", svrl.firedRules, "failures", svrl.failures,
                            "successful_reports", svrl.successfulReports,
                            "expected_failure_ids", test.expectedFailures, "matches_expectation", matches,
                            "invoice_sha256", digest(test.path), "svrl", output.relativize(svrlPath).toString()));
                    System.out.println(key + ": " + (ids.isEmpty() ? "PASS" : "FAIL " + String.join(", ", ids)));
                    if (!matches) {
                        unexpected.add(key);
                    }
                }
            }
        }
        Map<String, List<String>> expectedProbes = Map.of(
                "negative-vat-round-one-argument", List.of("-0.66", "-0.67"),
                "negative-vat-round-two-arguments", List.of("-0.66", "-0.67"),
                "decimal-literals", List.of("true", "true"),
                "exponent-literals", List.of("false", "true"),
                "explicit-decimal-xml", List.of("true", "true"),
                "untyped-xml", List.of("false", "true"),
                "number-function", List.of("false", "false"),
                "division-precision", List.of("false", "true"),
                "line-binary-floating-point", List.of("1", "1.01"),
                "line-negative-midpoint", List.of("-6.37", "-6.38"));
        for (var engine : classpaths.entrySet()) {
            Path result = output.resolve("numeric-probes-" + engine.getKey() + ".xml");
            Files.deleteIfExists(result);
            run(logs.resolve("probes-" + engine.getKey() + ".log"), java, "-cp", engine.getValue(),
                    "net.sf.saxon.Transform", "-s:" + fixtures.resolve("numeric-probes.xml"),
                    "-xsl:" + fixtures.resolve("numeric-probes.xsl"), "-o:" + result);
            List<Map<String, Object>> values = elements(parse(result), "/probes/probe").stream()
                    .map(En16931Comparison::attributes).toList();
            probes.put(engine.getKey(), values);
            Set<Object> ids = values.stream().map(p -> p.get("id")).collect(Collectors.toSet());
            if (!ids.equals(expectedProbes.keySet()) || values.size() != expectedProbes.size()) {
                unexpected.add(engine.getKey() + ": missing, extra or duplicate numeric probes");
            }
            for (Map<String, Object> probe : values) {
                List<String> expected = expectedProbes.get(probe.get("id"));
                if (expected == null || !Objects.equals(probe.get("actual"),
                        expected.get(engine.getKey().equals("enhanced") ? 1 : 0))) {
                    unexpected.add(engine.getKey() + ": " + probe.get("id") + " = " + probe.get("actual"));
                }
            }
        }
        summary.put("unexpected_results", unexpected);
        Files.writeString(output.resolve("summary.json"), json(summary, 0) + "\n");
        System.out.println("Results: " + output.resolve("summary.json"));
        require(unexpected.isEmpty(), "Unexpected comparison results: " + String.join("; ", unexpected));
    }

    private Invoice baseline(String syntax) throws Exception {
        Document document = parse(fixtures.resolve("invoice-2017-" + syntax + ".xml"));
        Element root = document.getDocumentElement();
        List<Line> lines = new ArrayList<>();
        List<Tax> taxes = new ArrayList<>();
        List<BigDecimal> totals;
        String id;
        String issueDate;
        String currency;
        BigDecimal vat;
        if (syntax.equals("ubl")) {
            for (Element line : elements(root, "cac:InvoiceLine")) {
                lines.add(new Line(text(line, "cac:Item/cbc:Name"), decimal(line, "cbc:InvoicedQuantity"),
                        find(line, "cbc:InvoicedQuantity").getAttribute("unitCode"),
                        decimal(line, "cac:Price/cbc:PriceAmount"), decimal(line, "cac:Price/cbc:BaseQuantity"),
                        decimal(line, "cac:Item/cac:ClassifiedTaxCategory/cbc:Percent"), decimal(line, "cbc:LineExtensionAmount")));
            }
            for (Element tax : elements(root, "cac:TaxTotal/cac:TaxSubtotal")) {
                taxes.add(new Tax(decimal(tax, "cac:TaxCategory/cbc:Percent"),
                        decimal(tax, "cbc:TaxableAmount"), decimal(tax, "cbc:TaxAmount")));
            }
            totals = decimals(find(root, "cac:LegalMonetaryTotal"), "cbc:",
                    List.of("LineExtensionAmount", "TaxExclusiveAmount", "TaxInclusiveAmount", "PayableRoundingAmount", "PayableAmount"));
            vat = decimal(root, "cac:TaxTotal/cbc:TaxAmount");
            currency = text(root, "cbc:DocumentCurrencyCode");
            id = text(root, "cbc:ID");
            issueDate = text(root, "cbc:IssueDate").replace("-", "");
        } else {
            Element transaction = find(root, "rsm:SupplyChainTradeTransaction");
            for (Element line : elements(transaction, "ram:IncludedSupplyChainTradeLineItem")) {
                Element price = find(line, "ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice");
                Element settlement = find(line, "ram:SpecifiedLineTradeSettlement");
                Element quantity = find(line, "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity");
                lines.add(new Line(text(line, "ram:SpecifiedTradeProduct/ram:Name"), number(quantity.getTextContent()),
                        quantity.getAttribute("unitCode"), decimal(price, "ram:ChargeAmount"), decimal(price, "ram:BasisQuantity"),
                        decimal(settlement, "ram:ApplicableTradeTax/ram:RateApplicablePercent"),
                        decimal(settlement, "ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount")));
            }
            Element settlement = find(transaction, "ram:ApplicableHeaderTradeSettlement");
            for (Element tax : elements(settlement, "ram:ApplicableTradeTax")) {
                taxes.add(new Tax(decimal(tax, "ram:RateApplicablePercent"), decimal(tax, "ram:BasisAmount"),
                        decimal(tax, "ram:CalculatedAmount")));
            }
            Element total = find(settlement, "ram:SpecifiedTradeSettlementHeaderMonetarySummation");
            totals = decimals(total, "ram:", List.of("LineTotalAmount", "TaxBasisTotalAmount", "GrandTotalAmount", "RoundingAmount", "DuePayableAmount"));
            vat = decimal(total, "ram:TaxTotalAmount");
            currency = text(settlement, "ram:InvoiceCurrencyCode");
            id = text(root, "rsm:ExchangedDocument/ram:ID");
            issueDate = text(root, "rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString");
        }
        for (Line line : lines) {
            BigDecimal amount = line.quantity.multiply(line.price).divide(line.baseQuantity, 2, RoundingMode.HALF_UP);
            require(equal(amount, line.amount), syntax + ": inconsistent line amount for " + line.name);
        }
        for (Tax tax : taxes) {
            BigDecimal basis = lines.stream().filter(l -> equal(l.rate, tax.rate)).map(Line::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal amount = tax.basis.multiply(tax.rate).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
            require(equal(basis, tax.basis) && equal(amount, tax.amount), syntax + ": inconsistent VAT breakdown at " + tax.rate + "%");
        }
        BigDecimal lineSum = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxSum = taxes.stream().map(Tax::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        require(equal(lineSum, totals.get(0)) && equal(totals.get(0), totals.get(1))
                && equal(taxSum, vat) && equal(totals.get(1).add(vat), totals.get(2))
                && equal(totals.get(2).add(totals.get(3)), totals.get(4)), syntax + ": inconsistent document totals");
        return new Invoice(id, issueDate, currency, lines, taxes, totals, vat);
    }

    private List<InvoiceCase> variants(String syntax) throws Exception {
        List<InvoiceCase> cases = new ArrayList<>();
        Path original = fixtures.resolve("invoice-2017-" + syntax + ".xml");
        for (String name : List.of("baseline", "source-vat", "negative-half-cent", "negative-toward-positive", "invalid-vat", "invalid-payable")) {
            Path path = inputs.resolve(name + "-" + syntax + ".xml");
            if (name.equals("baseline")) {
                // Validate the exact fixture bytes, without changing whitespace or namespace declarations.
                Files.copy(original, path, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Document document = parse(original);
                Element root = document.getDocumentElement();
                boolean ubl = syntax.equals("ubl");
                Element settlement = ubl ? root : find(root, "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement");
                Element total = find(settlement, ubl ? "cac:LegalMonetaryTotal" : "ram:SpecifiedTradeSettlementHeaderMonetarySummation");
                Element taxTotal = ubl ? find(root, "cac:TaxTotal") : total;
                List<Element> taxes = elements(ubl ? taxTotal : settlement, ubl ? "cac:TaxSubtotal" : "ram:ApplicableTradeTax");
                String prefix = ubl ? "cbc:" : "ram:";
                String amountPath = ubl ? "cbc:TaxAmount" : "ram:CalculatedAmount";
                String vatTotalPath = ubl ? "cbc:TaxAmount" : "ram:TaxTotalAmount";
                List<String> fields = ubl
                        ? List.of("LineExtensionAmount", "TaxExclusiveAmount", "TaxInclusiveAmount", "PayableRoundingAmount", "PayableAmount")
                        : List.of("LineTotalAmount", "TaxBasisTotalAmount", "GrandTotalAmount", "RoundingAmount", "DuePayableAmount");
                setText(root, ubl ? "cbc:Note" : "rsm:ExchangedDocument/ram:IncludedNote/ram:Content",
                        "FIKTIVES TESTDOKUMENT. Diagnostische Variante: " + name + ".");
                List<String> values = null;
                switch (name) {
                    case "source-vat" -> {
                        setText(taxes.get(1), amountPath, "0.43");
                        setText(taxTotal, vatTotalPath, "37.93");
                        values = List.of("203.44", "203.44", "241.37", "0.00", "241.37");
                    }
                    case "negative-half-cent", "negative-toward-positive" -> {
                        String vat = name.equals("negative-half-cent") ? "-0.67" : "-0.66";
                        String gross = name.equals("negative-half-cent") ? "-4.17" : "-4.16";
                        setText(taxes.get(0), amountPath, vat);
                        setText(taxes.get(0), ubl ? "cbc:TaxableAmount" : "ram:BasisAmount", "-3.50");
                        taxes.get(1).getParentNode().removeChild(taxes.get(1));
                        setText(taxTotal, vatTotalPath, vat);
                        values = List.of("-3.50", "-3.50", gross, "0.00", gross);
                        Element transaction = ubl ? root : find(root, "rsm:SupplyChainTradeTransaction");
                        List<Element> lines = elements(transaction, ubl ? "cac:InvoiceLine" : "ram:IncludedSupplyChainTradeLineItem");
                        for (Element line : lines.subList(1, lines.size())) {
                            transaction.removeChild(line);
                        }
                        setText(lines.get(0), ubl ? "cbc:InvoicedQuantity" : "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity", "-1");
                        setText(lines.get(0), ubl ? "cbc:LineExtensionAmount"
                                : "ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount", "-3.50");
                        setText(lines.get(0), ubl ? "cac:Price/cbc:PriceAmount"
                                : "ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount", "3.50");
                    }
                    case "invalid-vat" -> {
                        setText(taxes.get(1), amountPath, "5.42");
                        setText(taxTotal, vatTotalPath, "42.92");
                        values = List.of("203.44", "203.44", "246.36", "0.01", "246.37");
                    }
                    case "invalid-payable" -> setText(total, prefix + fields.getLast(), "242.37");
                    default -> throw new IllegalStateException("Unknown case: " + name);
                }
                if (values != null) {
                    for (int i = 0; i < fields.size(); i++) {
                        setText(total, prefix + fields.get(i), values.get(i));
                    }
                }
                var transformer = TransformerFactory.newDefaultInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.transform(new DOMSource(document), new StreamResult(path.toFile()));
            }
            List<String> expected = switch (name) {
                case "invalid-vat" -> List.of("BR-CO-17", "BR-S-09");
                case "invalid-payable" -> List.of("BR-CO-16");
                default -> List.of();
            };
            cases.add(new InvoiceCase(name, path, expected));
        }
        return cases;
    }

    private Path assets() throws Exception {
        List<Asset> assets = List.of(
                new Asset("en16931.zip", "https://codeload.github.com/ConnectingEurope/eInvoicing-EN16931/zip/" + COMMIT,
                        "57a52054fb5f56d06ccc8023c3f999c95fc72e392853cd9d633bfccc458105d9"),
                new Asset("UBL-2.1.zip", "https://docs.oasis-open.org/ubl/os-UBL-2.1/UBL-2.1.zip",
                        "60b80d76394a8a2add90723ecb8e0e2e9d826775de9749df37a72d60703f86ed"));
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30)).build()) {
            for (Asset asset : assets) {
                Path target = cache.resolve(asset.name);
                if (!Files.exists(target)) {
                    System.out.println("Downloading " + asset.url);
                    Path temporary = cache.resolve(asset.name + ".download");
                    Files.deleteIfExists(temporary);
                    var request = HttpRequest.newBuilder(URI.create(asset.url)).timeout(Duration.ofMinutes(2)).build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
                    require(response.statusCode() == 200, "Download failed: HTTP " + response.statusCode() + " for " + asset.url);
                    verifyHash(temporary, asset.sha256);
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                verifyHash(target, asset.sha256);
                try (ZipFile zip = new ZipFile(target.toFile())) {
                    for (var entry : Collections.list(zip.entries())) {
                        String name = entry.getName();
                        boolean keep = asset.name.equals("UBL-2.1.zip") ? name.startsWith("xsd/")
                                : name.contains("/schema/") || name.contains("/xslt/") || name.endsWith("/LICENSE.txt");
                        if (!keep || entry.isDirectory()) {
                            continue;
                        }
                        Path destination = cache.resolve(name).normalize();
                        require(destination.startsWith(cache), "Unsafe archive path: " + name);
                        Files.createDirectories(destination.getParent());
                        require(destination.getParent().toRealPath().startsWith(cache.toRealPath())
                                && !Files.isSymbolicLink(destination), "Unsafe extraction target: " + destination);
                        try (InputStream stream = zip.getInputStream(entry)) {
                            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }
        return cache.resolve("eInvoicing-EN16931-" + COMMIT);
    }

    private static void validateSchema(Path schemaPath, List<InvoiceCase> cases, Path log) throws Exception {
        // JDK provider: XSD validation does not load either Saxon implementation.
        var factory = SchemaFactory.newDefaultInstance();
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        var validator = factory.newSchema(schemaPath.toFile()).newValidator();
        try (var writer = Files.newBufferedWriter(log)) {
            for (InvoiceCase test : cases) {
                try {
                    validator.validate(new StreamSource(test.path.toFile()));
                    writer.write("XSD PASS " + test.path.getFileName() + "\n");
                } catch (Exception exception) {
                    writer.write("XSD FAIL " + test.path.getFileName() + ": " + exception.getMessage() + "\n");
                    throw exception;
                }
            }
        }
    }

    private static Svrl readSvrl(Path path) throws Exception {
        Document document = parse(path);
        Element root = document.getDocumentElement();
        require(NS.get("svrl").equals(root.getNamespaceURI()) && "schematron-output".equals(root.getLocalName()), "Not SVRL: " + path);
        int fired = elements(root, ".//svrl:fired-rule").size();
        require(fired > 0, "No rules fired: " + path);
        return new Svrl(fired, messages(root, ".//svrl:failed-assert"), messages(root, ".//svrl:successful-report"));
    }

    private static List<Map<String, Object>> messages(Node node, String path) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Element element : elements(node, path)) {
            Map<String, Object> message = attributes(element);
            message.put("message", element.getTextContent().replaceAll("\\s+", " ").trim());
            result.add(message);
        }
        return result;
    }

    private void run(Path log, Object... arguments) throws Exception {
        List<String> command = Arrays.stream(arguments).map(Object::toString).toList();
        var builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        int status = builder.start().waitFor();
        if (status != 0) {
            String detail = Files.readString(log);
            throw new IllegalStateException("Command failed (" + status + "); see " + log + "\n"
                    + detail.substring(Math.max(0, detail.length() - 4000)));
        }
    }

    private static boolean isWindows() { return System.getProperty("os.name").startsWith("Windows"); }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
    private static String digest(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void verifyHash(Path path, String expected) throws Exception {
        String actual = digest(path);
        require(actual.equals(expected), "SHA-256 mismatch for " + path + ": " + actual + ", expected " + expected);
    }
    private static Document parse(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(path.toFile());
    }
    private static XPath newXPath() {
        XPath xpath = XPathFactory.newDefaultInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return NS.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }
            public String getPrefix(String uri) { return getPrefixes(uri).hasNext() ? getPrefixes(uri).next() : null; }
            public Iterator<String> getPrefixes(String uri) {
                return NS.entrySet().stream().filter(e -> e.getValue().equals(uri)).map(Map.Entry::getKey).iterator();
            }
        });
        return xpath;
    }
    private static List<Element> elements(Node node, String path) throws Exception {
        NodeList nodes = (NodeList) XPATH.evaluate(path, node, XPathConstants.NODESET);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) { result.add((Element) nodes.item(i)); }
        return result;
    }
    private static Element find(Node node, String path) throws Exception {
        Element result = (Element) XPATH.evaluate(path, node, XPathConstants.NODE);
        require(result != null, "Missing fixture element: " + path);
        return result;
    }
    private static String text(Node node, String path) throws Exception { return find(node, path).getTextContent().trim(); }
    private static void setText(Node node, String path, String value) throws Exception { find(node, path).setTextContent(value); }
    private static BigDecimal number(String value) { return new BigDecimal(value.trim()).stripTrailingZeros(); }
    private static BigDecimal decimal(Node node, String path) throws Exception { return number(text(node, path)); }
    private static List<BigDecimal> decimals(Node node, String prefix, List<String> fields) throws Exception {
        List<BigDecimal> result = new ArrayList<>();
        for (String field : fields) { result.add(decimal(node, prefix + field)); }
        return result;
    }
    private static boolean equal(BigDecimal left, BigDecimal right) { return left.compareTo(right) == 0; }
    private static Map<String, Object> attributes(Element node) {
        Map<String, Object> result = new LinkedHashMap<>();
        var attributes = node.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            result.put(attribute.getNodeName(), attribute.getNodeValue());
        }
        return result;
    }
    private static Map<String, Object> object(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) { result.put((String) pairs[i], pairs[i + 1]); }
        return result;
    }

    /** Small JSON writer for this report's maps, lists, scalar values and decimal strings. */
    private static String json(Object value, int depth) {
        String indent = "  ".repeat(depth);
        String childIndent = indent + "  ";
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty() ? "{}" : "{\n" + map.entrySet().stream()
                    .map(e -> childIndent + quote(e.getKey().toString()) + ": " + json(e.getValue(), depth + 1))
                    .collect(Collectors.joining(",\n")) + "\n" + indent + "}";
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? "[]" : "[\n" + list.stream().map(v -> childIndent + json(v, depth + 1))
                    .collect(Collectors.joining(",\n")) + "\n" + indent + "]";
        }
        if (value instanceof BigDecimal decimal) { return quote(decimal.toPlainString()); }
        if (value instanceof Number || value instanceof Boolean) { return value.toString(); }
        return value == null ? "null" : quote(value.toString());
    }
    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) { result.append(String.format("\\u%04x", (int) c)); }
                    else { result.append(c); }
                }
            }
        }
        return result.append('"').toString();
    }
}
