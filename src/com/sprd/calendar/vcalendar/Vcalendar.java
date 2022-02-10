/* SPRD: for bug473564, add backup info @{ */
package com.sprd.calendar.vcalendar;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.TimeZone;

import org.apache.commons.codec.DecoderException;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * The calendar event handler class
 *
 * @author linying
 *
 */
public class Vcalendar {
    public static final String TAG = "Vcalendar";
    private Context mContext;
    public static final Charset UTF_8 = Charset.forName("UTF-8");
    private final ByteArrayOutputStream mOut;

    private static byte ESCAPE_CHAR = '=';
    boolean isGBK = false;
    boolean isUTF = false;

    static final String[] sTwoCharacterNumbers =
            new String[] {
                    "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"
            };

    /**
     * The digital front 10 supplement 0
     *
     * @param num number
     * @return String
     */
    static String formatTwo(int num) {
        if (num <= 12) {
            return sTwoCharacterNumbers[num];
        } else {
            return Integer.toString(num);
        }
    }

    /**
     * get TimeZone
     *
     * @param offsetMinutes  Time offset value
     * @return TimeZone
     */
    static String utcOffsetString(int offsetMinutes) {
        StringBuilder sb = new StringBuilder();
        int hours = offsetMinutes / 60;
        if (hours < 0) {
            sb.append('-');
            hours = 0 - hours;
        } else {
            sb.append('+');
        }
        int minutes = offsetMinutes % 60;
        if (hours < 10) {
            sb.append('0');
        }
        sb.append(hours);
        if (minutes < 10) {
            sb.append('0');
        }
        sb.append(minutes);
        return sb.toString();
    }

    /**
     * Convert time to milliseconds
     *
     * @param VCalendarTime time
     * @param timezone  TimeZone
     * @param dst Whether the daylight-saving time
     * @return milliseconds
     */
    String transitionMillisToVCalendarTime(long millis, TimeZone tz, boolean dst) {
        StringBuilder sb = new StringBuilder();

        GregorianCalendar cal = new GregorianCalendar(tz);
        cal.setTimeInMillis(millis);
        int zoneOffset = cal.get(java.util.Calendar.ZONE_OFFSET);
        int dstOffset = cal.get(java.util.Calendar.DST_OFFSET);
        cal.add(java.util.Calendar.MILLISECOND, -(zoneOffset + dstOffset));

        sb.append(cal.get(Calendar.YEAR));
        sb.append(formatTwo(cal.get(Calendar.MONTH) + 1));
        sb.append(formatTwo(cal.get(Calendar.DAY_OF_MONTH)));
        sb.append('T');
        sb.append(formatTwo(cal.get(Calendar.HOUR_OF_DAY)));
        sb.append(formatTwo(cal.get(Calendar.MINUTE)));
        sb.append(formatTwo(cal.get(Calendar.SECOND)));
        sb.append('Z');

        return sb.toString();
    }

    /**
     * Convert time to milliseconds
     *
     * @param VCalendarTime time
     * @param timezone  TimeZone
     * @return milliseconds
     */
    long transitionVCalendarTimeToMillis(String VCalendarTime, TimeZone timezone) {
        if (TextUtils.isEmpty(VCalendarTime)) {
            return 0;
        }

        String date = VCalendarTime;
        if (date.length() == 8) {
            date = date + "T000000Z";
        }

        GregorianCalendar cal = new GregorianCalendar(Integer.parseInt(date.substring(0, 4)),
                Integer.parseInt(date.substring(4, 6)) - 1, Integer.parseInt(date.substring(6, 8)),
                Integer.parseInt(date.substring(9, 11)), Integer.parseInt(date.substring(11, 13)),
                Integer.parseInt(date.substring(13, 15)));

        cal.setTimeZone(timezone);
        return cal.getTimeInMillis();
    }

