#!/usr/bin/env bash
# Defeat transition/cancellation/disconnect checks; disposable server with accepted test EULA.
set -euo pipefail
cd /workspace/Shirokai-Raids
SHIROKAI_INTEGRATION_MODE=victory bash scripts/run-teleport-integration.sh
