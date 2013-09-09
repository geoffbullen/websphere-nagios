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

import com.ibm.websphere.pmi.stat.WSBoundedRangeStatistic;
import com.ibm.websphere.pmi.stat.WSRangeStatistic;
import com.ibm.websphere.pmi.stat.WSStats;
import com.ibm.websphere.pmi.stat.WSThreadPoolStats;

import net.wait4it.nagios.wasagent.core.Result;
import net.wait4it.nagios.wasagent.core.Status;
import net.wait4it.nagios.wasagent.core.WASClientProxy;

/**
 * Gets statistics for WebSphere thread pools.
 * 
 * The following metrics are available:
 * 
 *   - The thread pool current size
 *   - The thread pool maximum size
 *   - The active thread count
 *   - The hung thread count (WAS 7.0 & 8.x)
 * 
 * @author Yann Lambret
 *      
 */
public class ThreadPoolTest extends TestUtils implements Test {

    /**
     * WebSphere thread pools stats.
     * 
     * @param proxy   an applicative proxy for the target WAS instance
     * @param params  a pipe separated list of thread pool names, or
     *                a wildcard character (*) for all thread pools
     * @return result collected data and test status
     */
    public Result run(WASClientProxy proxy, String params) {
        Result result = new Result();
        List<String> output = new ArrayList<String>();
        List<String> active = new ArrayList<String>();
        List<String> hung = new ArrayList<String>();
        int code = 0;

        // Test thresholds
        long warning, critical;
        String thresholds = "";
        Map<String,String> pools = new HashMap<String,String>();

        // Test code for a specific thread pool
        int testCode = 0;

        // WAS version
        String version = "";

        // PMI stats
        WSStats stats;
        WSBoundedRangeStatistic ps;
        WSBoundedRangeStatistic ac;
        WSRangeStatistic chtc;

        // Performance data
        long currentPoolSize, maxPoolSize, activeCount, hungCount;

        // Parses HTTP query params
        for (String s : Arrays.asList(params.split("\\|"))) {
            pools.put(s.split(",", 2)[0], s.split(",", 2)[1]);
        }

        try {
            stats = proxy.getStats(WSThreadPoolStats.NAME);
            version = proxy.getServerVersion();
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus(Status.UNKNOWN);
            result.setMessage(e.toString());
            return result;
        }

        WSStats[] stats1 = stats.getSubStats();
        for (WSStats stat1 : stats1) {
            if (pools.containsKey("*") || pools.containsKey(stat1.getName())) {
                ps = (WSBoundedRangeStatistic)stat1.getStatistic(WSThreadPoolStats.PoolSize);
                ac = (WSBoundedRangeStatistic)stat1.getStatistic(WSThreadPoolStats.ActiveCount);
                try {
                    currentPoolSize = ps.getCurrent();
                    maxPoolSize = ps.getUpperBound();
                    activeCount = ac.getCurrent();
                } catch (NullPointerException e) {
                    throw new RuntimeException("invalid 'Thread Pools' PMI settings.");
                }

                // Test output (Nagios performance data)
                StringBuilder out = new StringBuilder();
                out.append("pool-" + stat1.getName() + "-size=" + currentPoolSize + ";;;0;" + maxPoolSize + " ");
                out.append("pool-" + stat1.getName() + "-activeCount=" + activeCount + ";;;0;" + maxPoolSize);

                // Test return code
                thresholds = pools.get("*") != null ? pools.get("*") : pools.get(stat1.getName());
                warning = Long.parseLong(thresholds.split(",")[0]);
                critical = Long.parseLong(thresholds.split(",")[1]);
                testCode = checkResult(activeCount, maxPoolSize, critical, warning);

                if (testCode == Status.WARNING.getCode() || testCode == Status.CRITICAL.getCode()) {
                    active.add("'" + stat1.getName() + "' (" + activeCount + "/" + maxPoolSize + ")");
                    code = (testCode > code) ? testCode : code;
                }

                // Hung thread detection, only for WAS 7.0 & 8.x
                if (version.matches("^[78]\\..*")) {
                    chtc = (WSRangeStatistic)stat1.getStatistic(WSThreadPoolStats.ConcurrentHungThreadCount);
                    try {
                        hungCount = chtc.getCurrent();
                        out.append(" pool-" + stat1.getName() + "-hungCount=" + hungCount + ";;;0;" + maxPoolSize);
                        testCode = checkResult(hungCount, maxPoolSize, 20L, 10L);

                        if (testCode == Status.WARNING.getCode() || testCode == Status.CRITICAL.getCode()) {
                            hung.add("'" + stat1.getName() + "' (" + hungCount + "/" + maxPoolSize + ")");
                            code = (testCode > code) ? testCode : code;
                        }
                    } catch (NullPointerException ignored) {
                        /*
                         * PMI settings may be wrong, or this metric is not available due to a specific 
                         * configuration ('com.ibm.websphere.threadmonitor.interval' = 0 for instance)
                         * Anyway we don't want to pollute the regular test output.
                         * 
                         */
                    }                           
                }
                output.add(out.toString());
            }
        }

        String msg1 = formatMsg("thread pool active count: ", active);
        String msg2 = formatMsg("thread pool hung count: ", hung);

        for (Status status : Status.values()) {
            if (code == status.getCode()) {
                result.setStatus(status);
                break;
            }
        }

        result.setOutput(formatOut(output));
        result.setMessage(msg1 + (! msg1.equals("") && ! msg2.equals("") ? " - " + msg2 : msg2));

        return result;
    }

}
