#!/usr/bin/env bash
# Disposable native capture/inventory test; requires the user's accepted test-server EULA.
set -euo pipefail
cd /workspace/Shirokai-Raids
SHIROKAI_INTEGRATION_MODE=capture bash scripts/run-teleport-integration.sh
