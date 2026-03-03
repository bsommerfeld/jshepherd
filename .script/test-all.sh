#!/bin/bash
# ============================================
# jShepherd - Run All Tests
# ============================================
# Runs both module tests and integration tests
# Usage: ./test-all.sh

set -e

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║           jShepherd - Running All Tests                        ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Step 1: Build and test all modules
echo "▶ [1/2] Building and testing main modules..."
mvn clean install -Dgpg.skip=true -q

if [ $? -eq 0 ]; then
    echo "✅ Main modules: BUILD SUCCESS"
else
    echo "❌ Main modules: BUILD FAILED"
    exit 1
fi

echo ""

# Step 2: Run integration tests
echo "▶ [2/2] Running integration tests..."
mvn test -f .integration-tests/pom.xml -q

if [ $? -eq 0 ]; then
    echo "✅ Integration tests: BUILD SUCCESS"
else
    echo "❌ Integration tests: BUILD FAILED"
    exit 1
fi

echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║           ✅ ALL TESTS PASSED                                  ║"
echo "╚════════════════════════════════════════════════════════════════╝"
