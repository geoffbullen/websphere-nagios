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

package net.wait4it.nagios.wasagent.core;

import java.util.Map;

/**
 * @author Yann Lambret
 *
 */
public class WASStatsManager {

    private StringBuilder header = new StringBuilder();
    private StringBuilder message = new StringBuilder();
    private StringBuilder output = new StringBuilder();
    private String status = "OK";
    private int code = 0;

    /**
     * Instantiates a WebSphere proxy, and run all
     * the required tests based on the params contents.
     * 
     * @param  params HTTP request params
     * @return output Nagios performance data
     */
    public String process(Map<String, String> params) {
        WASClientProxy proxy = null;
        String serverName = "";

        try {
            proxy = new WASClientProxy(params);
            proxy.init();
            // We get the target instance name
            serverName = proxy.getServerName();
        } catch (Exception e) {
            e.printStackTrace();
            return "2|" + e;
        }

        for (Option option : Option.values()) {
            if (params.containsKey(option.getName())) {
                checkResult(option.getTest().run(proxy, params.get(option.getName())));
            }
        }

        header.append(serverName + ": ");
        header.append("status " + status);

        // Something went wrong. We should get useful information
        // from the message string
        if (! status.equals("OK") && message.length() > 0) {
            header.append(" - " + message.toString());
        }

        output.insert(0, header + "|");
        output.insert(0, code + "|");
        return output.toString();
    }

    private void checkResult(Result result) {
        String out = "";
        String msg = "";

        switch (result.getStatus()) {
        case OK:
            out = result.getOutput();
            break;
        case WARNING:
            if (code < 1) { code = 1; status = "WARNING"; }
            msg = result.getMessage();
            out = result.getOutput();
            break;
        case CRITICAL:
            if (code < 2) { code = 2; status = "CRITICAL"; }
            msg = result.getMessage();
            out = result.getOutput();
            break;
        case UNKNOWN:
            code = 3;
            status = "UNKNOWN";
            msg = result.getMessage();
            break;
        }

        if (msg.length() > 0) {
            if (message.length() > 0) {
                message.append(" - ");
            }
            message.append(msg);
        }

        if (out.length() > 0) {
            if (output.length() > 0) {
                output.append(" ");
            }
            output.append(out);
        }
    }

}
