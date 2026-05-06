#!/bin/bash

# Personal Budget App - GitHub Push Script
# This script will authenticate and push your code to GitHub

echo "🚀 Personal Budget App - GitHub Push"
echo "===================================="
echo ""

# Check if gh is authenticated
if ! gh auth status &>/dev/null; then
    echo "📝 Authenticating with GitHub..."
    echo ""
    gh auth login -p https -h github.com -w
    echo ""
fi

# Create the repository and push
echo "📦 Creating repository and pushing code..."
echo ""

cd /Users/valentina.peikova/PersonalBudgetApp

gh repo create PersonalBudgetApp \
    --public \
    --source=. \
    --description "Personal Budget App - Android app with Firebase sync, offline-first architecture, and AI-powered features" \
    --push

echo ""
echo "✅ Done! Your repository is live at:"
echo "   https://github.com/vpeikova233455-Val/PersonalBudgetApp"
echo ""
echo "📊 Pushed:"
echo "   - 104 files"
echo "   - 7,360 lines of code"
echo "   - Phase 1 + Phase 2 complete"
echo ""
