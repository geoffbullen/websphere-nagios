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

import net.wait4it.nagios.wasagent.core.Result;
import net.wait4it.nagios.wasagent.core.Status;
import net.wait4it.nagios.wasagent.core.WASClientProxy;

/**
 * Gets the depth of queues declared on the
 * WebSphere SIB provider. Also display stats
 * for the SIB error queue (*SYSTEM.Exception.Destination*)
 * 
 * @author Yann Lambret
 *
 */
public class SIBQueueTest extends TestUtils implements Test {

    /**
     * WebSphere SIB queues stats.
     * 
     * @param proxy   an applicative proxy for the target WAS instance
     * @param params  a pipe separated list of SIB queue names, or
     *                a wildcard character (*) for all SIB queues
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
        Map<String,String> queues = new HashMap<String,String>();

        // Test code for a specific queue
        int testCode = 0;

        // Message prefix
        String prefix = "queue depth: ";

        // Queue identifier
        String identifier = "";

        // Performance data
        long depth;

        // Parses HTTP query params
        for (String s : Arrays.asList(params.split("\\|"))) {
            queues.put(s.split(",", 2)[0], s.split(",", 2)[1]);
        }

        try {
            Set<ObjectName> mbeans = proxy.getMBeans("WebSphere:*,type=SIBQueuePoint");
            for (ObjectName mbean : mbeans) {
                identifier = (String)proxy.getAttribute(mbean, "identifier");

                // No statistics for WAS internal components
                if (identifier.matches("^_PSIMP.*||^_PTRM.*")) {
                    continue;
                }

                if (queues.containsKey("*") || queues.containsKey(identifier)) {
                    depth = (Long)proxy.getAttribute(mbean, "depth");

                    // Test output (Nagios performance data)
                    output.add("sib-queue-" + identifier + "=" + depth);

                    // Test return code
                    thresholds = queues.get("*") != null ? queues.get("*") : queues.get(identifier);
                    warning = Long.parseLong(thresholds.split(",")[0]);
                    critical = Long.parseLong(thresholds.split(",")[1]);
                    testCode = checkResult(depth, critical, warning);

                    if (testCode == Status.WARNING.getCode() || testCode == Status.CRITICAL.getCode()) {
                        message.add("'" + identifier + "' depth (" + depth + ")");
                        code = (testCode > code) ? testCode : code;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus(Status.UNKNOWN);
            result.setMessage(e.toString());
            return result;
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