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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.websphere.pmi.stat.WSRangeStatistic;
import com.ibm.websphere.pmi.stat.WSSessionManagementStats;
import com.ibm.websphere.pmi.stat.WSStats;

import net.wait4it.nagios.wasagent.core.Result;
import net.wait4it.nagios.wasagent.core.Status;
import net.wait4it.nagios.wasagent.core.WASClientProxy;

/**
 * Gets the current HTTP sessions count
 * for a web application.
 * 
 * @author Yann Lambret
 *
 */
public class ApplicationTest extends TestUtils implements Test {

    /**
     * WebSphere applications stats.
     * 
     * @param proxy   an applicative proxy for the target WAS instance
     * @param params  a pipe separated list of web application names, or
     *                a wildcard character (*) for all web applications
     * @return result collected data and test status
     */
    public Result run(WASClientProxy proxy, String params) {
        Result result = new Result();
        List<String> output = new ArrayList<String>();
        List<String> message = new ArrayList<String>();
        int code = 0;

        // Test thresholds
        long warning, critical;
        String thresholds = "";
        Map<String,String> apps = new HashMap<String,String>();

        // Test code for a specific application
        int testCode = 0;

        // Message prefix
        String prefix = "HTTP session count: ";

        // PMI stats
        WSStats stats;
        WSRangeStatistic lc;

        // Performance data
        long liveCount;

        // Parses HTTP query params
        for (String s : Arrays.asList(params.split("\\|"))) {
            apps.put(s.split(",", 2)[0], s.split(",", 2)[1]);
        }

        try {
            stats = proxy.getStats(WSSessionManagementStats.NAME);
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus(Status.UNKNOWN);
            result.setMessage(e.toString());
            return result;
        }

        WSStats[] stats1 = stats.getSubStats();
        for (WSStats stat1 : stats1) {

            // No statistics for WAS internal components
            if (stat1.getName().matches("ibmasyncrsp#ibmasyncrsp.war")) {
                continue;
            }

            if (apps.containsKey("*") || apps.containsKey(stat1.getName())) {
                lc = (WSRangeStatistic)stat1.getStatistic(WSSessionManagementStats.LiveCount);
                try {
                    liveCount = lc.getCurrent();
                } catch (NullPointerException e) {
                    throw new RuntimeException("invalid 'Servlet Session Manager' PMI settings.");
                }

                // Test output (Nagios performance data)
                output.add("app-" + stat1.getName() + "=" + liveCount);

                // Test return code
                thresholds = apps.get("*") != null ? apps.get("*") : apps.get(stat1.getName());
                warning = Long.parseLong(thresholds.split(",")[0]);
                critical = Long.parseLong(thresholds.split(",")[1]);
                testCode = checkResult(liveCount, critical, warning);               

                if (testCode == Status.WARNING.getCode() || testCode == Status.CRITICAL.getCode()) {
                    message.add("'" + stat1.getName() + "' (" + liveCount + ")");
                    code = (testCode > code) ? testCode : code;
                }
            }
        }

        for (Status status : Status.values()) {
            if (code == status.getCode()) {
                result.setStatus(status);
                break;
            }
        }

        result.setOutput(formatOut(output));
        result.setMessage(formatMsg(prefix, message));

        return result;
    }

}
