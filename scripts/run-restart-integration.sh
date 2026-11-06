#!/usr/bin/env bash
set -euo pipefail
cd /workspace/Shirokai-Raids
source scripts/cloud-env.sh
python3 scripts/run_restart_integration.py
