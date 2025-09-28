#!/bin/bash

echo "🧪 Simple End-to-End Test"
echo "========================"

# Send a simple message
echo "Sending test message..."
echo 'simple-test-001:{"id":"simple-test-001","name":"Simple Test","data":"This should work","testType":"SUCCESS","timestamp":"2025-09-28T04:50:00.000Z"}' | /opt/homebrew/opt/kafka/bin/kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic projection-processing-queue \
    --property "key.separator=:" \
    --property "parse.key=true" \
    --property "key.serializer=org.apache.kafka.common.serialization.StringSerializer" \
    --property "value.serializer=org.apache.kafka.common.serialization.StringSerializer"

echo "Waiting 10 seconds for processing..."
sleep 10

echo "Checking stats..."
curl -s http://localhost:8080/api/diagnostic/stats | jq .

echo "Checking database..."
export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
psql -U diagnostic_user -d diagnostic_service -c "SELECT message_id, topic, processing_status, created_at FROM message_logs ORDER BY created_at DESC LIMIT 5;"

echo "Test completed!"
