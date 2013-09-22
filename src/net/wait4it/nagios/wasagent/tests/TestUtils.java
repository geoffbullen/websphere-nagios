/**
 * This file is part of Wasagent.
 *
 * Wasagent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wasagent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Wasagent. If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package net.wait4it.nagios.wasagent.tests;

import java.util.Collections;
import java.util.List;

/**
 * Common test methods.
 * 
 * @author Yann Lambret
 *
 */
public abstract class TestUtils {

    /**
     * Compares a ratio to the submitted thresholds.
     * 
     * @param n1       the test output value
     * @param n2       the reference value
     * @param critical the critical threshold
     * @param warning  the warning threshold
     * @return         a Nagios exit code (0, 1 or 2)
     */
    public static int checkResult(long n1, long n2, long critical, long warning) {
        int code = 0;
        if (isCritical(n1, n2, critical)) {
            code = 2;
        } else if (isWarning(n1, n2, warning)) {
            code = 1;
        }
        return code;
    }

    /**
     * Compares a value to the submitted thresholds.
     * 
     * @param n        the test output value
     * @param critical the critical threshold
     * @param warning  the warning threshold
     * @return         a Nagios exit code (0, 1 or 2)
     */
    public static int checkResult(long n, long critical, long warning) {
        int code = 0;
        if (isCritical(n, critical)) {
            code = 2;
        } else if (isWarning(n, warning)) {
            code = 1;
        }
        return code;
    }

    /**
     * This method is used when a test produces a data set.
     * 
     * @param  list a list of strings produced by the test
     * @return      a nicely formatted string
     */
    public String formatOut(List<String> list) {
        String s = "";
        if (! list.isEmpty()) {
            Collections.sort(list);
            s = list.remove(0);
            while(! list.isEmpty()) {
                s += " " + list.remove(0);
            }
        }
        return s;
    }

    /**
     * This method is used when a test produces several alert messages.
     * 
     * @param  prefix a string which allows to identify the test
     *                which produced the message
     * @param  list   a list of strings produced by the test
     * @return        a nicely formatted string
     */
    public String formatMsg(String prefix, List<String> list) {
        String s = "";
        if (! list.isEmpty()) {
            Collections.sort(list);
            s = prefix + list.remove(0);
            while(! list.isEmpty()) {
                s += ", " + list.remove(0);
            }
        }
        return s;
    }

    /**
     * Compares a ratio to a critical threshold.
     * 
     * @param n1       the test output value
     * @param n2       the reference value
     * @param critical the critical threshold
     * @return         true if the test should raise a critical alert
     */
    private static boolean isCritical(long n1, long n2, long critical) {
        long ratio = ratio(n1, n2);
        return ratio >= critical;
    }

    /**
     * Compares a value to a critical threshold.
     * 
     * @param n        the test output value
     * @param critical the critical threshold
     * @return         true if the test should raise a critical alert
     */
    private static boolean isCritical(long n, long critical) {
        return n >= critical;
    }

    /**
     * Compares a ratio to a warning threshold.
     * 
     * @param n1      the test output value
     * @param n2      the reference value
     * @param warning the warning threshold
     * @return        true if the test should raise a warning alert
     */
    private static boolean isWarning(long n1, long n2, long warning) {
        long ratio = ratio(n1, n2);
        return ratio >= warning;
    }

    /**
     * Compares a value to a warning threshold.
     * 
     * @param n       the test output value
     * @param warning the warning threshold
     * @return        true if the test should raise a warning alert
     */
    private static boolean isWarning(long n, long warning) {
        return n >= warning;
    }

    /**
     * Calculates a ratio expressed as a percentage.
     * 
     * @param n1 the test output value
     * @param n2 the reference value
     * @return   the calculated ratio
     */
    private static long ratio(long n1, long n2) {
        return Math.round((double) n1 / (double) n2 * 100);
    }

}
