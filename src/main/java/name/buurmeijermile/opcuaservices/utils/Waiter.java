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
package name.buurmeijermile.opcuaservices.utils;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataStreamController;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class Waiter {

    /**
     * Wait (sleep) for a given duration.
     * @param duration
     */
    public static void wait(Duration duration) {
        // TODO: take into account double speed => duration / 2
        try {
            // wait a while (=duration)
            long seconds = duration.getSeconds();
            int nanoseconds = duration.getNano();
            long milliseconds = Math.round(nanoseconds / 1000000.0); // convert nanoseconds to milliseconds
            milliseconds = milliseconds + seconds * 1000; // and add seconds converted to milliseconds to them
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
            Logger.getLogger(DataStreamController.class.getName()).log(Level.SEVERE, "Interrupted exception in wait(duration)", ex);
        }
    }
    
}
