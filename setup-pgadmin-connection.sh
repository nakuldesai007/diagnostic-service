#!/bin/bash

# pgAdmin Connection Setup Helper Script
# This script provides instructions for setting up PostgreSQL connection in pgAdmin

echo "🔧 pgAdmin Connection Setup Guide"
echo "================================="
echo

echo "📋 Step-by-Step Instructions:"
echo "============================="
echo
echo "1. Open pgAdmin in your browser:"
echo "   http://localhost:8082"
echo
echo "2. Login with these credentials:"
echo "   Email: admin@diagnostic-service.com"
echo "   Password: admin123"
echo
echo "3. Add a new server connection:"
echo "   Right-click 'Servers' → 'Register' → 'Server...'"
echo
echo "4. In the 'General' tab:"
echo "   Name: Diagnostic Service DB"
echo
echo "5. In the 'Connection' tab:"
echo "   Host name/address: postgres"
echo "   Port: 5432"
echo "   Maintenance database: diagnostic_service"
echo "   Username: diagnostic_user"
echo "   Password: diagnostic_password"
echo
echo "6. Click 'Save' to create the connection"
echo
echo "7. You should now see the 'Diagnostic Service DB' server in the left panel"
echo
echo "8. Expand the server to explore:"
echo "   - Databases → diagnostic_service"
echo "   - Schemas → public"
echo "   - Tables → message_logs, circuit_breaker_events, retry_attempts, dead_letter_messages"
echo
echo "🔍 Quick Database Queries:"
echo "=========================="
echo
echo "View recent message logs:"
echo "SELECT * FROM message_logs ORDER BY created_at DESC LIMIT 10;"
echo
echo "View circuit breaker events:"
echo "SELECT * FROM circuit_breaker_events ORDER BY created_at DESC LIMIT 10;"
echo
echo "View retry attempts:"
echo "SELECT * FROM retry_attempts ORDER BY created_at DESC LIMIT 10;"
echo
echo "View dead letter messages:"
echo "SELECT * FROM dead_letter_messages ORDER BY created_at DESC LIMIT 10;"
echo
echo "📊 Database Statistics:"
echo "======================"
echo
echo "Message processing status:"
echo "SELECT processing_status, COUNT(*) as count FROM message_logs GROUP BY processing_status;"
echo
echo "Error categories:"
echo "SELECT error_category, COUNT(*) as count FROM message_logs WHERE error_category IS NOT NULL GROUP BY error_category ORDER BY count DESC;"
echo
echo "Processing time statistics:"
echo "SELECT processing_status, COUNT(*) as count, AVG(processing_time_ms) as avg_time_ms FROM message_logs WHERE processing_time_ms IS NOT NULL GROUP BY processing_status;"
echo
echo "🎯 Tips:"
echo "======="
echo "- Use the Query Tool (Tools → Query Tool) to run SQL queries"
echo "- Right-click on tables to view data or structure"
echo "- Use the Dashboard to see database statistics"
echo "- Export query results as CSV if needed"
echo
echo "✅ You're all set! pgAdmin is now ready to use."
