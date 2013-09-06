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

import java.text.DecimalFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.websphere.pmi.stat.WSStats;
import com.ibm.websphere.pmi.stat.WSTimeStatistic;
import com.ibm.websphere.pmi.stat.WSWebAppStats;

import net.wait4it.nagios.wasagent.core.Result;
import net.wait4it.nagios.wasagent.core.Status;
import net.wait4it.nagios.wasagent.core.WASClientProxy;

/**
 * Gets servlet service method execution time (ms).
 * 
 * The metric name is of the form:
 * 
 *   app_name#web_module_name.servlet_name
 * 
 * @author Yann Lambret
 *
 */
public class ServletTest extends TestUtils implements Test {

    // Servlet response time format
    private static final DecimalFormat DF = new DecimalFormat("0.00");

    /**
     * WebSphere servlets stats.
     * 
     * @param proxy   an applicative proxy for the target WAS instance
     * @param params  a pipe separated list of servlet names, or
     *                a wildcard character (*) for all servlets
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
        Map<String,String> servlets = new HashMap<String,String>();

        // Test code for a specific servlet
        int testCode = 0;

        // Message prefix
        String prefix = "servlet response time: ";

        // PMI stats
        WSStats stats;

        // Performance data
        double serviceTime;

        // Parses HTTP query params
        for (String s : Arrays.asList(params.split("\\|"))) {
            servlets.put(s.split(",", 2)[0], s.split(",", 2)[1]);
        }

        try {
            stats = proxy.getStats(WSWebAppStats.NAME);
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus(Status.UNKNOWN);
            result.setMessage(e.toString());
            return result;
        }

        if (stats != null) {           
            WSStats[] stats1 = stats.getSubStats(); // WEB module level
            for (WSStats stat1 : stats1) {
                WSStats[] stats2 = stat1.getSubStats(); // Servlets module level
                for (WSStats stat2 : stats2) {
                    WSStats[] stats3 = stat2.getSubStats(); // Servlet level 
                    for (WSStats stat3 : stats3) {

                        // No statistics for WAS internal components
                        if (stat3.getName().matches("rspservlet")) {
                            continue;
                        }

                        if (servlets.containsKey("*") || servlets.containsKey(stat3.getName())) {
                            try {
                                serviceTime = ((WSTimeStatistic)stat3.getStatistic(WSWebAppStats.ServletStats.ServiceTime)).getMean();
                            } catch (NullPointerException e) {
                                throw new RuntimeException("invalid 'Web Applications' PMI settings.");
                            }

                            // Test output (Nagios performance data)
                            output.add("servlet-" + stat3.getName() + "-serviceTime=" + DF.format(serviceTime));

                            // Test return code
                            thresholds = servlets.get("*") != null ? servlets.get("*") : servlets.get(stat3.getName());
                            warning = Long.parseLong(thresholds.split(",")[0]);
                            critical = Long.parseLong(thresholds.split(",")[1]);
                            testCode = checkResult(Math.round(serviceTime), critical, warning);

                            if (testCode == Status.WARNING.getCode() || testCode == Status.CRITICAL.getCode()) {
                                message.add("'" + stat3.getName() + "' (" + DF.format(serviceTime) + ")");
                                code = (testCode > code) ? testCode : code;
                            }
                        }
                    }
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