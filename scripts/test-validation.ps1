$ErrorActionPreference = "Stop"

Write-Host "1. Testing Protocol BOTH (Dual comparison)..."
$both = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/tests" -Method POST -Body (@{
    targetHost="127.0.0.1";
    tcpPort=9091;
    udpPort=9092;
    protocol="BOTH";
    testMode="RTT";
    payloadSize=512;
    packetCount=30;
    timeoutMs=5000
} | ConvertTo-Json) -ContentType "application/json"
$bothId = $both.id
Write-Host "Both test started ID: $bothId"

for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Milliseconds 200
    $bothRes = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/tests/$bothId"
    if ($bothRes.status -eq "COMPLETED" -or $bothRes.status -eq "FAILED") {
        break
    }
}
Write-Host "Dual Protocol Result Status: $($bothRes.status)"
Write-Host "Packets Sent: $($bothRes.packetsSent) | Recv: $($bothRes.packetsReceived)"
Write-Host "Avg RTT: $($bothRes.avgRttMs) ms | Throughput: $($bothRes.throughputMbps) Mbps"
Write-Host "Error message: $($bothRes.errorMessage)"

Write-Host "`n2. Testing Metrics Summary (/api/metrics/summary)..."
$summary = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/metrics/summary"
Write-Host "Total Experiments: $($summary.totalExperiments)"
Write-Host "Overall Avg Throughput: $($summary.overallAvgThroughputMbps) Mbps"
Write-Host "Overall Avg RTT: $($summary.overallAvgRttMs) ms"
Write-Host "TCP Avg Throughput: $($summary.tcpAvgThroughputMbps) Mbps"
Write-Host "UDP Avg Throughput: $($summary.udpAvgThroughputMbps) Mbps"

Write-Host "`n3. Testing History Pagination (/api/history)..."
$hist = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/history?limit=10"
Write-Host "Total items in DB: $($hist.total) | Items returned: $($hist.items.Count)"

Write-Host "`n4. Testing Samples Endpoint (/api/tests/{id}/samples)..."
$samples = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/tests/$bothId/samples"
Write-Host "Samples stored and retrieved: $($samples.Count)"

Write-Host "`n5. Testing Boundary Validation (UDP payload > 65463 bytes)..."
try {
    Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/tests" -Method POST -Body (@{
        targetHost="127.0.0.1";
        tcpPort=9091;
        udpPort=9092;
        protocol="UDP";
        testMode="RTT";
        payloadSize=65500;
        packetCount=10
    } | ConvertTo-Json) -ContentType "application/json"
    Write-Host "FAILED: Expected error was not thrown!"
} catch {
    Write-Host "SUCCESS: Validation correctly rejected invalid UDP payload! Message: $($_.Exception.Message)"
}
