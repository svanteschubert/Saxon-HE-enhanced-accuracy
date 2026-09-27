////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.number;


import net.sf.saxon.str.TwineBuilder;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.transpile.CSharpReplaceException;
import net.sf.saxon.value.CalendarValue;
import net.sf.saxon.value.DateTimeValue;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * This class attempts to identify a timezone name, given the date (including the time zone offset)
 * and the country. The process is heuristic: sometimes there is more than one timezone that matches
 * this information, sometimes there is none, but the class simply does its best. This is in support
 * of the XSLT format-date() function.
 */
public class NamedTimeZone {

    static Set<String> knownTimeZones = new HashSet<>(50);
    static HashMap<String, List<String>> idForCountry = new HashMap<>(50);
    static List<String> worldTimeZones = new ArrayList<>(20);

    static {
        knownTimeZones.addAll(ZoneId.getAvailableZoneIds());
    }

    /**
     * Register a timezone in use in a particular country. Note that some countries use multiple
     * time zones
     *
     * @param country the two-character code for the country
     * @param zoneId  the Olson timezone name for the timezone
     */

    static void tz(String country, String zoneId) {
        List<String> list = idForCountry.get(country);
        if (list == null) {
            list = new ArrayList<String>(4);
        }
        list.add(zoneId);
        idForCountry.put(country, list);
    }

    /**
     * Register a timezone in use in a particular country, and mark it as a major world
     * timezone so it is also recognized in other countries. Note that some countries use multiple
     * time zones
     *
     * @param country the two-character code for the country
     * @param zoneId  the Olson timezone name for the timezone
     * @param major true if this is a major world timezone, meaning that its name is recognized
     *              by people outside its own country
     */

    static void tz(String country, String zoneId, boolean major) {
        tz(country, zoneId);
        if (major) {
            worldTimeZones.add(zoneId);
        }
    }

