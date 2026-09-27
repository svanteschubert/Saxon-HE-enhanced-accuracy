<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:f="https://www.saxonica.com/ns/functions"
                xmlns:h="http://www.w3.org/1999/xhtml"
                xmlns:math="http://www.w3.org/2005/xpath-functions/math"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="#all"
                version="3.0">

<xsl:output method="html" html-version="5" encoding="utf-8" indent="no"/>

<xsl:variable name="overallMax" select="max(//T/@count)"/>

<xsl:template match="/">
  <html>
    <head>
      <title>Coverage report</title>
<style>
:root { font-size: 14pt; }
.line { display: inline-block; width: 100%; }
/*
.line.miss:nth-child(even) { background-color: #f7f7f7; color: #888888; }
.line.miss:nth-child(odd) { background-color: #f2f2f2; color: #888888; }
*/

.line.skip { color: #a0a0a0; }
.line.hit0 {  }
.line.hit1 { background-color: rgb(0, 228, 0, 0.1); }
.line.hit2 { background-color: rgb(0, 228, 0, 0.2); }
.line.hit3 { background-color: rgb(0, 228, 0, 0.3); }
.line.hit4 { background-color: rgb(0, 228, 0, 0.4); }
.line.hit5 { background-color: rgb(0, 228, 0, 0.5); }
.line.hit6 { background-color: rgb(0, 228, 0, 0.6); }
.line.hit7 { background-color: rgb(0, 228, 0, 0.7); }
.line.hit8 { background-color: rgb(0, 228, 0, 0.8); }
.line.hit9 { background-color: rgb(0, 228, 0, 0.9); }
/*
.line.hit:nth-child(even) { background-color: #f7e7e7; }
.line.hit:nth-child(odd) { background-color: #f2e2e2; }
*/
.lno, .hitcount { color: black; border-right: 1px solid #7f7f7f; padding-right: 0.25em; }
.header { color: black; border-bottom: 1px solid black; }
.poi { background-color: rgb(0, 228, 0); }
.poi .count { font-size: 10pt; }
</style>
    </head>
    <body>
      <xsl:apply-templates/>
    </body>
  </html>
</xsl:template>

<xsl:template match="coverage">
  <xsl:apply-templates select="module">
    <xsl:sort select="@module"/>
  </xsl:apply-templates>
</xsl:template>

<xsl:template match="module">
  <xsl:variable name="module" select="."/>
  <xsl:variable name="text" select="unparsed-text(@module)"/>

  <xsl:variable name="moduleLines" select="count(T)"/>
  <xsl:variable name="hitLines" select="count(T[xs:integer(@count) gt 0])"/>

  <details>
    <summary>
      <code>
        <!-- It feels like format-number should be able to do this, but ... I failed -->
        <xsl:variable name="perc" select="floor(round($hitLines * 100 div $moduleLines))"/>
        <xsl:if test="$perc lt 100"> </xsl:if>
        <xsl:if test="$perc lt 10"> </xsl:if>
        <xsl:value-of select="$perc"/>
        <xsl:text>% </xsl:text>
        <xsl:value-of select="@module"/>
      </code>
    </summary>
    <pre>
      <span class="line header">
        <code class="lno">line#</code>
        <xsl:text> </xsl:text>
        <code class="hitcount">hits</code>
        <xsl:text> </xsl:text>
        <code>text</code>
      </span>
      <xsl:for-each select="tokenize($text, '&#10;')">
        <xsl:variable name="lnum" select="position()"/>
        <xsl:variable name="hits" select="$module/T[@line=$lnum]/@count ! xs:integer(.)"/>

        <xsl:variable name="class" as="xs:string">
          <xsl:choose>
            <xsl:when test="empty($hits)">
              <xsl:sequence select="'skip'"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:variable name="percentile"
                            select="round((math:log10($hits) * 10) div math:log10($overallMax))"/>
              <xsl:variable name="level"
                            select="if ($hits = 0)
                                    then 0
                                    else max((min(($percentile, 9)), 1))"/>
              <xsl:sequence select="'hit' || $level"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:variable>

        <span>
          <xsl:attribute name="class" select="'line ' || $class"/>
          <code class="lno">
            <xsl:if test="$lnum lt 10000"> </xsl:if>
            <xsl:if test="$lnum lt 1000"> </xsl:if>
            <xsl:if test="$lnum lt 100"> </xsl:if>
            <xsl:if test="$lnum lt 10"> </xsl:if>
            <xsl:value-of select="$lnum"/>
          </code>
          <xsl:text> </xsl:text>
          <xsl:choose>
            <xsl:when test="exists($hits)">
              <code class="hitcount">
                <xsl:if test="$hits lt 1000"> </xsl:if>
                <xsl:if test="$hits lt 100"> </xsl:if>
                <xsl:if test="$hits lt 10"> </xsl:if>
                <xsl:value-of select="$hits"/>
              </code>
              <xsl:text> </xsl:text>
            </xsl:when>
            <xsl:otherwise>
              <code class="hitcount">    </code>
              <xsl:text> </xsl:text>
            </xsl:otherwise>
          </xsl:choose>
          <xsl:value-of select="."/>
          <xsl:text> </xsl:text>
        </span>
        <xsl:text>&#10;</xsl:text>
      </xsl:for-each>
    </pre>
  </details>
</xsl:template>

<xsl:function name="f:show-line" as="node()*">
  <xsl:param name="line" as="xs:string"/>
  <xsl:value-of select="$line"/>
</xsl:function>

<xsl:template match="T">
  <xsl:if test="xs:integer(@count) ge 0">
    <span class="poi">
      <xsl:text>⌘</xsl:text>
      <span class="count">
        <xsl:value-of select="@count"/>
      </span>
    </span>
  </xsl:if>
</xsl:template>

</xsl:stylesheet>
