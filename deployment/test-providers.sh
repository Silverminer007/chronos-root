#!/usr/bin/env bash
set -euo pipefail

# Test script to validate secret providers
# Usage: ./test-providers.sh [provider]

CHART_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# GitHub Actions output helper
log_summary() {
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        echo "$1" >> "$GITHUB_STEP_SUMMARY"
    fi
}

test_provider() {
    local provider=$1
    local values_file=$2

    echo ""
    echo "### Testing provider: $provider"
    log_summary "Testing provider: **$provider**"

    # Determine which values file to use
    if [ ! -f "$CHART_DIR/$values_file" ]; then
        echo "❌ Values file not found: $values_file"
        return 1
    fi

    # Create test values
    cat > "$TEMP_DIR/test-values.yaml" <<EOF
$(cat "$CHART_DIR/$values_file")
secrets:
  provider: $provider
EOF

    # Test helm lint
    echo "Running helm lint..."
    if ! helm lint "$CHART_DIR" -f "$TEMP_DIR/test-values.yaml" > "$TEMP_DIR/lint-$provider.log" 2>&1; then
        echo "❌ helm lint failed:"
        cat "$TEMP_DIR/lint-$provider.log"
        return 1
    fi
    echo "✅ helm lint passed"
    log_summary "- ✅ helm lint passed"

    # Test helm template
    echo "Running helm template..."
    if ! helm template chronos "$CHART_DIR" -f "$TEMP_DIR/test-values.yaml" > "$TEMP_DIR/template-$provider.yaml" 2>&1; then
        echo "❌ helm template failed:"
        cat "$TEMP_DIR/template-$provider.yaml"
        return 1
    fi
    echo "✅ helm template succeeded"
    log_summary "- ✅ helm template succeeded"

    # Check for expected resources
    echo "Checking resource types..."
    case $provider in
        bitwarden)
            if ! grep -q "^kind: BitwardenSecret" "$TEMP_DIR/template-$provider.yaml"; then
                echo "❌ BitwardenSecret not found"
                return 1
            fi
            echo "✅ BitwardenSecret found"
            log_summary "- ✅ BitwardenSecret created"
            ;;
        values)
            if ! grep -q "^kind: Secret" "$TEMP_DIR/template-$provider.yaml"; then
                echo "❌ Secret not found"
                return 1
            fi
            echo "✅ Secret found"
            log_summary "- ✅ Plain Secret created"

            if grep -q "^kind: BitwardenSecret" "$TEMP_DIR/template-$provider.yaml"; then
                echo "❌ BitwardenSecret should not be present"
                return 1
            fi
            echo "✅ No BitwardenSecret (as expected)"
            ;;
        existing)
            if grep -q "^kind: BitwardenSecret" "$TEMP_DIR/template-$provider.yaml"; then
                echo "❌ BitwardenSecret should not be present"
                return 1
            fi
            if grep -A5 "^kind: Secret" "$TEMP_DIR/template-$provider.yaml" | grep -q "name: chronos-secret"; then
                echo "❌ chronos-secret Secret should not be created"
                return 1
            fi
            echo "✅ No secrets created (as expected)"
            log_summary "- ✅ No secrets created"
            ;;
    esac

    return 0
}

test_helper_functions() {
    echo ""
    echo "### Testing helper functions"
    log_summary "Testing helper functions"

    # Test that all templates use the helper for secret names
    echo "Checking that deployments use chronos.secretName helper..."

    local deployments
    deployments=$(find "$CHART_DIR/templates" -name "*-deployment.yaml")
    for deployment in $deployments; do
        if grep -q "chronos.secretName" "$deployment"; then
            echo "✅ $(basename "$deployment") uses chronos.secretName helper"
        else
            echo "❌ $(basename "$deployment") does not use chronos.secretName helper"
            return 1
        fi
    done

    log_summary "- ✅ All templates use helper functions"
    return 0
}

echo "=== Helm Chart Provider Tests ==="
log_summary "## Helm Chart Tests"

# Test all providers
for provider in "bitwarden" "values" "existing"; do
    case "$provider" in
        bitwarden)
            test_provider "bitwarden" "values-prod.yaml" || exit 1
            ;;
        values)
            test_provider "values" "values-values.yaml" || exit 1
            ;;
        existing)
            test_provider "existing" "values-existing.yaml" || exit 1
            ;;
    esac
done

# Test helper functions
test_helper_functions || exit 1

echo ""
echo "✅ All Helm chart tests passed!"
