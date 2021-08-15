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
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class Waiter {

    private static final long SLEEP_PRECISION = TimeUnit.MICROSECONDS.toNanos(200); // arbitrary value, TODO: derive this from actual machine the code is running on
    private static final long SPIN_YIELD_PRECISION = TimeUnit.MICROSECONDS.toNanos(100); // arbitrary value, TODO: derive this from actual machine the code is running on
    
     /*
     * Spin-yield loop based alternative to Thread.sleep Based on the code of
     * Andy Malakov
     * http://andy-malakov.blogspot.fr/2010/06/alternative-to-threadsleep.html
     * and the gist: https://gist.github.com/ChristianSchwarz/2dbe3e2a15572b3927735569d2a35704
     */
    public static void sleepNanos(long nanoSecondDuration) throws InterruptedException {
        final long end = System.nanoTime() + nanoSecondDuration;
        long timeLeft = nanoSecondDuration;
        do {
            if (timeLeft > SLEEP_PRECISION) {
                LockSupport.parkNanos( 1);
            } else {
                if (timeLeft > SPIN_YIELD_PRECISION) {
                    Thread.yield();
                }
            }
            timeLeft = end - System.nanoTime();

            if (Thread.interrupted())
                throw new InterruptedException();
        } while (timeLeft > 0);
    }

    /**
     * Wait (sleep) for a given duration.
     * @param duration
     */
    public static void waitADuration(Duration duration) {
        // TODO: take into account double speed => duration / 2
        // waitADuration a while (=duration)
        long seconds = duration.getSeconds();
        int nanoseconds = duration.getNano();
        long milliseconds = Math.round(nanoseconds / 1000000.0); // convert nanoseconds to milliseconds
        milliseconds = milliseconds + seconds * 1000; // and add seconds converted to milliseconds to them
        Waiter.waitMilliseconds( milliseconds);
    }
    
    public static void waitMilliseconds( long milliseconds) {
         try {
            Thread.sleep( milliseconds);
        } catch (InterruptedException ex) {
            Logger.getLogger(Waiter.class.getName()).log(Level.SEVERE, "Interrupted exception in wait(duration)", ex);
        }
       
    }
    
    public static boolean hasTimePassed( LocalDateTime previousTimestamp, Duration aDuration) {
        if (LocalDateTime.now().compareTo(previousTimestamp.plus(aDuration)) > 0 ) {
            return true;
        } else {
            return false;
        }
    }
}
