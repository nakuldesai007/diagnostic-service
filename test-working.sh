#!/bin/bash

echo "🔧 Testing End-to-End Functionality"
echo "==================================="

# Test 1: Send a simple message and check if it gets processed
echo "Test 1: Sending simple message..."
echo 'working-test-001:{"id":"working-test-001","name":"Working Test","data":"This should work","testType":"SUCCESS","timestamp":"2025-09-28T04:50:00.000Z"}' | /opt/homebrew/opt/kafka/bin/kafka-console-producer \
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

echo "Checking database for new messages..."
export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"
psql -U diagnostic_user -d diagnostic_service -c "SELECT message_id, topic, processing_status, created_at FROM message_logs ORDER BY created_at DESC LIMIT 5;"

echo "Test 2: Sending error message to trigger retry logic..."
echo 'error-test-001:{"id":"error-test-001","name":"Error Test","data":"Connection timeout error","testType":"TIMEOUT","errorType":"TIMEOUT","timestamp":"2025-09-28T04:50:00.000Z"}' | /opt/homebrew/opt/kafka/bin/kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic projection-processing-queue \
    --property "key.separator=:" \
    --property "parse.key=true" \
    --property "key.serializer=org.apache.kafka.common.serialization.StringSerializer" \
    --property "value.serializer=org.apache.kafka.common.serialization.StringSerializer"

echo "Waiting 10 seconds for processing..."
sleep 10

echo "Checking stats after error message..."
curl -s http://localhost:8080/api/diagnostic/stats | jq .

echo "Checking database for new messages..."
psql -U diagnostic_user -d diagnostic_service -c "SELECT message_id, topic, processing_status, created_at FROM message_logs ORDER BY created_at DESC LIMIT 5;"

echo "Test 3: Sending message directly to failed-projection-messages..."
echo 'failed-test-001:{"messageId":"failed-test-001","originalMessage":"Test failed message","errorMessage":"Database connection failed","sourceTopic":"projection-processing-queue","partition":0,"offset":100,"failureTimestamp":"2025-09-28T04:50:00.000Z"}' | /opt/homebrew/opt/kafka/bin/kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic failed-projection-messages \
    --property "key.separator=:" \
    --property "parse.key=true" \
    --property "key.serializer=org.apache.kafka.common.serialization.StringSerializer" \
    --property "value.serializer=org.apache.kafka.common.serialization.StringSerializer"

echo "Waiting 10 seconds for processing..."
sleep 10

echo "Final stats..."
curl -s http://localhost:8080/api/diagnostic/stats | jq .

echo "Final database state..."
psql -U diagnostic_user -d diagnostic_service -c "SELECT message_id, topic, processing_status, created_at FROM message_logs ORDER BY created_at DESC LIMIT 10;"

echo "Test completed!"
