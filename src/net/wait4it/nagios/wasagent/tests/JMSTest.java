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
import java.util.Set;

import javax.management.ObjectName;

import com.ibm.websphere.pmi.stat.WSBoundedRangeStatistic;
import com.ibm.websphere.pmi.stat.WSJCAConnectionPoolStats;
import com.ibm.websphere.pmi.stat.WSRangeStatistic;
import com.ibm.websphere.pmi.stat.WSStats;

import net.wait4it.nagios.wasagent.core.Result;
import net.wait4it.nagios.wasagent.core.Status;
import net.wait4it.nagios.wasagent.core.WASClientProxy;

/**
 * Gets statistics for JMS connection factories.
 * 
 * The following metrics are available:
 * 
 *   - The JMS connection factory current pool size
 *   - The JMS connection factory maximum pool size
 *   - The active connection count
 *   - The number of threads waiting for an available
 *     connection
 * 
 * @author Yann Lambret
 *
 */
public class JMSTest extends TestUtils implements Test {

    /**
     * WebSphere JMS connection factories stats.
     * 
     * @param proxy   an applicative proxy for the target WAS instance
     * @param params  a pipe separated list of factory names, or
     *                a wildcard character (*) for all factories
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
        Map<String,String> factories = new HashMap<String,String>();

        // Test code for a specific factory
        int testCode = 0;

        // JMS 1.0 listeners status
        int stopped = 0;

        // Message prefix
        String prefix = "factory active count: ";

        // PMI stats
        WSStats stats;
        WSBoundedRangeStatistic ps;
        WSBoundedRangeStatistic fps;
        WSRangeStatistic wtc;

        // Performance data
        long currentPoolSize, maxPoolSize, freePoolSize, waitingThreadCount, activeThreadCount;

        // Parses HTTP query params
        for (String s : Arrays.asList(params.split("\\|"))) {
            factories.put(s.split(",", 2)[0], s.split(",", 2)[1]);
        }

        try {
            stats = proxy.getStats(WSJCAConnectionPoolStats.NAME);

            // JMS 1.0 listeners status
            Set<ObjectName> listeners = proxy.getMBeans("WebSphere:*,type=ListenerPort");
            for (ObjectName listener : listeners) {             
                if (! (Boolean)proxy.getAttribute(listener, "started")) {
                    stopped += 1;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus(Status.UNKNOWN);
            result.setMessage(e.toString());
            return result;
        }

        WSStats[] stats1 = stats.getSubStats(); // JMS provider level
        for (WSStats stat1 : stats1) {

            if (stat1.getName().equals("SIB JMS Resource Adapter") || stat1.getName().equals("WebSphere MQ JMS Provider")) {
                WSStats[] stats2 = stat1.getSubStats(); // JCA factory level                    
                for (WSStats stat2 : stats2) {

                    if (factories.containsKey("*") || factories.containsKey(stat2.getName())) {
                        ps = (WSBoundedRangeStatistic)stat2.getStatistic(WSJCAConnectionPoolStats.PoolSize);
                        fps = (WSBoundedRangeStatistic)stat2.getStatistic(WSJCAConnectionPoolStats.FreePoolSize);
                        wtc = (WSRangeStatistic)stat2.getStatistic(WSJCAConnectionPoolStats.WaitingThreadCount);
                        try {
                            currentPoolSize = ps.getCurrent();
                            maxPoolSize = ps.getUpperBound();
                            freePoolSize = fps.getCurrent();
                            waitingThreadCount = wtc.getCurrent();
                            activeThreadCount = currentPoolSize - freePoolSize;
                        } catch (NullPointerException e) {
                            throw new RuntimeException("invalid 'JCA Connection Pools' PMI settings.");
                        }

                        // Test output (Nagios performance data)
                        StringBuilder out = new StringBuilder();
                        out.append("jms-" + stat2.getName() + "-size=" + currentPoolSize + ";;;0;" + maxPoolSize + " ");
                        out.append("jms-" + stat2.getName() + "-activeThreadCount=" + activeThreadCount + ";;;0;" + maxPoolSize + " ");
                        out.append("jms-" + stat2.getName() + "-waitingThreadCount=" + waitingThreadCount);
                        output.add(out.toString());

                        // Test return code
                        thresholds = factories.get("*") != null ? factories.get("*") : factories.get(stat2.getName());
                        warning = Long.parseLong(thresholds.split(",")[0]);
                        critical = Long.parseLong(thresholds.split(",")[1]);
                        testCode = checkResult(activeThreadCount, maxPoolSize, critical, warning);

                        if (testCode == Status.WARNING.getCode() || testCode == Status.CRITICAL.getCode()) {
                            message.add("'" + stat2.getName() + "' (" + activeThreadCount + "/" + maxPoolSize + ")");
                            code = (testCode > code) ? testCode : code;
                        }
                    }
                }
            }
        }

        if (code == Status.OK.getCode() && stopped > 0) {
            code = Status.WARNING.getCode();
        }

        // Hack for the message string
        if (stopped > 0 && message.size() > 0) {
            prefix = "stopped listeners: " + stopped + " - " + prefix;
        } else if (stopped > 0) {
            prefix = "stopped listeners: ";
            message.add(String.valueOf(stopped));
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
