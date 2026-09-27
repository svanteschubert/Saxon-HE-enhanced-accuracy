# EN16931:2017 invoice comparison on Saxon 13.0

The two baseline invoices pass XML Schema validation and the unchanged official
EN16931 1.3.16 XSLT on **both stock Saxon-HE 13.0 and this enhanced-accuracy fork**.
The supplied example does not demonstrate a failure of stock Saxon. The official
rules also accept both rounding outcomes at the negative VAT midpoint tested here.
Separate arithmetic probes demonstrate the fork's changes without modifying or
misrepresenting the official validator.

## Invoices and input mapping

- [UBL 2.1 invoice](../src/test/resources/en16931/invoice-2017-ubl.xml)
- [UN/CEFACT CII D16B invoice](../src/test/resources/en16931/invoice-2017-cii.xml)

Both encode invoice `TEST-2017-0001`, issued and delivered on 15 November 2017,
due on 15 December 2017, in EUR. “2017” is interpreted as both EN16931:2017 and
the invoice year. Seller **Fiktiver Musterhandel Elbwolke GmbH**, Hamburg, and buyer
**Fiktive Beispielwerkstatt Mainstern GmbH**, Frankfurt am Main, are invented.
Their addresses and VAT identifiers are synthetic test data. Both countries are
`DE`; no live registration or payment details are used.

The source workbook and its Excel lock file are input-only and are not part of
the deliverable. Neither building nor running this comparison requires them.
The source mapping is `1 Original Calculation!B4:H6`, the VAT rate table at
`J4:K5`, the category calculations at `A9:C10`, and totals at `A12:B17`.
The five-decimal net unit prices follow `2 Net from Gross!B4` and its rows 7–9.
Business decimal values (2.049 EUR and 7%) are used, rather than Excel's serialized
binary approximations (2.0489999999999999 and 0.070000000000000007).

| Item | Quantity / unit | Source gross unit price | VAT | Invoice net unit price | Rounded net line amount |
| --- | ---: | ---: | ---: | ---: | ---: |
| Tobacco | 3 / C62 | 9.99 | 19% | 8.39496 | 25.18 |
| c't magazin für computer technik | 1 / C62 | 6.50 | 7% | 6.07477 | 6.07 |
| Gasoline | 100 / LTR | 2.049 | 19% | 1.72185 | 172.19 |

Net unit price = gross unit price / (1 + rate / 100), rounded half away from zero
to five decimal places. Line amounts are rounded to two decimal places. Both
19% and 7% use VAT category `S` (taxed at a positive rate).
The VAT-inclusive source prices are described in the invoice note, not put in the
UBL/CII “gross price” field: that business term means price before discounts,
**excluding VAT**.

There is a real one-cent difference between the workbook's gross-first calculation
and tax calculated from the invoice's rounded net amounts:

| Total | Workbook gross-first calculation | Baseline invoices |
| --- | ---: | ---: |
| 19% taxable amount | 197.37 | 197.37 |
| 19% VAT | 37.50 | 37.50 |
| 7% taxable amount | 6.07 | 6.07 |
| 7% VAT | 0.43 | 0.42 |
| Net total | 203.44 | 203.44 |
| VAT total | 37.93 | 37.92 |
| Total including VAT, before payable rounding | 241.37 | 241.36 |
| Payable rounding adjustment (BT-114) | — | +0.01 |
| Amount payable | 241.37 | 241.37 |

The workbook uses `round(6.50 × 7 / 107, 2) = 0.43`. The baseline uses
`round(6.07 × 7 / 100, 2) = 0.42`. The **explicit +0.01 payable adjustment**
preserves the source payment total while making the stated tax match the rounded
net tax basis. This is a documented fixture-design choice, not a change to the
spreadsheet or evidence of an engine defect. Java `BigDecimal` independently checks
line calculations, VAT breakdowns, totals, and equivalence of the UBL/CII figures.

A generated `source-vat` variant retains the workbook's original 0.43/37.93 VAT
figures and 241.37 total, with zero payable rounding. It passes the official rules
on both engines because of their tolerances. That pass does not make 0.43 the
rounded product of 6.07 and 7%.

## Reproduce

Requirements: JDK 25+, Maven, and network access on first execution.
Select JDK 25 through your existing `jenv` setup or `JAVA_HOME`, then run from the
repository root:

```bash
java -version
mvn -version
mvn -Pen16931-comparison -Dmaven.javadoc.skip=true verify
```

