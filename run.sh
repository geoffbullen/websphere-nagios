#!/bin/bash

cd $(dirname "$0")

JAVA_HOME=""
JAVA_OPTS="-Dwasagent.host=127.0.0.1 -Dwasagent.port=9090 -Dwasagent.configuration=wasagent.properties"
CLASSPATH=".:wasagent-r220.jar"

for jar in $(find "lib" -name '*.jar'); do
  CLASSPATH=${CLASSPATH}:${jar};
done

${JAVA_HOME}/bin/java -Xmx16m -cp ${CLASSPATH} ${JAVA_OPTS} net.wait4it.nagios.wasagent.core.WASAgent > /dev/null 2>&1 &
