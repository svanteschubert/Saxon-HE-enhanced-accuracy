<?xml version="1.0" encoding="UTF-8"?>
<!--
  Verdict of the release check (mvn -Prelease-check verify).

  Before this stylesheet runs, each engine has been called from the command line:
    java -jar <engine>.jar -s:<invoice> -xsl:EN16931-<UBL|CII>-validation.xslt -o:svrl/<engine>/<invoice>
    java -jar <engine>.jar -s:accuracy-examples.xml -xsl:accuracy-examples.xsl -o:accuracy-examples-<engine>.xml

  This stylesheet compares those results with the expectations in cases.xml and accuracy-examples.xml,
  prints a summary, writes report.html and terminates with a non-zero exit code on any mismatch.
  It only compares strings and runs on stock Saxon-HE 13.0, so a defect of the fork cannot hide itself.

    java -jar saxon-he-13.0.jar -it:main -xsl:verdict.xsl cases=… examples=… results=…
-->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:svrl="http://purl.oclc.org/dsdl/svrl"
                xmlns:v="urn:x-svanton:release-check"
                exclude-result-prefixes="#all" expand-text="yes">

  <!-- URIs of cases.xml, accuracy-examples.xml and the directory with the engines' results. -->
  <xsl:param name="cases" as="xs:string" required="yes"/>
  <xsl:param name="examples" as="xs:string" required="yes"/>
  <xsl:param name="results" as="xs:string" required="yes"/>
  <!-- Provenance shown in the report. -->
  <xsl:param name="validator" as="xs:string" select="''"/>
  <xsl:param name="stock-jar" as="xs:string" select="''"/>
  <xsl:param name="stock-sha256" as="xs:string" select="''"/>
  <xsl:param name="svanton-jar" as="xs:string" select="''"/>
  <xsl:param name="svanton-sha256" as="xs:string" select="''"/>
  <xsl:param name="java-version" as="xs:string" select="''"/>

  <xsl:variable name="engines" as="xs:string+" select="'stock', 'svanton'"/>
  <xsl:variable name="dir" as="xs:string" select="replace($results, '([^/])$', '$1/')"/>

  <xsl:function name="v:ids" as="xs:string*">
    <xsl:param name="value" as="xs:string?"/>
    <xsl:sequence select="sort(tokenize(normalize-space($value)))"/>
  </xsl:function>

  <xsl:function name="v:verdict" as="xs:string">
    <xsl:param name="ids" as="xs:string*"/>
    <xsl:sequence select="if (empty($ids)) then 'valid' else string-join($ids, ' ')"/>
  </xsl:function>

  <xsl:function name="v:pad" as="xs:string">
    <xsl:param name="value" as="xs:string"/>
    <xsl:param name="width" as="xs:integer"/>
    <xsl:sequence select="$value || string-join((1 to $width - string-length($value)) ! ' ')"/>
  </xsl:function>

  <xsl:template name="main">
    <!-- One v:result per invoice or example and engine: what was expected, what the engine returned. -->
    <xsl:variable name="invoices" as="element(v:row)*">
      <xsl:for-each select="doc($cases)/cases/case/invoice">
        <xsl:variable name="invoice" select="."/>
        <v:row id="{../@id}" syntax="{upper-case(@syntax)}" label="{../@change}"
               correct="{v:verdict(v:ids(@svanton))}">
          <xsl:for-each select="$engines">
            <xsl:variable name="svrl" select="$dir || 'svrl/' || . || '/' || tokenize($invoice/@href, '/')[last()]"/>
            <xsl:variable name="report" select="if (doc-available($svrl)) then doc($svrl) else ()"/>
            <xsl:variable name="actual" as="xs:string" select="
                if (empty($report)) then 'no SVRL report'
                else if ($report//svrl:successful-report) then 'successful-report ' || string-join($report//svrl:successful-report/@id, ' ')
                else v:verdict(sort($report//svrl:failed-assert/@id ! string()))"/>
            <v:result engine="{.}" expected="{v:verdict(v:ids($invoice/@*[local-name() = current()]))}" actual="{$actual}"/>
          </xsl:for-each>
        </v:row>
      </xsl:for-each>
    </xsl:variable>
    <xsl:variable name="calculations" as="element(v:row)*">
      <xsl:variable name="actual" as="map(xs:string, element(result)*)"
                    select="map:merge($engines ! map:entry(., doc($dir || 'accuracy-examples-' || . || '.xml')/results/result))"
                    xmlns:map="http://www.w3.org/2005/xpath-functions/map"/>
      <xsl:for-each select="doc($examples)/examples/example">
        <xsl:variable name="example" select="."/>
        <v:row id="{@id}" syntax="" label="{xpath}" correct="{fork}">
          <xsl:for-each select="$engines">
            <xsl:variable name="found" select="$actual(.)[@id = $example/@id]"/>
            <v:result engine="{.}" expected="{if (. = 'stock') then $example/stock else $example/fork}"
                      actual="{if (count($found) = 1) then $found/@actual else count($found) || ' results'}"/>
          </xsl:for-each>
        </v:row>
      </xsl:for-each>
    </xsl:variable>
    <xsl:variable name="mismatches" as="xs:string*" select="
        for $row in ($invoices, $calculations), $r in $row/v:result[@expected ne @actual]
        return string-join(($row/@syntax[. ne ''], $row/@id, $r/@engine, 'returned', '&quot;' || $r/@actual || '&quot;,',
                            'expected', '&quot;' || $r/@expected || '&quot;'), ' ')"/>
    <xsl:variable name="missing" as="xs:string*" select="
        for $engine in $engines,
            $result in doc($dir || 'accuracy-examples-' || $engine || '.xml')/results/result[not(@id = doc($examples)/examples/example/@id)]
        return 'accuracy example ' || $result/@id || ' of ' || $engine || ' is not in accuracy-examples.xml'"/>

    <xsl:call-template name="summary">
      <xsl:with-param name="invoices" select="$invoices"/>
      <xsl:with-param name="calculations" select="$calculations"/>
    </xsl:call-template>
    <xsl:result-document href="{$dir}report.html" method="html" html-version="5" indent="no">
      <xsl:call-template name="report">
        <xsl:with-param name="invoices" select="$invoices"/>
        <xsl:with-param name="calculations" select="$calculations"/>
        <xsl:with-param name="problems" select="$mismatches, $missing"/>
      </xsl:call-template>
    </xsl:result-document>
    <xsl:message>Report: {$dir}report.html</xsl:message>
    <xsl:if test="exists(($mismatches, $missing))">
      <xsl:message terminate="yes" error-code="v:mismatch">RELEASE CHECK FAILED, {count(($mismatches, $missing))} unexpected result(s):&#10;  {string-join(($mismatches, $missing), '&#10;  ')}</xsl:message>
    </xsl:if>
    <xsl:message>RELEASE CHECK PASSED: {count($invoices)} invoices and {count($calculations)} calculations gave the expected result on both engines.</xsl:message>
  </xsl:template>

  <xsl:template name="summary">
    <xsl:param name="invoices" as="element(v:row)*"/>
    <xsl:param name="calculations" as="element(v:row)*"/>
    <xsl:message>{v:pad('Official EN16931 validation ' || $validator, 38)} {v:pad('Saxon-HE 13.0', 20)} Svanton</xsl:message>
    <xsl:for-each select="$invoices">
      <xsl:message>{v:pad(@syntax || ' ' || @id, 38)} {v:pad(v:line(v:result[1]), 20)} {v:line(v:result[2])}</xsl:message>
    </xsl:for-each>
    <xsl:message>{v:pad('Calculations', 38)} {v:pad(count($calculations/v:result[1][@actual eq ../@correct]) || ' of ' || count($calculations) || ' correct', 20)} {count($calculations/v:result[2][@actual eq ../@correct])} of {count($calculations)} correct</xsl:message>
  </xsl:template>

  <!-- A verdict for the console: '!' marks an incorrect verdict, '(UNEXPECTED)' a mismatch. -->
  <xsl:function name="v:line" as="xs:string">
    <xsl:param name="result" as="element(v:result)"/>
    <xsl:sequence select="$result/@actual
        || (if ($result/@actual ne $result/../@correct) then ' !' else '')
        || (if ($result/@actual ne $result/@expected) then ' (UNEXPECTED)' else '')"/>
  </xsl:function>

  <xsl:template name="report">
    <xsl:param name="invoices" as="element(v:row)*"/>
    <xsl:param name="calculations" as="element(v:row)*"/>
    <xsl:param name="problems" as="xs:string*"/>
    <html lang="en">
      <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <title>Svanton release check</title>
        <style xsl:expand-text="no">
          :root { --bg: #fff; --fg: #1d1d1f; --muted: #6e6e73; --line: #d2d2d7; --good: #1a7f37; --bad: #cf222e; --head: #f5f5f7; }
          @media (prefers-color-scheme: dark) {
            :root { --bg: #161618; --fg: #f5f5f7; --muted: #a1a1a6; --line: #3a3a3c; --good: #3fb950; --bad: #ff7b72; --head: #222225; }
          }
          body { background: var(--bg); color: var(--fg); font: 15px/1.5 system-ui, sans-serif; margin: 0 auto; max-width: 70rem; padding: 1.5rem 1rem; }
          h1 { font-size: 1.6rem; margin: 0 0 .25rem; }
          h2 { font-size: 1.15rem; margin: 2rem 0 .5rem; }
          p, dl { color: var(--muted); }
          dl { display: grid; grid-template-columns: max-content 1fr; gap: .15rem 1rem; }
          dd { margin: 0; overflow-wrap: anywhere; }
          .scroll { overflow-x: auto; }
          table { border-collapse: collapse; width: 100%; }
          th, td { border-bottom: 1px solid var(--line); padding: .35rem .6rem; text-align: left; vertical-align: top; }
          th { background: var(--head); font-weight: 600; }
          code { font: 13px/1.4 ui-monospace, monospace; }
          .correct { color: var(--good); }
          .incorrect { color: var(--bad); font-weight: 600; }
          .status { font-weight: 700; }
          .pass { color: var(--good); }
          .fail { color: var(--bad); }
        </style>
      </head>
      <body>
        <h1>Svanton release check</h1>
        <p class="status {if (empty($problems)) then 'pass' else 'fail'}">{
          if (empty($problems)) then 'PASSED: every result is as expected on both engines.'
          else 'FAILED: ' || count($problems) || ' unexpected result(s).'}</p>
        <xsl:if test="exists($problems)">
          <ul><xsl:for-each select="$problems"><li><code>{.}</code></li></xsl:for-each></ul>
        </xsl:if>
        <dl>
          <dt>Validator</dt><dd>Official EN16931 validation XSLT {$validator}, unchanged</dd>
          <dt>Saxon-HE 13.0</dt><dd><code>{$stock-jar}</code><br/>SHA-256 <code>{$stock-sha256}</code></dd>
          <dt>Svanton</dt><dd><code>{$svanton-jar}</code><br/>SHA-256 <code>{$svanton-sha256}</code></dd>
          <dt>Java</dt><dd>{$java-version}</dd>
        </dl>
        <p>Green is the correct result, red an incorrect one. Every engine ran from the command line with
          <code>java -jar</code>; the expected results are in <code>cases.xml</code> and <code>accuracy-examples.xml</code>.</p>
        <h2>EN16931 invoices</h2>
        <div class="scroll">
          <table>
            <tr><th>Syntax</th><th>Invoice</th><th>Change against the correct invoice</th><th>Saxon-HE 13.0</th><th>Svanton</th></tr>
            <xsl:for-each select="$invoices">
              <tr><td>{@syntax}</td><td><code>{@id}</code></td><td>{@label}</td><xsl:apply-templates select="v:result"/></tr>
            </xsl:for-each>
          </table>
        </div>
        <h2>Calculations</h2>
        <div class="scroll">
          <table>
            <tr><th>#</th><th>XPath</th><th>Saxon-HE 13.0</th><th>Svanton</th></tr>
            <xsl:for-each select="$calculations">
              <tr><td>{@id}</td><td><code>{@label}</code></td><xsl:apply-templates select="v:result"/></tr>
            </xsl:for-each>
          </table>
        </div>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="v:result">
    <td class="{if (@actual eq ../@correct) then 'correct' else 'incorrect'}">
      <code>{@actual}</code>
      <xsl:if test="@actual ne @expected"><br/><strong class="fail">UNEXPECTED, expected <code>{@expected}</code></strong></xsl:if>
    </td>
  </xsl:template>
</xsl:stylesheet>
