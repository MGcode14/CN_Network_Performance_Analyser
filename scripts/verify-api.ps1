$ErrorActionPreference = "Stop"

function Run-TestAndPrint($label, $bodyObj) {
    Write-Host "`n--------------------------------------------------"
    Write-Host "Executing $label..."
    $json = $bodyObj | ConvertTo-Json
    $startRes = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/tests" -Method POST -Body $json -ContentType "application/json"
    $expId = $startRes.id
    Write-Host "Test queued. ID: $expId"

    # Wait for completion
    $exp = $null
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 400
        $exp = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/tests/$expId"
        if ($exp.status -eq "COMPLETED" -or $exp.status -eq "FAILED" -or $exp.status -eq "CANCELLED") {
            break
        }
    }

    Write-Host "Result Status: $($exp.status)"
    Write-Host "Packets Sent: $($exp.packetsSent) | Received: $($exp.packetsReceived)"
    Write-Host "Throughput: $($exp.throughputMbps) Mbps | Rate: $($exp.dataRateMBs) MB/s"
    Write-Host "Avg RTT: $($exp.avgRttMs) ms | Min: $($exp.minRttMs) ms | Median: $($exp.medianRttMs) ms | P95: $($exp.p95RttMs) ms"
    Write-Host "Jitter: $($exp.jitterMs) ms | Packet Loss: $($exp.packetLossPct)%"
    Write-Host "Duration: $($exp.durationMs) ms"
    return $expId
}

# 1. TCP RTT
$tcpRttId = Run-TestAndPrint "1. TCP RTT Test (100 packets, 1024B)" @{
    targetHost = "127.0.0.1"
    tcpPort = 9091
    udpPort = 9092
    protocol = "TCP"
    testMode = "RTT"
    payloadSize = 1024
    packetCount = 100
    warmupCount = 5
    timeoutMs = 5000
    linkCapacityMbps = 100.0
}

# 2. UDP RTT
$udpRttId = Run-TestAndPrint "2. UDP RTT Test (100 packets, 1024B)" @{
    targetHost = "127.0.0.1"
    tcpPort = 9091
    udpPort = 9092
    protocol = "UDP"
    testMode = "RTT"
    payloadSize = 1024
    packetCount = 100
    warmupCount = 5
    timeoutMs = 5000
    linkCapacityMbps = 100.0
}

# 3. TCP Bulk Throughput
$tcpBulkId = Run-TestAndPrint "3. TCP Bulk Transfer (50 packets, 8192B)" @{
    targetHost = "127.0.0.1"
    tcpPort = 9091
    udpPort = 9092
    protocol = "TCP"
    testMode = "BULK_TRANSFER"
    payloadSize = 8192
    packetCount = 50
    warmupCount = 0
    timeoutMs = 5000
    linkCapacityMbps = 100.0
}

# 4. UDP Bulk Throughput (Paced)
$udpBulkId = Run-TestAndPrint "4. UDP Bulk Transfer (50 packets, 8192B, Paced 1000 pps)" @{
    targetHost = "127.0.0.1"
    tcpPort = 9091
    udpPort = 9092
    protocol = "UDP"
    testMode = "BULK_TRANSFER"
    payloadSize = 8192
    packetCount = 50
    sendingRatePps = 1000
    warmupCount = 0
    timeoutMs = 5000
    linkCapacityMbps = 100.0
}

# 5. Compare TCP RTT vs UDP RTT
Write-Host "`n--------------------------------------------------"
Write-Host "5. Testing Comparison API (/api/comparisons)..."
$comp = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/comparisons?tcpId=$tcpRttId&udpId=$udpRttId"
Write-Host "Comparable: $($comp.comparable)"
Write-Host "Throughput Diff: $($comp.throughputDifferenceMbps) Mbps"
Write-Host "RTT Diff: $($comp.avgRttDifferenceMs) ms"
Write-Host "Faster Protocol: $($comp.fasterProtocol)"
Write-Host "Lower Latency Protocol: $($comp.lowerLatencyProtocol)"
Write-Host "Analysis: $($comp.technicalAnalysis)"

Write-Host "`nAll Verification Experiments Completed Successfully!"
