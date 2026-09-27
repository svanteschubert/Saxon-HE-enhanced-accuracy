<?xml version="1.0" encoding="UTF-8"?>
<!-- Supplemental arithmetic probes. This is NOT an official EN16931 validator. -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="xs">
  <xsl:output method="xml" indent="yes"/>
  <xsl:template match="/probes">
    <xsl:variable name="vat" select="xs:decimal(net) * xs:decimal(rate) div 100"/>
    <probes>
      <probe id="negative-vat-round-one-argument" expected="-0.67"
             actual="{round($vat * 100) div 100}"/>
      <probe id="negative-vat-round-two-arguments" expected="-0.67"
             actual="{round($vat, 2)}"/>
      <probe id="decimal-literals" expected="true" actual="{0.1 + 0.2 = 0.3}"/>
      <probe id="exponent-literals" expected="true" actual="{0.1E0 + 0.2E0 = 0.3E0}"/>
      <probe id="explicit-decimal-xml" expected="true"
             actual="{xs:decimal(a) + xs:decimal(b) = xs:decimal(c)}"/>
      <probe id="untyped-xml" expected="true" actual="{a + b = c}"/>
      <probe id="number-function" expected="true"
             actual="{number(a) + number(b) = number(c)}"/>
      <probe id="division-precision" expected="true"
             actual="{(1.0 div 3.0) * 100000000000000000000 = 33333333333333333333.33333333333333}"/>
    </probes>
  </xsl:template>
</xsl:stylesheet>
