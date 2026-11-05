# Source this file in the managed cloud environment; contains no credentials.
export JAVA_HOME=/workspace/shared/jdk
export PATH="$JAVA_HOME/bin:$PATH"
if [ -n "${HTTPS_PROXY:-}" ]; then
    raids_proxy_host=$(python3 -c 'import os,urllib.parse; print(urllib.parse.urlparse(os.environ["HTTPS_PROXY"]).hostname)')
    raids_proxy_port=$(python3 -c 'import os,urllib.parse; print(urllib.parse.urlparse(os.environ["HTTPS_PROXY"]).port or 80)')
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttps.proxyHost=$raids_proxy_host -Dhttps.proxyPort=$raids_proxy_port -Dhttp.proxyHost=$raids_proxy_host -Dhttp.proxyPort=$raids_proxy_port"
fi
if [ -f /etc/ssl/certs/java/cacerts ]; then
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