The platform-independent implementation is
[En16931Comparison.java](../src/test/java/net/sf/saxon/En16931Comparison.java),
invoked by [En16931ComparisonIT](../src/test/java/net/sf/saxon/En16931ComparisonIT.java)
through Maven Failsafe. It uses JDK APIs for downloads, hashing, ZIP extraction,
XML Schema validation, XML processing, process execution, and JSON reports.
There are no standalone comparison scripts or additional runtime libraries.

The profile in the root [pom.xml](../pom.xml) copies stock
`net.sf.saxon:Saxon-HE:13.0` and XML Resolver into a separate directory before
integration testing. Maven passes `${project.build.directory}/${project.build.finalName}.jar`
to the test, so the comparison follows the current fork coordinates and version,
including `com.schubert-consulting:Saxon-HE-accuracy:13.0.1-SNAPSHOT`.
Each engine runs in its own JVM with the same XML Resolver dependencies.
The stock JAR and fork JAR are never put on the same classpath.

The integration test is disabled unless the profile is selected. Ordinary builds
do not download these validator archives or execute the comparison. Javadoc is
skipped in the command above only to shorten the build; it does not affect validation.

The default output is `target/en16931-comparison/`:

- `summary.json`: engine and stylesheet hashes, independent financial checks,
  every assertion failure (ID, location, test, message), and numeric probe results.
- `invoices/`: baseline copies and all diagnostic variants in both syntaxes.
- `svrl/`: all 24 full official-validator SVRL results.
- `logs/`: XSD validation, exact engine versions and transformation logs.
- `numeric-probes-stock.xml` and `numeric-probes-enhanced.xml`: supplemental results.
- `cache/`: verified upstream archives, schemas, unmodified stylesheets, and JARs.

Failsafe also writes its integration-test results to `target/failsafe-reports/`.

The test requires nonempty SVRL rule execution and checks failed assertions;
a successful Java/XSLT exit code alone does not mean an invoice passed. Intentional
negative controls must fail with exactly the expected rule IDs. Any unexpected
result fails the integration test and Maven build. Downloaded archives and stylesheets are
SHA-256 checked. Cached archives are checked again on reuse.

## Observed official validation results

Run on 27 September 2026 through the Java integration test with Temurin JDK
25.0.4.1, stock Saxon-HE 13.0, and a fresh build of
`com.schubert-consulting:Saxon-HE-accuracy:13.0.1-SNAPSHOT` on `accuracy-feature`.
The run started with an empty validation cache and downloaded both archives using
Java's HTTP client. The recorded results and JAR hash below refer to that build.
The existing Maven suite passed: 22 tests, 18 successful and 4 previously disabled.
The additional Failsafe integration test passed all 24 official comparisons and
16 numeric probe outcomes. With the profile disabled, this integration test was
confirmed to be skipped.
All twelve XML instances (six cases × two syntaxes) passed their XSDs.

| Case | UBL stock | UBL enhanced | CII stock | CII enhanced |
| --- | --- | --- | --- | --- |
| Baseline invoices above | PASS | PASS | PASS | PASS |
| Original workbook VAT totals (`source-vat`) | PASS | PASS | PASS | PASS |
| Negative midpoint: net −3.50, VAT −0.67 | PASS | PASS | PASS | PASS |
| Same net, VAT −0.66 instead | PASS | PASS | PASS | PASS |
| VAT overstated by 5.00; totals reconciled | FAIL | FAIL | FAIL | FAIL |
| Payable total overstated by 1.00 | FAIL | FAIL | FAIL | FAIL |

The VAT-overstatement control fails `BR-CO-17` and `BR-S-09` in every run.
The payable control fails `BR-CO-16` in every run. Baseline SVRL contains 75
fired rules for UBL and 109 for CII, with no failed assertions or successful-report
warnings. All 24 outcomes match expectations.