    static {

        // The table starts with countries that use multiple timezones, then proceeds in alphabetical order

        tz("us", "America/New_York", true);
        tz("us", "America/Chicago", true);
        tz("us", "America/Denver", true);
        tz("us", "America/Los_Angeles", true);
        tz("us", "America/Anchorage", true);
        tz("us", "America/Halifax", true);
        tz("us", "Pacific/Honolulu", true);

        tz("ca", "Canada/Pacific");
        tz("ca", "Canada/Mountain");
        tz("ca", "Canada/Central");
        tz("ca", "Canada/Eastern");
        tz("ca", "Canada/Atlantic");

        tz("au", "Australia/Sydney", true);
        tz("au", "Australia/Darwin", true);
        tz("au", "Australia/Perth", true);

        tz("ru", "Europe/Moscow", true);
        tz("ru", "Europe/Samara");
        tz("ru", "Asia/Yekaterinburg");
        tz("ru", "Asia/Novosibirsk");
        tz("ru", "Asia/Krasnoyarsk");
        tz("ru", "Asia/Irkutsk");
        tz("ru", "Asia/Chita");
        tz("ru", "Asia/Vladivostok");

        tz("an", "Europe/Andorra");
        tz("ae", "Asia/Abu_Dhabi");
        tz("af", "Asia/Kabul");
        tz("al", "Europe/Tirana");
        tz("am", "Asia/Yerevan");
        tz("ao", "Africa/Luanda");
        tz("ar", "America/Buenos_Aires");
        tz("as", "Pacific/Samoa");
        tz("at", "Europe/Vienna");
        tz("aw", "America/Aruba");
        tz("az", "Asia/Baku");

        tz("ba", "Europe/Sarajevo");
        tz("bb", "America/Barbados");
        tz("bd", "Asia/Dhaka");
        tz("be", "Europe/Brussels", true);
        tz("bf", "Africa/Ouagadougou");
        tz("bg", "Europe/Sofia");
        tz("bh", "Asia/Bahrain");
        tz("bi", "Africa/Bujumbura");
        tz("bm", "Atlantic/Bermuda");
        tz("bn", "Asia/Brunei");
        tz("bo", "America/La_Paz");
        tz("br", "America/Sao_Paulo");
        tz("bs", "America/Nassau");
        tz("bw", "Gaborone");
        tz("by", "Europe/Minsk");
        tz("bz", "America/Belize");

        tz("cd", "Africa/Kinshasa");
        tz("ch", "Europe/Zurich");
        tz("ci", "Africa/Abidjan");
        tz("cl", "America/Santiago");
        tz("cn", "Asia/Shanghai");
        tz("co", "America/Bogota");
        tz("cr", "America/Costa_Rica");
        tz("cu", "America/Cuba");
        tz("cv", "Atlantic/Cape_Verde");
        tz("cy", "Asia/Nicosia");
        tz("cz", "Europe/Prague");

        tz("de", "Europe/Berlin");
        tz("dj", "Africa/Djibouti");
        tz("dk", "Europe/Copenhagen");
        tz("do", "America/Santo_Domingo");
        tz("dz", "Africa/Algiers");

        tz("ec", "America/Quito");
        tz("ee", "Europe/Tallinn");
        tz("eg", "Africa/Cairo");
        tz("er", "Africa/Asmara");
        tz("es", "Europe/Madrid");

        tz("fi", "Europe/Helsinki");
        tz("fj", "Pacific/Fiji");
        tz("fk", "America/Stanley");
        tz("fr", "Europe/Paris");

        tz("ga", "Africa/Libreville");
        tz("gb", "Europe/London");
        tz("gd", "America/Grenada");
        tz("ge", "Asia/Tbilisi");
        tz("gh", "Africa/Accra");
        tz("gm", "Africa/Banjul");
        tz("gn", "Africa/Conakry");
        tz("gr", "Europe/Athens");
        tz("gy", "America/Guyana");

        tz("hk", "Asia/Hong_Kong");
        tz("hn", "America/Tegucigalpa");
        tz("hr", "Europe/Zagreb");
        tz("ht", "America/Port-au-Prince");
        tz("hu", "Europe/Budapest");

        tz("id", "Asia/Jakarta");
        tz("ie", "Europe/Dublin");
        tz("il", "Asia/Tel_Aviv", true);
        tz("in", "Asia/Calcutta", true);
        tz("iq", "Asia/Baghdad");
        tz("ir", "Asia/Tehran");
        tz("is", "Atlantic/Reykjavik");
        tz("it", "Europe/Rome");

        tz("jm", "America/Jamaica");
        tz("jo", "Asia/Amman");
        tz("jp", "Asia/Tokyo", true);

        tz("ke", "Africa/Nairobi");
        tz("kg", "Asia/Bishkek");
        tz("kh", "Asia/Phnom_Penh");
        tz("kp", "Asia/Pyongyang");
        tz("kr", "Asia/Seoul");
        tz("kw", "Asia/Kuwait");

        tz("lb", "Asia/Beirut");
        tz("li", "Europe/Liechtenstein");
        tz("lk", "Asia/Colombo");
        tz("lr", "Africa/Monrovia");
        tz("ls", "Africa/Maseru");
        tz("lt", "Europe/Vilnius");
        tz("lu", "Europe/Luxembourg");
        tz("lv", "Europe/Riga");
        tz("ly", "Africa/Tripoli");

        tz("ma", "Africa/Rabat");
        tz("mc", "Europe/Monaco");
        tz("md", "Europe/Chisinau");
        tz("mg", "Indian/Antananarivo");
        tz("mk", "Europe/Skopje");
        tz("ml", "Africa/Bamako");
        tz("mm", "Asia/Rangoon");
        tz("mn", "Asia/Ulaanbaatar");
        tz("mo", "Asia/Macao");
        tz("mq", "America/Martinique");
        tz("mt", "Europe/Malta");
        tz("mu", "Indian/Mauritius");
        tz("mv", "Indian/Maldives");
        tz("mw", "Africa/Lilongwe");
        tz("mx", "America/Mexico_City");
        tz("my", "Asia/Kuala_Lumpur");

        tz("na", "Africa/Windhoek");
        tz("ne", "Africa/Niamey");
        tz("ng", "Africa/Lagos");
        tz("ni", "America/Managua");
        tz("nl", "Europe/Amsterdam");
        tz("no", "Europe/Oslo");
        tz("np", "Asia/Kathmandu");
        tz("nz", "Pacific/Aukland");

        tz("om", "Asia/Muscat");

        tz("pa", "America/Panama");
        tz("pe", "America/Lima");
        tz("pg", "Pacific/Port_Moresby");
        tz("ph", "Asia/Manila");
        tz("pk", "Asia/Karachi");
        tz("pl", "Europe/Warsaw");
        tz("pr", "America/Puerto_Rico");
        tz("pt", "Europe/Lisbon");
        tz("py", "America/Asuncion");

        tz("qa", "Asia/Qatar");

        tz("ro", "Europe/Bucharest");
        tz("rs", "Europe/Belgrade");

        tz("rw", "Africa/Kigali");

        tz("sa", "Asia/Riyadh");
        tz("sd", "Africa/Khartoum");
        tz("se", "Europe/Stockholm");
        tz("sg", "Asia/Singapore");
        tz("si", "Europe/Ljubljana");
        tz("sk", "Europe/Bratislava");
        tz("sl", "Africa/Freetown");
        tz("so", "Africa/Mogadishu");
        tz("sr", "America/Paramaribo");
        tz("sv", "America/El_Salvador");
        tz("sy", "Asia/Damascus");
        tz("sz", "Africa/Mbabane");

        tz("td", "Africa/Ndjamena");
        tz("tg", "Africa/Lome");
        tz("th", "Asia/Bangkok");
        tz("tj", "Asia/Dushanbe");
        tz("tm", "Asia/Ashgabat");
        tz("tn", "Africa/Tunis");
        tz("to", "Pacific/Tongatapu");
        tz("tr", "Asia/Istanbul");
        tz("tw", "Asia/Taipei");
        tz("tz", "Africa/Dar_es_Salaam");

        tz("ua", "Europe/Kiev");
        tz("ug", "Africa/Kampala");
        tz("uk", "Europe/London", true);
        tz("uy", "America/Montevideo");
        tz("uz", "Asia/Tashkent");

        tz("ve", "America/Caracas");
        tz("vn", "Asia/Hanoi");

        tz("za", "Africa/Johannesburg");
        tz("zm", "Africa/Lusaka");
        tz("zw", "Africa/Harare");


    }

