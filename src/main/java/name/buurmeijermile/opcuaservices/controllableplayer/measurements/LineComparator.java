/*
 * The MIT License
 *
 * Copyright 2018 Milé Buurmeijer <mbuurmei at netscape.net>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class LineComparator implements Comparator<String> {
    
    private final DateTimeFormatter TIMESTAMP_FORMATTER=  DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");

    @Override
    public int compare(String line1, String line2) {
        // the timestamp is in the 3rd column of an input line
        String[] line1items = line1.split(";");
        String[] line2items = line2.split(";");
        // check if there are 4 fields on both inputs
        if ( line1items.length != 4 || line2items.length != 4) {
            return -1;
        } else {
            try {
                LocalDateTime line1Timestamp;
                LocalDateTime line2Timestamp;
                line1Timestamp = LocalDateTime.parse( line1items[2], TIMESTAMP_FORMATTER);
                line2Timestamp = LocalDateTime.parse( line2items[2], TIMESTAMP_FORMATTER);
                return line1Timestamp.compareTo( line2Timestamp);
            } catch ( DateTimeParseException dtpe) {
                Logger.getLogger( LineComparator.class.getName()).log(Level.SEVERE, "Error comparing line1=" + line1 + " with line2=" +  line2);
                return -1;
            }
        }
    }
    
}
