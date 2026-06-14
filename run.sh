#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
  echo "Java compiler not found."
  echo "Install with:"
  echo "  sudo apt update && sudo apt install default-jdk"
  exit 1
fi

if ! command -v fpcalc >/dev/null 2>&1; then
  echo "WARNING: fpcalc is not installed."
  echo "Install with:"
  echo "  sudo apt update && sudo apt install libchromaprint-tools"
fi

mkdir -p out
javac -d out src/FourierOnlineSongFinderPRO.java
java -cp out FourierOnlineSongFinderPRO