    /** Build a String from UTF-8 bytes */
    public static String fromUtf8(byte[] b) {
        if (b == null) {
            return "";
        }
        try {
            final CharBuffer cb = UTF_8.decode(ByteBuffer.wrap(b));
            return new String(cb.array(), 0, cb.length());
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    public Vcalendar(Context paramContext) {
        this.mContext = paramContext;
        this.mOut = new ByteArrayOutputStream();
    }

    /**
     * read the data to an array of bytes
     *
     * @param in InputStream
     * @return datas
     */
    public byte[] readFileSdcard(InputStream in) {
        byte[] buffer = null;
        try {
            int length = in.available();
            buffer = new byte[length];
            in.read(buffer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    Log.d(TAG, "------------readFileSdcard close error~");
                    Log.d(TAG, "exception: " + e.toString());
                }
            }
        }
        return buffer;
    }


    /**
     * Character decoding, get the string's bytes and converted to string after converted to ASCII
     *
     * @param str The source string
     * @return  Posttranslational character
     * @throws DecoderException exception
     */
    private String decodeQuotedPrintable(String str) throws DecoderException {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        byte[] bytes;

        try {
            bytes = str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Charset " + "UTF-8" + " cannot be used. "
                    + "Try default charset");
            bytes = str.getBytes();
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i];
            if (b == ESCAPE_CHAR) {
                try {
                    int u = Character.digit((char) bytes[++i], 16);
                    int l = Character.digit((char) bytes[++i], 16);
                    if (u == -1 || l == -1) {
                        throw new DecoderException("Invalid quoted-printable encoding");
                    }
                    buffer.write((char) ((u << 4) + l));
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new DecoderException("Invalid quoted-printable encoding");
                }
            } else {
                buffer.write(b);
            }
        }
        return buffer.toString();
    }

    /* SPRD: Modify for bug 756694, the event info appears garbled. @{ */
    private String decodeQuotedPrintableNormal(String str) throws DecoderException {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        byte[] bytes;

        try {
            bytes = str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Charset " + "UTF-8" + " cannot be used. "
                    + "Try default charset");
            bytes = str.getBytes();
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i];
            buffer.write(b);
        }
        return buffer.toString();
    }

    private String decodeQuotedPrintableForGBK(String str){
        String decodeString = null;
        if(str.startsWith("=")){
            decodeString = HexadecimalConver.decode(str.replaceAll("=", ""));
        } else {
            try {
                decodeString = decodeQuotedPrintableNormal(str);
            } catch(Exception e) {
                decodeString = "";
            }
        }
        return decodeString;
    }
    /* @} */

    private static class UnterminatedBlockException extends IOException { // UNISOC: Modify for bug1219661
        private static final long serialVersionUID = 1L;

        UnterminatedBlockException(String name) {
            super(name);
        }
    }
    /* SPRD modify for bug 644208 {@ */
    private boolean isNextItem(String line) {
        if (line.startsWith("LOCATION") || line.startsWith("DTEND") || line.startsWith("SUMMARY")
            || line.startsWith("HASATTENDEE")  || line.startsWith("ATTACH")) {
            return true;
        }

        int pos = line.indexOf(':');
        if (pos > 0 && pos < line.length()) {
            String tempStr = line.substring(0, pos);

            pos = tempStr.indexOf(';');
            if (pos > 0 && pos < tempStr.length()) {
                tempStr = tempStr.substring(0, pos);
            }
            int len = tempStr.length();
            for (int i = 0; i < len; i++) {
                if(!Character.isUpperCase(tempStr.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    /* @} */
    /**
     * Parsing file to the corresponding data collection
     *
     * @author linying
     *
     */
    private class BlockHash {
        String name;

        ArrayList<HashMap<String, String>> hashList = new ArrayList<HashMap<String, String>>();


        // SPRD 565043
        private String calendarDue;

        BlockHash(String _name, BufferedReader reader) throws IOException {
            name = _name;
            String lastField = null;
            String lastValue = null;
            String preline = null;
            boolean descriptionFlag = true;
            boolean descriptionStr = false;
            boolean notSaveFlag = false; //SPRD modify for bug 644208
            HashMap<String, String> hash = new HashMap<String, String>();
            while (true) {
                // Get a line; we're done if it's null
                String line = reader.readLine();
                if (line == null) {
                    throw new UnterminatedBlockException(name);
                }

                /* SPRD for bug536574, some of the description are lost. @{ */
                if (descriptionStr) {
                    if (isNextItem(line)) {
                        lastValue = lastValue.replace("\\n", "");
                        hash.put("EVENTDESCRIPTION", lastValue);
                        descriptionStr = false;
                    } else {
                        lastValue = lastValue + "\n" + line;
                        continue;
                    }
                }
                /* @} */

                // fix bug 91122 on 20121115 begin
                if (line.endsWith("=")) {
                    /* SPRD modify for bug 644208 {@ */
                    if (!notSaveFlag){
                        if (preline != null) {
                            line = preline.substring(0, preline.length() - 1) + line;
                        }
                        preline = line;
                    } else {
                        notSaveFlag = false;
                        Log.d(TAG, "[End]do not save to hash.  line str:" + line);
                    }
                    /* @} */
                    continue;
                } else {
                    if (preline != null && preline.endsWith("=")) {
                        line = preline.substring(0, preline.length() - 1) + line;
                        preline = null;
                    }
                }
                // fix bug 91122 on 20121115 end

                int length = line.length();
                if (length == 0) {
                    // We shouldn't ever see an empty line
                    throw new IllegalArgumentException();
                }
                // A line starting with tab is a continuation
                if (line.charAt(0) == '\t') {
                    /* SPRD modify for bug 644208 {@ */
                    if (!notSaveFlag){
                        // Remember the line and length
                        lastValue = line.substring(1);
                        // Save the concatenation of old and new values
                        hash.put(lastField, hash.get(lastField) + lastValue);
                    }
                    /* @} */
                    continue;
                }
               /* SPRD modify for bug 644208 {@ */
               if (line.startsWith("ATTACH")) {
                    notSaveFlag = true;
                    Log.d(TAG, "[Begin]do not save to hash.  line str:" + line);
                    continue;
                } else if (notSaveFlag) {
                    notSaveFlag = false;
                    Log.d(TAG, "[Adjust]do not save to hash.  line str:" + line);
                }
                /* @} */
                // Find the field delimiter
                int pos = line.indexOf(':');
                // If not found, or at EOL, this is a bad ics
                if (pos < 0 || pos >= length) {
                    continue;
                }
                // Remember the field, value, and length
                lastField = line.substring(0, pos);
                lastValue = line.substring(pos + 1);

                /* SPRD for bug 481858 the description and reminders of the schedule are lost. @{ */
                if (line.equals("BEGIN:VALARM")) {
                    descriptionFlag = false;
                } else if (line.equals("END:VALARM")) {
                    descriptionFlag = true;
                }

                if (lastField.equals("BEGIN")) {
                    continue;
                } else if (lastField.equals("END")) {
                    // SPRD 565043
                    if (lastValue.equals("VEVENT") || lastValue.equals("VTODO")) {
                        hashList.add(hash);
                        hash = new HashMap<String, String>();
                    } else if (lastValue.equals("VCALENDAR")) {
                        break;
                    }
                } else if (lastField.startsWith("TRIGGER")) {
                    if (hash.containsKey("TRIGGER")) {
                        hash.put("TRIGGER", hash.get("TRIGGER") + ";" + lastValue);
                    } else {
                        hash.put("TRIGGER", lastValue);
                    }
                } else {
                    // Save it away and continue
                    if (hash.containsKey(lastField)) {
                        hash.put(lastField, hash.get(lastField) + ";" + lastValue);
                    } else {
                        hash.put(lastField, lastValue);
                    }
                    if (lastField.startsWith("SUMMARY")) {
                        isGBK = (lastField.indexOf("CHARSET=GBK") != -1);
                        isUTF = (lastField.indexOf("CHARSET=UTF-8") != -1);
                        hash.put("SUMMARY", lastValue);
                    //SPRD for bug 481858 the description and reminders of the schedule are lost.
                    } else if (lastField.startsWith("DESCRIPTION") && descriptionFlag) {
                        //SPRD for bug536574, some of the description are lost.
                        descriptionStr = true;
                        //hash.put("EVENTDESCRIPTION", lastValue);
                    } else if (lastField.startsWith("LOCATION")) {
                        hash.put("LOCATION", lastValue);
                    } else if (lastField.startsWith("ATTENDEEEMAIL")) {
                        // The organizer need requires a separate record
                        if (lastField.indexOf("ORGANIZER") != -1) {
                            hash.put("ORGANIZER", lastValue);
                        }
                        int index = lastField.lastIndexOf("=");
                        if (hash.containsKey("ATTENDEE")) {
                            hash.put("ATTENDEE", hash.get("ATTENDEE") + ";" + lastValue);
                            if (index > 0 && index < lastField.length() - 1) {
                                hash.put("ATTENDEESTATUE", hash.get("ATTENDEESTATUE") + ";"
                                        + lastField.substring(index + 1));
                            } else {
                                hash.put("ATTENDEESTATUE", " ;" + lastField.substring(index + 1));
                            }
                        } else {
                            hash.put("ATTENDEE", lastValue);
                            if (index > 0 && index < lastField.length() - 1) {
                                hash.put("ATTENDEESTATUE", lastField.substring(index + 1));
                            } else {
                                hash.put("ATTENDEESTATUE", "");
                            }
                        }
                    } else if (lastField.startsWith("DTSTART;TZID")) {
                        int index = lastField.indexOf("=");
                        hash.put("TIMEZONE", lastField.substring(index + 1));
                        hash.put("TIMESTART", lastValue);
                    } else if (lastField.startsWith("DTEND;TZID")) {
                        hash.put("TIMEEND", lastValue);
                    } else if (lastField.startsWith("RRULE")) {
                        hash.put("RRULE", lastValue);
                    } else if (lastField.startsWith("DTSTART")) {
                        hash.put("TIMEZONE", "UTC");
                        hash.put("TIMESTART", lastValue);
                    } else if (lastField.startsWith("DTEND")) {
                        hash.put("TIMEEND", lastValue);
                    /* SPRD 565043 @{ */
                    } else if (lastField.startsWith("DUE")){
                        hash.put("TIMEZONE", "UTC");
                        hash.put("TIMESTART", lastValue);
                        calendarDue = lastValue;
                    } else if(lastField.startsWith("COMPLETED")){
                        hash.put("TIMEEND", getTimeEnd(calendarDue, lastValue, "UTC"));
                    }
                    /* @} */
                }
            }
        }

        /**
         * The corresponding values through the label name
         *
         * @param i index
         * @param field tagert name
         * @return value
         */
        String get(int i, String field) {
            return hashList.get(i).get(field);
        }

        /**
         * get array size
         *
         * @return size
         */
        int size() {
            return hashList.size();
        }
        /* SPRD 565043 @{ */
        private String getTimeEnd(String calendardue, String timeend, String timezone){
            int hour = 0;
            String result = null;
            long calendarstart = transitionVCalendarTimeToMillis(
                calendardue, TimeZone.getTimeZone("GMT" + timezone));
            long calendarend = transitionVCalendarTimeToMillis(
                timeend, TimeZone.getTimeZone("GMT" + timezone));
            if(calendarstart >= calendarend){
                // done as Samsung,if end time is less than start time,just plus 1 hour
                hour = Integer.valueOf(calendardue.substring(9, 11)) + 1;
                result = calendardue.substring(0, 9) + hour + calendardue.substring(11, 15);
            }else {
                result = timeend;
            }
            Log.d(TAG, "getTimeEnd"+result);
            return result;
        }
        /* @} */
    }

    /**
     * Though the target parse vcs file
     *
     * @param bytes datas
     * @return data array
     * @throws IOException
     */
    private BlockHash parseIcsContent(byte[] bytes) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(fromUtf8(bytes)));
        String line = reader.readLine();
        if (line == null || !line.equals("BEGIN:VCALENDAR")) {
            throw new IllegalArgumentException();
        }
        return new BlockHash("VCALENDAR", reader);
    }

    /** Fix bug 610002 import ics file, date display error **/
    public static final String CURSOR_DATA = "_data";

    private String loadProfileType(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(uri,
                    new String[] { CURSOR_DATA }, null, null, null);
            if (cursor == null || cursor.getCount() != 1) {
                return null;
            }
            if (cursor.moveToFirst()) {
                String filePath = cursor.getString(cursor.getColumnIndexOrThrow(CURSOR_DATA));
                return filePath.substring(filePath.lastIndexOf("."), filePath.length());
            }
        } catch (Exception e) {
            Log.e(TAG, "loadProfileType", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ex) {
                }
            }
        }
        return null;
    }
    /** @} **/

    /**
     * load the vcs file and create VcalendarInfo object
     *
     * @param cr ContentResolver
     * @param uri file uri address
     * @return Event information
     */
    public ArrayList<VcalendarInfo> VcalendarImport(ContentResolver cr, Uri uri) {
        /** Fix bug 610002 import ics file, date display error {@ **/
        String pathStrType = null;
        /** @} **/
        BlockHash vcalendar;
        InputStream in = null;
        ArrayList<VcalendarInfo> calendarList = new ArrayList<VcalendarInfo>();
        String pathStr = uri.toString();
        try {
            /** Fix bug 610002 import ics file, date display error {@ **/
            if(mContext != null){
                pathStrType = loadProfileType(uri);
            }
            /** @} **/
            in = cr.openInputStream(uri);
            byte[] buffer = readFileSdcard(in);
            vcalendar = parseIcsContent(buffer);
            int size = vcalendar.size();

            /* SPRD: bug 460127 Could not import multiple agenda @{ */
            for (int idx = 0; idx < size; idx++) {
                VcalendarInfo mVCalendarsinfo = new VcalendarInfo();
                String eventid = vcalendar.get(idx, "VEVENTID");
                if (eventid == null || eventid.trim().equals("")) {
                    eventid = "-1";
                }
                mVCalendarsinfo.id = (int) Long.parseLong(eventid);
                /* TZID */
                String timezone = vcalendar.get(idx, "TIMEZONE");
                if (pathStr.endsWith(".ics")) {
                    timezone = "UTC";
                }
                mVCalendarsinfo.timezone = timezone;
                /* DTSTART */
                String starttime = vcalendar.get(idx, "TIMESTART");
                mVCalendarsinfo.starttime = transitionVCalendarTimeToMillis(
                        starttime, TimeZone.getTimeZone("GMT" + timezone));
                /* DTEND */
                String endtime = vcalendar.get(idx, "TIMEEND");
                if (!TextUtils.isEmpty(endtime)) {
                    mVCalendarsinfo.endtime = transitionVCalendarTimeToMillis(
                            endtime, TimeZone.getTimeZone("GMT" + timezone));
                }
                /* LOCATION */
                String location = vcalendar.get(idx, "LOCATION");
                if (!TextUtils.isEmpty(location)) {
                    if (location.endsWith("=")) {
                        location = location.substring(0, location.length() - 1);
                    }
                    /* SPRD: Modify for bug 756694, the event info appears garbled. @{ */
                    if (isGBK) {
                        mVCalendarsinfo.location = decodeQuotedPrintableForGBK(location);
                    } else if (isUTF){
                        mVCalendarsinfo.location = decodeQuotedPrintable(location);
                    } else {
                        mVCalendarsinfo.location = decodeQuotedPrintableNormal(location);
                    }
                    /* @} */
                }
                /* SUMMARY */
                String title = vcalendar.get(idx, "SUMMARY");
                if (!TextUtils.isEmpty(title)) {
                    if (title.endsWith("=")) {
                        title = title.substring(0, title.length() - 1);
                    }
                    /* SPRD: Modify for bug 756694, the event info appears garbled. @{ */
                    if (isGBK) {
                        mVCalendarsinfo.eventitle = decodeQuotedPrintableForGBK(title);
                    } else if (isUTF){
                        mVCalendarsinfo.eventitle = decodeQuotedPrintable(title);
                    } else {
                        mVCalendarsinfo.eventitle = decodeQuotedPrintableNormal(title);
                    }
                    /* @} */
                }
                /*  SPRD: Bug #481668 DESCRIPTION key and  REMINDERS were not parsed @{ */
                /* DESCRIPTION */
                String text = vcalendar.get(idx, "EVENTDESCRIPTION");
                if (!TextUtils.isEmpty(text)) {
                    if (text.endsWith("=")) {
                        text = text.substring(0, text.length() - 1);
                    }
                    /* SPRD: Modify for bug 756694, the event info appears garbled. @{ */
                    if (isGBK) {
                        mVCalendarsinfo.description = decodeQuotedPrintableForGBK(text);
                    } else if (isUTF){
                        mVCalendarsinfo.description = decodeQuotedPrintable(text);
                    } else {
                        mVCalendarsinfo.description = decodeQuotedPrintableNormal(text);
                    }
                    /* @} */
                }

                /* REMINDER */
                String isHasAlarm = vcalendar.get(idx, "HAS_ALARM");
                if (!TextUtils.isEmpty(isHasAlarm)) {
                    if (isHasAlarm.endsWith("=")) {
                        isHasAlarm = isHasAlarm.substring(0, isHasAlarm.length() - 1);
                    }
                    if (Integer.valueOf(isHasAlarm) != 0) {
                        mVCalendarsinfo.hasAlarm = true;
                    } else {
                        mVCalendarsinfo.hasAlarm = false;
                    }
                }
                String remindersAlarm = vcalendar.get(idx, "REMINDERS");
                if (!TextUtils.isEmpty(remindersAlarm)) {
                    if (remindersAlarm.endsWith("=")) {
                        remindersAlarm = remindersAlarm.substring(0, remindersAlarm.length() - 1);
                    }
                    /* SPRD: Modify for bug 756694, the event info appears garbled. @{ */
                    if (isGBK) {
                        mVCalendarsinfo.AlarmMinute = decodeQuotedPrintableForGBK(remindersAlarm);
                    } else if (isUTF){
                        mVCalendarsinfo.AlarmMinute = decodeQuotedPrintable(remindersAlarm);
                    } else {
                        mVCalendarsinfo.AlarmMinute = decodeQuotedPrintableNormal(remindersAlarm);
                    }
                    /* @} */
                }
                /* @} */

                /* ATTENDEEEMAIL */
                String attendeeEmail = vcalendar.get(idx, "ATTENDEE");
                if (!TextUtils.isEmpty(attendeeEmail)) {
                    /* SPRD: Modify for bug 756694, the event info appears garbled. @{ */
                    if (isGBK) {
                        mVCalendarsinfo.attendeeEmail = decodeQuotedPrintableForGBK(attendeeEmail);
                    } else if (isUTF){
                        mVCalendarsinfo.attendeeEmail = decodeQuotedPrintable(attendeeEmail);
                    } else {
                        mVCalendarsinfo.attendeeEmail = decodeQuotedPrintableNormal(attendeeEmail);
                    }
                    /* @} */
                    /* ATTENDEESTATUS */
                    String attendeeStatus = vcalendar.get(idx, "ATTENDEESTATUE");
                    mVCalendarsinfo.attendeeStatus = attendeeStatus;
                }

                /* DURATION */
                String duration = vcalendar.get(idx, "DURATION");
                if (!TextUtils.isEmpty(duration)) {
                    if (duration.endsWith("=")) {
                        duration = duration.substring(0, duration.length() - 1);
                    }
                    /* SPRD: Modify for bug 756694, the event info appears garbled. @{ */
                    if (isGBK) {
                        mVCalendarsinfo.duration = decodeQuotedPrintableForGBK(duration);
                    } else if (isUTF){
                        mVCalendarsinfo.duration = decodeQuotedPrintable(duration);
                    } else {
                        mVCalendarsinfo.duration = decodeQuotedPrintableNormal(duration);
                    }
                    /* @} */
                }
                /* RRULE */
                String rrules = vcalendar.get(idx, "RRULE");
                // String BYDAY = rrules;
                mVCalendarsinfo.rRule = rrules;
                /* X-ALLDAY */
                /** Fix bug 610002 import ics file, date display error {@ **/
                if(pathStrType == null){
                    pathStrType = pathStr;
                }
                /* SPRD for bug 732548 import allday events different with old events @{ */
                if (pathStrType.endsWith(".ics")) {
                    if (starttime.length() == 8 && endtime.length() == 8) {
                        mVCalendarsinfo.allDay = true;
                    } else {
                        mVCalendarsinfo.allDay = false;
                    }
                } else {
                    String allDay = vcalendar.get(idx, "X-ALLDAY");
                    if ("1".equals(allDay)) {
                        mVCalendarsinfo.allDay = true;
                    } else {
                        mVCalendarsinfo.allDay = false;
                    }
                }
                /* @} */
                /** @} **/

                /* SPRD for bug 481858 the description and reminders of the schedule are lost. @{ */
                /* ACCESSLEVEL */
                String accessLevel = vcalendar.get(idx, "ACCESSLEVEL");
                if (!TextUtils.isEmpty(accessLevel)) {
                    mVCalendarsinfo.accessLevel = Integer.valueOf(accessLevel);
                } else {
                    String accessLevelSingle = vcalendar.get(idx, "CLASS");
                    if (!TextUtils.isEmpty(accessLevelSingle)) {
                        if ("PRIVATE".equals(accessLevelSingle)) {
                            mVCalendarsinfo.accessLevel = 2;
                        } else if ("PUBLIC".equals(accessLevelSingle)) {
                            mVCalendarsinfo.accessLevel = 3;
                        } else {
                            mVCalendarsinfo.accessLevel = 0;
                        }
                    }
                }
                /* @} */

                /* AVAILABLITY */
                /* SPRD for bug 481858 the description and reminders of the schedule are lost. @{ */
                String availablity = vcalendar.get(idx, "AVAILABILITY");
                if (!TextUtils.isEmpty(availablity)) {
                    mVCalendarsinfo.availablity = Integer.valueOf(availablity);
                } else {
                    availablity = vcalendar.get(idx, "AVAILABLITY");
                    if (!TextUtils.isEmpty(availablity)) {
                        mVCalendarsinfo.availablity = Integer.valueOf(availablity);
                    }
                }

                /* ORGANIZER */
                String organizer = vcalendar.get(idx, "ORGANIZER");
                if (!TextUtils.isEmpty(organizer)) {
                    mVCalendarsinfo.organizer = organizer;
                }

                /* ALARM */
                String alarm = vcalendar.get(idx, "TRIGGER");
                if (!TextUtils.isEmpty(alarm)) {
                    mVCalendarsinfo.hasAlarm = true;
                    StringBuilder sb = new StringBuilder();
                    String[] alarmstr = alarm.trim().split(";");
                    for (String i : alarmstr) {
                        String str = i.substring(3, (i.length() - 1));
                        sb.append(str).append(";");
                    }
                    mVCalendarsinfo.AlarmMinute = sb.toString();
                    String hasAttendee = vcalendar.get(idx, "HASATTENDEE");
                    if (hasAttendee != null) {
                        if (hasAttendee.equals("1")) {
                            mVCalendarsinfo.hasAttendee = true;
                        } else {
                            mVCalendarsinfo.hasAttendee = false;
                        }
                    }

                /* SPRD for bug 481858 the description and reminders of the schedule are lost. @{ */
                } else {
                    /* HAS_ALARM */
                    String hasAlarm = vcalendar.get(idx, "HAS_ALARM");
                    if (!TextUtils.isEmpty(hasAlarm)) {
                        mVCalendarsinfo.hasAlarm = true;
                    }
                    /* HAS_ATTENDEE */
                    String hasAttendee = vcalendar.get(idx, "HAS_ATTENDEE");
                    if (hasAttendee != null) {
                        if (hasAttendee.equals("1")) {
                            mVCalendarsinfo.hasAttendee = true;
                        } else {
                            mVCalendarsinfo.hasAttendee = false;
                        }
                    }
                    /* REMINDERS */
                    String reminders = vcalendar.get(idx, "REMINDERS");
                    if (!TextUtils.isEmpty(reminders)) {
                        mVCalendarsinfo.AlarmMinute = reminders;
                    }
                }
                /* @} */
                calendarList.add(mVCalendarsinfo);
            }/* @} */
            return calendarList;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
/* @} */
