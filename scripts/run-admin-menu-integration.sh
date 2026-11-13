#!/usr/bin/env bash
# Read-only admin menu checks in a disposable dedicated server; previously accepted test EULA.
set -euo pipefail
cd /workspace/Shirokai-Raids
SHIROKAI_INTEGRATION_MODE=admin bash scripts/run-teleport-integration.sh
