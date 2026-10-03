#!/usr/bin/env bash
set -euo pipefail

# Test script to validate secret providers
# Usage: ./test-providers.sh [provider]

CHART_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

test_provider() {
    local provider=$1
    local values_file=$2

    echo "Testing provider: $provider with $values_file"

    # Create test values
    cat > "$TEMP_DIR/test-values.yaml" <<EOF
$(cat "$values_file")
secrets:
  provider: $provider
EOF

    # Test helm lint
    echo "  Running helm lint..."
    if ! helm lint "$CHART_DIR" -f "$TEMP_DIR/test-values.yaml" > "$TEMP_DIR/lint-$provider.log" 2>&1; then
        echo "  ❌ helm lint failed"
        cat "$TEMP_DIR/lint-$provider.log"
        return 1
    fi
    echo "  ✓ helm lint passed"

    # Test helm template
    echo "  Running helm template..."
    if ! helm template chronos "$CHART_DIR" -f "$TEMP_DIR/test-values.yaml" > "$TEMP_DIR/template-$provider.yaml" 2>&1; then
        echo "  ❌ helm template failed"
        cat "$TEMP_DIR/template-$provider.yaml"
        return 1
    fi
    echo "  ✓ helm template succeeded"

    # Check for specific patterns
    case $provider in
        bitwarden)
            if ! grep -q "kind: BitwardenSecret" "$TEMP_DIR/template-$provider.yaml"; then
                echo "  ❌ BitwardenSecret not found in output"
                return 1
            fi
            echo "  ✓ BitwardenSecret found"
            ;;
        values)
            if grep -q "kind: BitwardenSecret" "$TEMP_DIR/template-$provider.yaml"; then
                echo "  ❌ BitwardenSecret should not be in output"
                return 1
            fi
            # Should have a plain Secret for chronos-secret
            if ! grep -A5 "kind: Secret" "$TEMP_DIR/template-$provider.yaml" | grep -q "name: chronos-secret"; then
                echo "  ❌ Plain Secret 'chronos-secret' not found"
                return 1
            fi
            echo "  ✓ Plain Secret found, BitwardenSecret not present"
            ;;
        existing)
            if grep -q "kind: BitwardenSecret" "$TEMP_DIR/template-$provider.yaml"; then
                echo "  ❌ BitwardenSecret should not be in output"
                return 1
            fi
            if grep -A5 "kind: Secret" "$TEMP_DIR/template-$provider.yaml" | grep -q "name: chronos-secret"; then
                echo "  ❌ Plain Secret 'chronos-secret' should not be created"
                return 1
            fi
            echo "  ✓ Neither BitwardenSecret nor chronos-secret Secret created"
            ;;
    esac

    return 0
}

echo "=== Helm Chart Provider Tests ==="

# Test current values files with bitwarden (default or explicit)
echo ""
echo "### Testing with existing values files ###"
test_provider "bitwarden" "values-prod.yaml" || exit 1
test_provider "bitwarden" "values-staging.yaml" || exit 1

echo ""
echo "✅ All tests passed"