    /**
     * Try to identify a timezone name corresponding to a given date (including time zone)
     * and a given country. Note that this takes account of Java's calendar of daylight savings time
     * changes in different countries. The returned value is the convenional short timezone name, for example
     * PDT for Pacific Daylight Time
     *
     * @param date  the dateTimeValue, including timezone
     * @param place either a two-letter ISO country code or an Olson timezone name
     * @return the short timezone name if a timezone with the given time displacement is in use in the country
     * in question (on the appropriate date, if known). Otherwise, the formatted (numeric) timezone offset. If
     * the dateTimeValue supplied has no timezone, return a zero-length string.
     */

    //@CSharpReplaceBody(code="return formatTimeZoneOffset(date);")
    public static String getTimeZoneNameForDate(DateTimeValue date, /*@Nullable*/ String place) {
        if (!date.hasTimezone()) {
            return "";
        }
        ZoneId referenceTimezone = null;
        if (place.startsWith("America/")) {
            place = "us";
        }
        switch (place) {
            case "us":
                referenceTimezone = ZoneId.of("America/New_York");
                break;
            case "uk":
            case "gb":
                referenceTimezone = ZoneId.of("Europe/London");
                break;

        }
        if (referenceTimezone == null && place.startsWith("Europe/")) {
            referenceTimezone = ZoneId.of(place);
        }
        if (referenceTimezone == null) {
            return formatTimeZoneOffset(date);
        }
        boolean summerTime = inDaylightTime(referenceTimezone, date.secondsSinceEpoch().longValue());
        int tzMinutes = date.getTimezoneInMinutes();

        if (summerTime) {
            switch (tzMinutes) {
                case 330:
                    return "IST";
                case 120:
                    return "CEST";
                case 60:
                    return "BST";
                case 0:
                    return "GMT";
                case -240:
                    return "EDT";
                case -300:
                    return "CDT";
                case -360:
                    return "MDT";
                case -420:
                    return "PDT";
                case -480:
                    return "AKDT";
                case -540:
                    return "HDT";
                default:
                    return formatTimeZoneOffset(date);
            }
        } else {
            switch (tzMinutes) {
                case 330:
                    return "IST";
                case 60:
                    return "CET";
                case 0:
                    return "GMT";
                case -300:
                    return "EST";
                case -360:
                    return "CST";
                case -420:
                    return "MST";
                case -480:
                    return "PST";
                case -540:
                    return "AKST";
                case -600:
                    return "HST";
                default:
                    return formatTimeZoneOffset(date);
            }
        }

    }

    /**
     * Format a timezone in numeric form for example +03:00 (or Z for +00:00)
     *
     * @param timeValue the value whose timezone is to be formatted
     * @return the formatted timezone
     */

    public static String formatTimeZoneOffset(DateTimeValue timeValue) {
        TwineBuilder tb = TwineBuilder.make(16);;
        tb = CalendarValue.appendTimezone(timeValue.getTimezoneInMinutes(), tb);
        return tb.toString();
    }

    /**
     * Try to identify a timezone name corresponding to a given date (including time zone)
     * and a given country. Note that this takes account of Java's calendar of daylight savings time
     * changes in different countries. The returned value is the Olson time zone name, for example
     * "Pacific/Los_Angeles", followed by an asterisk (*) if the time is in daylight savings time in that
     * timezone.
     *
     * @param date    the dateTimeValue, including timezone
     * @param country the country, as a two-letter code
     * @return the Olson timezone name if a timezone with the given time displacement is in use in the country
     * in question (on the appropriate date, if known). In this case an asterisk is appended to the result if the
     * date/time is in daylight savings time. Otherwise, the formatted (numeric) timezone offset. If
     * the dateTimeValue supplied has no timezone, return a zero-length string.
     */
    
