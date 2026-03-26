#!/bin/bash

SERVER=$1
PORT=4022

if [ -z "$SERVER" ]; then
    echo "Usage: ./findflags.sh <server_ip>"
    echo "Example: ./findflags.sh 10.200.51.18"
    exit 1
fi

echo "Searching for valid flag on $SERVER:$PORT..."
echo "Press Ctrl+C to stop"
echo ""

COUNT=0

while true; do
    COUNT=$((COUNT + 1))

    # Connect and grab output, timeout after 5 seconds
    OUTPUT=$(nc -w 5 $SERVER $PORT 2>/dev/null)

    # Extract flag (first line) and given hash (last line)
    FLAG=$(echo "$OUTPUT" | grep "^IN2011" | head -1)
    GIVEN_HASH=$(echo "$OUTPUT" | grep "Its SHA-256 should be" | awk '{print $NF}')

    if [ -z "$FLAG" ] || [ -z "$GIVEN_HASH" ]; then
        echo "Attempt $COUNT: Could not parse output, retrying..."
        continue
    fi

    # Compute hash of the flag
    COMPUTED_HASH=$(echo -n "$FLAG" | sha256sum | cut -d' ' -f1)

    # Compare (case insensitive)
    if [ "${COMPUTED_HASH,,}" = "${GIVEN_HASH,,}" ]; then
        echo "========================================="
        echo "VALID FLAG FOUND after $COUNT attempts!"
        echo "Flag: $FLAG"
        echo "Hash: $COMPUTED_HASH"
        echo "========================================="
        exit 0
    else
        echo "Attempt $COUNT: No match, trying again..."
    fi
done
