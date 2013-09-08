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

import com.ibm.websphere.pmi.stat.WSBoundedRangeStatistic;
import com.ibm.websphere.pmi.stat.WSCountStatistic;
import com.ibm.websphere.pmi.stat.WSJVMStats;
import com.ibm.websphere.pmi.stat.WSStats;

import net.wait4it.nagios.wasagent.core.Result;
import net.wait4it.nagios.wasagent.core.Status;
import net.wait4it.nagios.wasagent.core.WASClientProxy;

/**
 * Gets statistics for the the target
 * WAS instance JVM.
 * 
 * The following metrics are available:
 * 
 *   - The JVM current heap size (MB)
 *   - The JVM maximum heap size (MB)
 *   - The current amount of memory used by the JVM (MB)
 *   - The amount of CPU resources used by the JVM (%)
 * 
 * @author Yann Lambret
 *
 */
public class JVMTest extends TestUtils implements Test {

    /**
     * WebSphere JVM stats.
     * 
     * @param proxy   an applicative proxy for the target WAS instance
     * @param params  params warning and critical thresholds
     * @return result collected data and test status
     */
    public Result run(WASClientProxy proxy, String params) {
        Result result = new Result();
        int code = 0;

        // Test thresholds
        long warning, critical;

        // PMI stats
        WSStats stats;
        WSBoundedRangeStatistic hs;
        WSCountStatistic um;
        WSCountStatistic cu;

        // Performance data
        long maxMemory, heapSize, heapUsed, cpu;

        // Parses HTTP query params
        String[] paramsArray = params.split(",");
        warning = Long.parseLong(paramsArray[1]);
        critical = Long.parseLong(paramsArray[2]);

        try {
            stats = proxy.getStats(WSJVMStats.NAME);
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus(Status.UNKNOWN);
            result.setMessage(e.toString());
            return result;
        }

        hs = (WSBoundedRangeStatistic)stats.getStatistic(WSJVMStats.HeapSize);
        um = (WSCountStatistic)stats.getStatistic(WSJVMStats.UsedMemory);
        cu = (WSCountStatistic)stats.getStatistic(WSJVMStats.cpuUsage);

        try {
            // Memory values are expressed as Megabytes
            maxMemory = hs.getUpperBound() / 1024L;
            heapSize = hs.getCurrent() / 1024L;
            heapUsed = um.getCount() / 1024L;
            cpu = cu.getCount();
        } catch (NullPointerException e) {
            throw new RuntimeException("invalid 'JVM Runtime' PMI settings.");
        }

        // Test output (Nagios performance data)
        StringBuilder out = new StringBuilder();
        out.append("jvm-heapSize=" + heapSize + "MB;;;0;" + maxMemory + " ");
        out.append("jvm-heapUsed=" + heapUsed + "MB;;;0;" + maxMemory + " ");
        out.append("jvm-cpu=" + cpu + "%;;;0;100");
        result.setOutput(out.toString());

        // Test return code
        code = checkResult(heapUsed, maxMemory, critical, warning);

        if (code == Status.WARNING.getCode() || code == Status.CRITICAL.getCode()) {
            result.setMessage("memory used (" + heapUsed + "/" + maxMemory + ")");
        }

        for (Status status : Status.values()) {
            if (code == status.getCode()) {
                result.setStatus(status);
                break;
            }
        }

        return result;
    }

}