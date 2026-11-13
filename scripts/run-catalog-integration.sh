#!/usr/bin/env bash
# Real catalog metadata and twenty starts in a disposable dedicated server; accepted test EULA required.
set -euo pipefail
cd /workspace/Shirokai-Raids
SHIROKAI_INTEGRATION_MODE=catalog bash scripts/run-teleport-integration.sh
