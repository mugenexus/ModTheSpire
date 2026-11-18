#!/bin/sh
cd -- "$(dirname -- "$0")" || exit 1

for java in ./jre/bin/java "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java)"; do
    if [ -x "$java" ] && "$java" -version 2>&1 | grep -q 'version "1\.8\.'; then
        exec "$java" -jar ModTheSpire.jar "$@"
    fi
done

echo 'ModTheSpire requires Java 8. Install it and set JAVA_HOME to its installation directory.' >&2
exit 1
