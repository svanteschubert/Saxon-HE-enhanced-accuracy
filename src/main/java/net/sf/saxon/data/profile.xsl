<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="http://saxonica.com/ns/profile-functions"
    exclude-result-prefixes="xs"
    expand-text="yes"
    version="3.0">
    
    <xsl:param name="lang" as="xs:string" static="yes" required="yes"/>
    
    <xsl:variable name="process" as="xs:string" static="yes" select="if ($lang = 'XSLT') then 'Stylesheet' else 'Query'"/>
    <xsl:variable name="templateOr" select="if ($lang = 'XSLT') then 'template, ' else ''"/>
    <xsl:variable name="templatesAnd" select="if ($lang = 'XSLT') then 'templates and ' else ''"/>
    
    <xsl:variable name="style" as="xs:string" expand-text="no">
        
        
        body {
        background: #e4eef0;
        }
        
        h1 {
        font-family: Verdana, Arial, Helvetica, sans-serif;
        font-size: 14pt;
        font-style: normal;
        color: #3D5B96;
        font-weight: bold;
        }
        
        h2 {
        font-family: Verdana, Arial, Helvetica, sans-serif;
        font-size: 12pt;
        font-style: normal;
        color: #96433D;
        font-weight: bold;
        }		
        
        p {
        font-family: Verdana, Arial, Helvetica, sans-serif;
        font-size: 9pt;
        font-style: normal;
        color: #3D5B96;
        font-weight: normal;
        line-height: 1.3em;
        padding-right:15px;
        }
        
        table {
        border-collapse: collapse;
        border: 1px solid black;
        }
        
        th, td {
        border: 1px solid black;
        font-family: Verdana, Arial, Helvetica, sans-serif;
        font-size: 9pt;
        font-style: normal;
        color: #3D5B96;
        text-decoration: none;
        line-height: 1.3em;
        padding-left: 15px;
        text-indent: -15px;
        padding-right: 15px;
        padding-top: 0px;
        padding-bottom: 0px;
        }
        
        th {
        background: #B1CCC7;
        font-weight: bold;
        }
        
        td {
        font-weight: normal;
        }
        
    </xsl:variable>
    
    <xsl:template match="*">
        <html>
            <head>
                <title>Analysis of {$process} + Execution Time</title>
                <style>{$style}</style>
            </head>
            <body>
                <h1>Analysis of {$process} Execution Time</h1>
                <p>Total time: {format-number(@t-total, "#0.000")} milliseconds</p>
                <h2>Time spent in each {$templateOr} function or global variable:</h2>
                <p>The table below is ordered by the total net time spent in the {$templateOr} 
                    function or global variable. Gross time means the time including called
                    {$templatesAnd} functions (recursive calls only count from the original entry);
                    net time means time excluding time spent in called {$templatesAnd} functions.</p>
                <table>
                    <thead>
                        <tr>
                            <th>module</th>
                            <th>line</th>
                            <th>instruction</th>
                            <th>count</th>
                            <th>average time (gross/ms)</th>
                            <th>total time (gross/ms)</th>
                            <th>average time (net/ms)</th>
                            <th>total time (net/ms)</th>
                        </tr>
                    </thead>
                    <tbody>
                        <xsl:for-each select="fn"> 
                            <xsl:sort select="number(@t-sum-net)" order="descending"/>
                            <tr>
                                <td><a href="{@file}">{tokenize(@file, '/')[last()]}</a></td>
                                <td align="right">{@line}</td>
                                <td>{@construct, @name, @match}</td>
                                <td align="right">{format-number(@count, ',##0')}</td>
                                <td align="right">{format-number(@t-avg, '#0.000')}</td>
                                <td align="right">{format-number(@t-sum, ',##0.000')}</td>
                                <td align="right">{format-number(@t-avg-net, '#0.000')}</td>
                                <td align="right">{format-number(@t-sum-net, ',##0.000')}</td>
                            </tr>
                        </xsl:for-each>
                    </tbody>
                </table>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