These are **local runs of the official EU/CEN validation artefacts**, not submissions
to an online EU service. The scope is base EN16931 plus syntax XSDs, not XRechnung,
Peppol CIUS, a historical 2017 validator release, or a legal/registration check.
The [upstream project](https://github.com/ConnectingEurope/eInvoicing-EN16931)
explicitly distinguishes its base rules from CIUS rules.

## Why the official rules do not show the desired difference

In the pinned [UBL rules](https://github.com/ConnectingEurope/eInvoicing-EN16931/blob/a519ba02a59e2775436428f57ee96899feb1da8c/ubl/schematron/UBL/EN16931-UBL-model.sch),
`BR-S-09` casts the amounts and rate to `xs:decimal`. Stock Saxon already performs
these decimal operations without binary floating-point. Its division by 100 is
terminating. Both engines round positive midpoints the same way.

The VAT checks take `abs()` of the taxable amount and tax amount. Consequently,
the negative midpoint −0.665 is checked through positive magnitudes. `BR-S-09`
uses a strict ±1 monetary-unit window; `BR-CO-17` uses a strict window in UBL and
an inclusive one in CII. A one-cent difference is accepted by both. Document-total
rules typically sum values already restricted to two decimal places, so rounding
those sums to cents does not expose a negative half-cent either.

CII also contains untyped arithmetic, including the rate in `BR-S-09` and some
sums. The current fork does **not** replace all of that implicit binary arithmetic.
Merely validating the XML separately with an XSD does not change the values seen
by these subsequent Saxon-HE transformations.

Thus neither choosing a negative VAT midpoint nor carrying the workbook's extra
VAT cent creates a defensible “stock rejects, fork accepts” result with these
unchanged rules. No such result was observed here.

## Supplemental arithmetic results and useful next experiments

[numeric-probes.xsl](../src/test/resources/en16931/numeric-probes.xsl) is deliberately
separate from the official XSLT. Its companion XML supplies runtime inputs.
“Expected” means the decimal/half-away-from-zero policy being investigated; stock
Saxon's different result is often correct under standard XPath semantics.

| Probe | Desired result | Stock 13.0 | Enhanced 13.0 |
| --- | --- | --- | --- |
| `round(-0.665 * 100) div 100` | −0.67 | −0.66 | −0.67 |
| `round(-0.665, 2)` | −0.67 | −0.66 | −0.67 |
| `0.1 + 0.2 = 0.3` | true | true | true |
| `0.1E0 + 0.2E0 = 0.3E0` | true | false | true |
| Explicit decimal XML operands: 0.1 + 0.2 = 0.3 | true | true | true |
| Untyped XML operands: 0.1 + 0.2 = 0.3 | true | false | false |
| `number()` operands: 0.1 + 0.2 = 0.3 | true | false | false |
| `(1.0 div 3.0) * 10^20` equals 33333333333333333333.33333333333333 | true | false | true |

For a meaningful strict VAT comparison, use a supplemental Schematron rule that
requires the **signed**, rounded tax to equal the declared tax, with decimal casts
and without the official ±1 tolerance. A −3.50 EUR line at 19%, declaring −0.67,
then exercises the desired policy. The existing focused
[invoice.sch](../src/test/resources/schematron/invoice.sch) and
[SchematronAccuracyTest](../src/test/java/net/sf/saxon/SchematronAccuracyTest.java)
provide that kind of separate test. Keep its results labelled as supplemental.

For precision, test invoice **generation** as well as validation: calculate net
prices, line totals and taxes from source quantities/prices, then validate the
result. A validator that trusts rounded line amounts cannot reveal every error
in the calculation that produced them. Exponent literals expose one current fork
benefit; explicit `xs:decimal` XML casts show the portable decimal baseline.
Untyped arithmetic and `number()` remain useful failing probes for a future
numeric-policy change, rather than reasons to claim the current fork is wholly
decimal or implements IEEE decimal128.

## Provenance

Official artefacts: [EN16931 validation release 1.3.16](https://github.com/ConnectingEurope/eInvoicing-EN16931/releases/tag/validation-1.3.16).
The tag checkout used for reproducibility resolves to
`a519ba02a59e2775436428f57ee96899feb1da8c`.
The two XSLT files were also compared with the published release ZIP assets and
are byte-identical. No Schematron recompilation, patching or wrapper changes the
rules. CII XSDs come from the upstream repository's D16B uncoupled subset; UBL XSDs
come from the [OASIS UBL 2.1 distribution](https://docs.oasis-open.org/ubl/os-UBL-2.1/).
Upstream licences remain in the downloaded artefacts; EU validation artefacts are
EUPL 1.2. Third-party stylesheets/schemas and the workbook are not vendored.

| Asset | SHA-256 |
| --- | --- |
| UBL validation XSLT | `39f9d282867f1a49e7708d9e29a53da89643e1ee56f10cec1ebcf1277595fcbd` |
| CII validation XSLT | `0b234dea2bbfee739b7761e607a992c17fab88773014ef56355b6158cfb1cc53` |
| Stock Saxon-HE 13.0 JAR | `258fb4788b8e1bd986f9aed14269669412da88c7bb289b747878d4353f6168aa` |
| Enhanced JAR used in this run | `36bac57acb90d5d423d3397fb377e4694e526f6801c4e007995148940edcfe8b` |

The enhanced JAR hash can vary between builds because of archive timestamps.
Every run records its actual hash and Java version in `summary.json`.
