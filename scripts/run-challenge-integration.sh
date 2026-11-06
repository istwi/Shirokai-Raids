#!/usr/bin/env bash
set -euo pipefail
cd /workspace/Shirokai-Raids
SHIROKAI_INTEGRATION_MODE=challenge bash scripts/run-teleport-integration.sh
