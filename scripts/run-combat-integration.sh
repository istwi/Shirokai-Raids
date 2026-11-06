#!/usr/bin/env bash
# Same disposable runtime and explicit EULA contract as the teleport fixture.
set -euo pipefail
cd /workspace/Shirokai-Raids
SHIROKAI_INTEGRATION_MODE=combat bash scripts/run-teleport-integration.sh
