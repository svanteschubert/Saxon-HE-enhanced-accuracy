////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the function parse-ietf-date(), which is a standard function in XPath 3.1
 */

public class ParseIetfDate extends SystemFunction implements Callable {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequence objects
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue stringValue = (StringValue) arguments[0].head();
        if (stringValue == null) {
            return ZeroOrOne.empty();
        }
        return new ZeroOrOne(parse(stringValue.getStringValue(), context));
    }

    private String[] dayNames = new String[]{
        "Mon","Tue","Wed","Thu","Fri","Sat","Sun","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"
    };

    private boolean isDayName(String string){
        for (String s: dayNames){
            if (s.equalsIgnoreCase(string)){
                return true;
            }
        }
        return false;
    }

    private String[] monthNames = new String[]{
            "Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"
    };

    private boolean isMonthName(String string){
        for (String s: monthNames){
            if (s.equalsIgnoreCase(string)){
                return true;
            }
        }
        return false;
    }

    private byte getMonthNumber(String string){
        if ("Jan".equalsIgnoreCase(string)){
            return (byte) 1;
        } else if ("Feb".equalsIgnoreCase(string)){
            return (byte) 2;
        } else if ("Mar".equalsIgnoreCase(string)){
            return (byte) 3;
        } else if ("Apr".equalsIgnoreCase(string)){
            return (byte) 4;
        } else if ("May".equalsIgnoreCase(string)){
            return (byte) 5;
        } else if ("Jun".equalsIgnoreCase(string)){
            return (byte) 6;
        } else if ("Jul".equalsIgnoreCase(string)){
            return (byte) 7;
        } else if ("Aug".equalsIgnoreCase(string)){
            return (byte) 8;
        } else if ("Sep".equalsIgnoreCase(string)){
            return (byte) 9;
        } else if ("Oct".equalsIgnoreCase(string)){
            return (byte) 10;
        } else if ("Nov".equalsIgnoreCase(string)){
            return (byte) 11;
        } else if ("Dec".equalsIgnoreCase(string)){
            return (byte) 12;
        }
        return (byte) 0;
    }

    private int requireDSep(List<String> tokens, int i, String input) throws XPathException {
        boolean found = false;
        if (" ".equals(tokens.get(i))){
            i++;
            found = true;
        }
        if ("-".equals(tokens.get(i))){
            i++;
            found = true;
        }
        if (" ".equals(tokens.get(i))){
            i++;
            found = true;
        }
        if (!found) {
            badDate("Date separator missing", input);
        }
        return i;
    }

    private static void badDate(String msg, String value) throws XPathException{
        XPathException err = new XPathException(
                "Invalid IETF date value " + value + " (" + msg + ")");
        err.setErrorCode("FORG0010");
        throw err;
    }

    private String[] timezoneNames = new String[]{
            "UT","UTC","GMT","EST","EDT","CST","CDT","MST","MDT","PST","PDT"
    };

    private boolean isTimezoneName(String string) {
        for (String s: timezoneNames){
            if (s.equalsIgnoreCase(string)){
                return true;
            }
        }
        return false;
    }

    private int getTimezoneOffsetFromName(String string){
        if ("UT".equalsIgnoreCase(string)|"UTC".equalsIgnoreCase(string)|"GMT".equalsIgnoreCase(string)){
            return 0;
        } else if ("EST".equalsIgnoreCase(string)){
            return -5*60;
        } else if ("EDT".equalsIgnoreCase(string)){
            return -4*60;
        } else if ("CST".equalsIgnoreCase(string)){
            return -6*60;
        } else if ("CDT".equalsIgnoreCase(string)){
            return -5*60;
        } else if ("MST".equalsIgnoreCase(string)){
            return -7*60;
        } else if ("MDT".equalsIgnoreCase(string)){
            return -6*60;
        } else if ("PST".equalsIgnoreCase(string)){
            return -8*60;
        } else if ("PDT".equalsIgnoreCase(string)){
            return -7*60;
        }
        return 0; /* what should this return? */
    }

    /**
     * Parse a supplied string to obtain a dateTime
     *
     * @param input     a string containing the date and time in IETF format
     * @param context   the XPath context
     * @return either a DateTimeValue representing the input supplied, or a ValidationFailure if
     *         the input string was invalid
     */

    public DateTimeValue parse(String input, XPathContext context) throws XPathException {
        List<String> tokens = tokenize(input);
        int year = 0;
        byte month = 0;
        byte day = 0;
        List<TimeValue> timeValue = new ArrayList<>();
        int i = 0;
        String currentToken = tokens.get(i);
        if (currentToken.matches("[A-Za-z]+") && isDayName(currentToken)){
            currentToken = tokens.get(++i);
            if (",".equals(currentToken)){
                currentToken = tokens.get(++i);
            }
            if (!" ".equals(currentToken)){
                badDate("Space missing after day name", input);
            }
            currentToken = tokens.get(++i);
                /* Now expect either day number or month name */
        }
        if (isMonthName(currentToken)){
            month = getMonthNumber(currentToken);
            i = requireDSep(tokens, i+1, input);
            currentToken = tokens.get(i);
            if (!currentToken.matches("[0-9]+")){
                badDate("Day number expected after month name", input);
            }
            if (currentToken.length() > 2){
                badDate("Day number exceeds two digits", input);
            }
            day = (byte) Integer.parseInt(currentToken);
            currentToken = tokens.get(++i);
            if (!" ".equals(currentToken)){
                badDate("Space missing after day number", input);
            }
                /* Now expect time string */
            i = parseTime(tokens, ++i, timeValue, input);
            currentToken = tokens.get(++i);
            if (!" ".equals(currentToken)){
                badDate("Space missing after time string", input);
            }
            currentToken = tokens.get(++i);
            if (currentToken.matches("[0-9]+")) {
                year = checkTwoOrFourDigits(input, currentToken);
            } else {
                badDate("Year number expected after time", input);
            }
        }
        else if (currentToken.matches("[0-9]+")) {
            if (currentToken.length() > 2){
                badDate("First number in string expected to be day in two digits", input);
            }
            day = (byte) Integer.parseInt(currentToken);
            i = requireDSep(tokens, ++i, input);
            currentToken = tokens.get(i);
            if (!isMonthName(currentToken)){
                badDate("Abbreviated month name expected after day number", input);
            }
            month = getMonthNumber(currentToken);
            i = requireDSep(tokens, ++i, input);
            currentToken = tokens.get(i);
            if (currentToken.matches("[0-9]+")) {
                year = checkTwoOrFourDigits(input, currentToken);
            } else {
                badDate("Year number expected after month name", input);
            }
            currentToken = tokens.get(++i);
            if (!" ".equals(currentToken)){
                badDate("Space missing after year number", input);
            }
                /* Now expect time string ("after..." may differ) */
            i = parseTime(tokens, ++i, timeValue, input);
        }
        else {
            badDate("String expected to begin with month name or day name (or day number)", input);
        }
        if (!GDateValue.isValidDate(year, month, day)) {
            badDate("Date is not valid", input);
        }
        currentToken = tokens.get(++i);
        if (!currentToken.equals(EOF)){
            badDate("Extra content found in string after date", input);
        }
        DateValue date = new DateValue(year, month, day);
        TimeValue time = timeValue.get(0);
        if (time.getHour() == 24) {
            date = DateValue.tomorrow(date.getYear(), date.getMonth(), date.getDay());
            time = new TimeValue((byte) 0, (byte) 0, (byte) 0, 0, time.getTimezoneInMinutes(), "");
        }
        return DateTimeValue.makeDateTimeValue(date, time);
    }

    private int checkTwoOrFourDigits(String input, String currentToken) throws XPathException {
        int year;
        if (currentToken.length() == 4){
            year = Integer.parseInt(currentToken);
        } else if (currentToken.length() == 2){
            year = Integer.parseInt(currentToken) +1900;
        } else {
            badDate("Year number must be two or four digits", input);
            year = 0;
        }
        return year;
    }

    /**
     * Parse part of a string (already tokenized) to obtain a TimeValue
     *
     * @param tokens    tokenized string containing the date and time in IETF format
     * @param currentPosition   index of current token
     * @param result    TimeValue produced from parsing time from tokens
     * @return  index of token after parsing the time
     */

    public int parseTime(List<String> tokens, int currentPosition, List<TimeValue> result, String input) throws XPathException{
        byte hour;
        byte minute;
        byte second = 0;
        int microsecond = 0; /*the number of microseconds, 0-999999*/
        int tz = 0; /*the timezone displacement in minutes from UTC.*/
        int i = currentPosition;
        int n = currentPosition; /* the final token index, returned by the method */
        StringBuilder currentToken = new StringBuilder(tokens.get(i));
        if (!currentToken.toString().matches("[0-9]+")){
            badDate("Hour number expected", input);
        }
        if (currentToken.length() > 2){
            badDate("Hour number exceeds two digits", input);
        }
        hour = (byte) Integer.parseInt(currentToken.toString());
        currentToken = new StringBuilder(tokens.get(++i));
        if (!":".equals(currentToken.toString())){
            badDate("Separator ':' missing after hour", input);
        }
        currentToken = new StringBuilder(tokens.get(++i));
        if (!currentToken.toString().matches("[0-9]+")){
            badDate("Minutes expected after hour", input);
        }
        if (currentToken.length() != 2){
            badDate("Minutes must be exactly two digits", input);
        }
        minute = (byte) Integer.parseInt(currentToken.toString());
        currentToken = new StringBuilder(tokens.get(++i));
        boolean finished = false;

        if (currentToken.toString().equals(EOF)){
        /* seconds, microseconds, timezones not given*/
            n = i - 1;
            finished = true;
        }
        else if (":".equals(currentToken.toString())){
            currentToken = new StringBuilder(tokens.get(++i));
            if (!currentToken.toString().matches("[0-9]+")){
                badDate("Seconds expected after ':' separator after minutes", input);
            }
            if (currentToken.length() != 2){
                badDate("Seconds number must have exactly two digits (before decimal point)", input);
            }
            second = (byte) Integer.parseInt(currentToken.toString());
            currentToken = new StringBuilder(tokens.get(++i));
            if (currentToken.toString().equals(EOF)){
            /* microseconds, timezones not given*/
                n = i - 1;
                finished = true;
            }
            else if (".".equals(currentToken.toString())){
                currentToken = new StringBuilder(tokens.get(++i));
                if (!currentToken.toString().matches("[0-9]+")){
                    badDate("Fractional part of seconds expected after decimal point", input);
                }
                int len = Math.min(6, currentToken.length());
                currentToken = new StringBuilder(currentToken.substring(0, len));
                while (currentToken.length() < 6){
                    currentToken.append("0");
                }
                microsecond = Integer.parseInt(currentToken.toString());
                if (i < tokens.size() - 1) {
                    currentToken = new StringBuilder(tokens.get(++i));
                }
            }
        }
        if (!finished) {
            if (" ".equals(currentToken.toString())) {
                currentToken = new StringBuilder(tokens.get(++i));
                if (currentToken.toString().matches("[0-9]+")) {
                /* no timezone is given in the time, we must have reached a year */
                    n = i - 2;
                    finished = true;
                }
            }
            if (!finished) {
                if (currentToken.toString().matches("[A-Za-z]+")){
                    if (!isTimezoneName(currentToken.toString())){
                        badDate("Timezone name not recognised", input);
                    }
                    tz = getTimezoneOffsetFromName(currentToken.toString());
                    n = i;
                    finished = true;

                } else if ("+".equals(currentToken.toString())|"-".equals(currentToken.toString())) {
                    String sign = currentToken.toString();
                    int tzOffsetHours = 0;
                    int tzOffsetMinutes = 0;
                    currentToken = new StringBuilder(tokens.get(++i));
                    if (!currentToken.toString().matches("[0-9]+")){
                        badDate("Parsing timezone offset, number expected after '" + sign + "'", input);
                    }
                    int tLength = currentToken.length();
                    if (tLength > 4){
                        badDate("Timezone offset does not have the correct number of digits", input);
                    }
                    else if (tLength >= 3){
                        tzOffsetHours = Integer.parseInt(currentToken.substring(0, tLength - 2));
                        tzOffsetMinutes = Integer.parseInt(currentToken.substring(tLength - 2, tLength));
                        currentToken = new StringBuilder(tokens.get(++i));
                    }
                    else {
                        tzOffsetHours = Integer.parseInt(currentToken.toString());
                        currentToken = new StringBuilder(tokens.get(++i));
                        if (":".equals(currentToken.toString())) {
                            currentToken = new StringBuilder(tokens.get(++i));
                            if (currentToken.toString().matches("[0-9]+")){
                                if (currentToken.length() != 2) {
                                    badDate("Parsing timezone offset, minutes must be two digits", input);
                                } else {
                                    tzOffsetMinutes = Integer.parseInt(currentToken.toString());
                                }
                                currentToken = new StringBuilder(tokens.get(++i));
                            }
                        }
                    }
                    if (tzOffsetMinutes > 59) {
                        badDate("Timezone offset minutes out of range", input);
                    }
                    tz = tzOffsetHours * 60 + tzOffsetMinutes;
                    if (sign.equals("-")){
                        tz = -tz;
                    }
                    if (currentToken.toString().equals(EOF)){
                        n = i - 1;
                        finished = true;
                    }
                    else if (" ".equals(currentToken.toString())) {
                        currentToken = new StringBuilder(tokens.get(++i));
                        if (currentToken.toString().matches("[0-9]+")) {
                        /* we must have reached the year */
                            n = i - 2;
                            finished = true;
                        }
                    }
                    if (!finished && "(".equals(currentToken.toString())) {
                        currentToken = new StringBuilder(tokens.get(++i));
                        if (" ".equals(currentToken.toString())) {
                            currentToken = new StringBuilder(tokens.get(++i));
                        }
                        if (!currentToken.toString().matches("[A-Za-z]+")) {
                            badDate("Timezone name expected after '('", input);
                        }
                        else if (currentToken.toString().matches("[A-Za-z]+")) {
                            if (!isTimezoneName(currentToken.toString())){
                                badDate("Timezone name not recognised", input);
                            }
                            currentToken = new StringBuilder(tokens.get(++i));
                        }
                        if (" ".equals(currentToken.toString())) {
                            currentToken = new StringBuilder(tokens.get(++i));
                        }
                        if (!")".equals(currentToken.toString())){
                            badDate("Expected ')' after timezone name", input);
                        }
                        n = i;
                        finished = true;
                    }
                    else if (!finished) {
                        badDate("Unexpected content after timezone offset", input);
                    }
                } else {
                    badDate("Unexpected content in time (after minutes)", input);
                }
            }
        }
        if (!finished) {
            throw new AssertionError("Should have finished");
        }
        if (!isValidTime(hour, minute, second, microsecond, tz)) {
            badDate("Time/timezone is not valid", input);
        }
        TimeValue timeValue = new TimeValue(hour, minute, second, microsecond*1000, tz, "");
        result.add(timeValue);
        return n;
    }

    /**
     * Determine whether a given time is valid
     *
     * @param hour  the hour (0-24)
     * @param minute the minute (0-59)
     * @param second   the second (0-59)
     * @param microsecond   the microsecond (0-999999)
     * @param tz   the timezone displacement in minutes from UTC
     * @return true if this is a valid time
     */

    public static boolean isValidTime(int hour, int minute, int second, int microsecond, int tz) {
        return (hour >= 0 && hour <= 23 && minute >= 0 && minute < 60 && second >= 0 && second < 60
                && microsecond >= 0 && microsecond < 1000000
                || hour == 24 && minute == 0 && second == 0 && microsecond == 0)
                && tz >= -14 * 60 && tz <= 14 * 60;
    }



    private List<String> tokenize(String input) throws XPathException{
        List<String> tokens = new ArrayList<>();
        input = input.trim();
        if (input.isEmpty()){
            badDate("Input is empty",input);
            return tokens;
        }
        int i = 0;
        input = input + (char) 0;
        while (true) {
            char c = input.charAt(i);
            if (c == 0) {
                tokens.add(EOF);
                return tokens;
            }
            if (Whitespace.isWhite(c)){
                int j = i;
                while (Whitespace.isWhite(input.charAt(j++))){}
                tokens.add(" ");
                i = j-1;
            } else if (Character.isLetter(c)){
                int j = i;
                while (Character.isLetter(input.charAt(j++))){}
                tokens.add(input.substring(i,j-1));
                i = j-1;
            } else if (Character.isDigit(c)){
                int j = i;
                while (Character.isDigit(input.charAt(j++))){}
                tokens.add(input.substring(i,j-1));
                i = j-1;
            } else {
                tokens.add(input.substring(i,i+1));
                i++;
            }
        }
    }

    private static final String EOF = "";
}


// Copyright (c) 2018-2020 Saxonica Limited