    public static String getOlsonTimeZoneName(DateTimeValue date, String country) {
        if (!date.hasTimezone()) {
            return "";
        }
        List<String> possibleIds = idForCountry.get(country.toLowerCase());
        String exampleId;
        if (possibleIds == null) {
            return formatTimeZoneOffset(date);
        } else {
            exampleId = possibleIds.get(0);
        }
        ZoneId exampleZone = ZoneId.of(exampleId);
        boolean inSummerTime = inDaylightTime(exampleZone, date.secondsSinceEpoch().longValue());
        int tzMinutes = date.getTimezoneInMinutes();
        for (String olson : possibleIds) {
            ZoneId possibleTimeZone = ZoneId.of(olson);
            int offsetSeconds = getOffsetInSecondsAtDateTime(possibleTimeZone, date);
            if (offsetSeconds == tzMinutes * 60) {
                return inSummerTime ? olson + "*" : olson;
            }
        }
        return formatTimeZoneOffset(date);
    }

    /**
     * Determine whether a given date/time is in summer time (daylight savings time)
     * in a given region. This relies on the Java database of changes to daylight savings time.
     * Since summer time changes are set by civil authorities the information is not necessarily
     * reliable when applied to dates in the future.
     *
     * @param date   the date/time in question
     * @param region either the two-letter ISO country code, or an Olson timezone name such as
     *               "America/New_York" or "Europe/Lisbon". If the country code denotes a country spanning several
     *               timezones, such as the US, then one of them is chosen arbitrarily.
     * @return true if the date/time is known to be in summer time in the relevant country;
     * false if it is known not to be in summer time; null if there is no timezone or if no
     * information is available for the specified region.
     */

    public static Optional<Boolean> inSummerTime(DateTimeValue date, String region) {
        String olsonName;
        if (region.length() == 2) {
            List<String> possibleIds = idForCountry.get(region.toLowerCase());
            if (possibleIds == null) {
                return Optional.empty();
            } else {
                olsonName = possibleIds.get(0);
            }
        } else {
            olsonName = region;
        }
        ZoneId zone = olsonZoneOrUtc(olsonName);
        return Optional.of(inDaylightTime(zone, date.secondsSinceEpoch().longValue()));
    }

    @CSharpReplaceException(from="java.time.DateTimeException", to="System.Exception")
    private static ZoneId olsonZoneOrUtc(String olsonName) {
        try {
            return ZoneId.of(olsonName);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    @CSharpReplaceBody(code="return zone.inDaylightTime(secondsSinceEpoch);")
    private static boolean inDaylightTime(ZoneId zone, long secondsSinceEpoch) {
        return zone.getRules().isDaylightSavings(Instant.ofEpochSecond(secondsSinceEpoch));
    }

    /**
     * Get the civil time offset to be made to a given date/time in a given
     * civil timezone. For example, if the timezone is America/New_York, the civil time
     * offset will be -5 hours (= 5 x 3600000 ms) during the winter and -4 hours
     * (=4 x 3600000 ms) during the summer
     *
     * @param date      the date/time in question. If this has no timezone, it is assumed
     *                  to be in GMT.
     * @param olsonName the Olson (or IANA) name of the timezone, for example Europe/Lisbon
     * @return the civil time offset, in seconds, to be applied to the given
     * date/time. Returns zero if the timezone is not recognized.
     */

    public static int civilTimeOffsetInSeconds(DateTimeValue date, String olsonName) {
        ZoneId zone = olsonZoneOrUtc(olsonName);
        return getOffsetInSecondsAtDateTime(zone, date);
    }

    /**
     * Get the TimeZone object for a given Olson (=IANA) timezone name
     *
     * @param olsonName an Olson (or IANA) timezone name, for example "Europe/London"
     * @return the corresponding ZoneId object, or null if not available
     */

    public static ZoneId getNamedTimeZone(String olsonName) {
        if (knownTimeZones.contains(olsonName)) {
            return ZoneId.of(olsonName);
        } else {
            return null;
        }
    }

    /**
     * Get the time zone offset relative to UTC, in seconds, at a given point
     * in time, provided as a DateTimeValue
     * @param zone the timezone as a ZoneId
     * @param dateTime the time at which the offset is required
     * @return the offset relative to UTC at that time, in seconds
     */

    @CSharpReplaceBody(code="return zone.getOffsetInSecondsAtDateTime(dateTime);")
    private static int getOffsetInSecondsAtDateTime(ZoneId zone, DateTimeValue dateTime) {
        return zone.getRules().getOffset(dateTime.toJavaInstant()).getTotalSeconds();
    }

}

