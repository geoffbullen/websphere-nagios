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

import com.ibm.websphere.pmi.stat.WSCountStatistic;
import com.ibm.websphere.pmi.stat.WSJTAStats;
import com.ibm.websphere.pmi.stat.WSStats;

import net.wait4it.nagios.wasagent.core.Result;
import net.wait4it.nagios.wasagent.core.Status;
import net.wait4it.nagios.wasagent.core.WASClientProxy;

/**
 * Gets the current transaction active count.
 * 
 * @author Yann Lambret
 *
 */
public class JTATest extends TestUtils implements Test {

    /**
     * WebSphere JTA stats.
     * 
     * @param  proxy  an applicative proxy for the target WAS instance
     * @param  params warning and critical thresholds
     * @return result collected data and test status
     */
    public Result run(WASClientProxy proxy, String params) {
        Result result = new Result();
        int code = 0;

        // Test thresholds
        long warning, critical;

        // PMI stats
        WSStats stats;

        // Performance data
        long activeCount;

        // Parses HTTP query params
        String[] thresholds = params.split(",");
        warning = Long.parseLong(thresholds[1]);
        critical = Long.parseLong(thresholds[2]);

        try {
            stats = proxy.getStats(WSJTAStats.NAME);
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus(Status.UNKNOWN);
            result.setMessage(e.toString());
            return result;
        }

        try {
            activeCount = ((WSCountStatistic)stats.getStatistic(WSJTAStats.ActiveCount)).getCount();
        } catch (NullPointerException e) {
            throw new RuntimeException("invalid 'Transaction Manager' PMI settings.");
        }

        // Test output (Nagios performance data)
        result.setOutput("jta-activeCount=" + activeCount);

        // Test return code
        code = checkResult(activeCount, critical, warning);

        if (code == Status.WARNING.getCode() || code == Status.CRITICAL.getCode()) {
            result.setMessage("transaction active count (" + activeCount + ")");
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