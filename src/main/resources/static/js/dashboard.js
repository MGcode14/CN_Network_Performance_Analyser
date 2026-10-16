/**
 * Dashboard state, live metrics updates, test execution, and comparison module.
 */

import {
  fetchServerStatus,
  startServer,
  stopServer,
  startTest,
  fetchActiveTest,
  fetchTestDetails,
  cancelTest,
  fetchComparison,
  fetchMetricsSummary,
  fetchDiagnostics,
  fetchHistory
} from './api.js';

import {
  initCharts,
  updateRttChart,
  updateThroughputChart,
  updateComparisonChart,
  updatePacketLossChart,
  resetCharts
} from './charts.js';

import { loadHistoryTable } from './history.js';

let activeTestPollTimer = null;
let serverStatusPollTimer = null;
let currentActiveTestId = null;

export function initDashboard() {
  initCharts();
  setupEventListeners();
  pollServerStatus();
  pollMetricsSummary();
  loadHistoryTable();

  // Poll intervals
  serverStatusPollTimer = setInterval(pollServerStatus, 3000);
  checkActiveTestLoop();
}

function setupEventListeners() {
  // Preset buttons for payload size
  document.querySelectorAll('.preset-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      document.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const size = btn.getAttribute('data-size');
      const input = document.getElementById('payloadSizeInput');
      if (input) input.value = size;
    });
  });

  // Protocol select change
  const protoSelect = document.getElementById('protocolSelect');
  if (protoSelect) {
    protoSelect.addEventListener('change', (e) => {
      const val = e.target.value;
      const tcpGroup = document.getElementById('tcpPortGroup');
      const udpGroup = document.getElementById('udpPortGroup');
      const altGroup = document.getElementById('altOrderGroup');

      if (tcpGroup) tcpGroup.style.display = (val === 'UDP') ? 'none' : 'block';
      if (udpGroup) udpGroup.style.display = (val === 'TCP') ? 'none' : 'block';
      if (altGroup) altGroup.style.display = (val === 'BOTH') ? 'block' : 'none';
    });
  }

  // Run Test Form Submit
  const form = document.getElementById('testConfigForm');
  if (form) {
    form.addEventListener('submit', handleTestSubmit);
  }

  // Cancel Test Button
  const cancelBtn = document.getElementById('cancelTestBtn');
  if (cancelBtn) {
    cancelBtn.addEventListener('click', handleCancelTest);
  }

  // Receiver Controls
  const startRecvBtn = document.getElementById('startReceiversBtn');
  if (startRecvBtn) {
    startRecvBtn.addEventListener('click', async () => {
      try {
        await startServer();
        pollServerStatus();
      } catch (err) {
        alert(err.message);
      }
    });
  }

  const stopRecvBtn = document.getElementById('stopReceiversBtn');
  if (stopRecvBtn) {
    stopRecvBtn.addEventListener('click', async () => {
      try {
        await stopServer();
        pollServerStatus();
      } catch (err) {
        alert(err.message);
      }
    });
  }

  // Refresh All
  const refreshBtn = document.getElementById('refreshAllBtn');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', () => {
      pollServerStatus();
      pollMetricsSummary();
      loadHistoryTable();
      populateComparisonDropdowns();
    });
  }

  // Compare Tab button trigger
  const runCompareBtn = document.getElementById('runCompareBtn');
  if (runCompareBtn) {
    runCompareBtn.addEventListener('click', handleRunComparison);
  }
}

async function pollServerStatus() {
  try {
    const status = await fetchServerStatus();
    if (!status) return;

    // TCP Pill
    const tcpDot = document.getElementById('tcpStatusDot');
    const tcpText = document.getElementById('tcpStatusText');
    if (tcpDot && tcpText) {
      if (status.tcpRunning) {
        tcpDot.className = 'status-dot online';
        tcpText.textContent = `TCP :${status.tcpPort} [ONLINE]`;
      } else {
        tcpDot.className = 'status-dot offline';
        tcpText.textContent = `TCP :${status.tcpPort} [OFFLINE]`;
      }
    }

    // UDP Pill
    const udpDot = document.getElementById('udpStatusDot');
    const udpText = document.getElementById('udpStatusText');
    if (udpDot && udpText) {
      if (status.udpRunning) {
        udpDot.className = 'status-dot online';
        udpText.textContent = `UDP :${status.udpPort} [ONLINE]`;
      } else {
        udpDot.className = 'status-dot offline';
        udpText.textContent = `UDP :${status.udpPort} [OFFLINE]`;
      }
    }
  } catch (err) {
    console.debug('Server status poll error:', err.message);
  }
}

async function pollMetricsSummary() {
  try {
    const summary = await fetchMetricsSummary();
    if (!summary) return;

    const countEl = document.getElementById('overviewTotalExp');
    if (countEl) countEl.textContent = summary.totalExperiments || '0';

    const tpEl = document.getElementById('overviewAvgThroughput');
    if (tpEl) tpEl.textContent = summary.overallAvgThroughputMbps ? `${summary.overallAvgThroughputMbps.toFixed(2)} Mbps` : 'N/A';

    const rttEl = document.getElementById('overviewAvgRtt');
    if (rttEl) rttEl.textContent = summary.overallAvgRttMs ? `${summary.overallAvgRttMs.toFixed(2)} ms` : 'N/A';
  } catch (err) {
    console.debug('Metrics summary poll error:', err);
  }
}

async function handleTestSubmit(e) {
  e.preventDefault();
  hideValidationError();

  const targetHost = document.getElementById('targetHostInput').value.trim();
  const tcpPort = parseInt(document.getElementById('tcpPortInput').value, 10);
  const udpPort = parseInt(document.getElementById('udpPortInput').value, 10);
  const protocol = document.getElementById('protocolSelect').value;
  const testMode = document.getElementById('testModeSelect').value;
  const payloadSize = parseInt(document.getElementById('payloadSizeInput').value, 10);
  const packetCount = parseInt(document.getElementById('packetCountInput').value, 10);
  const sendingRatePps = parseInt(document.getElementById('sendingRateInput').value, 10) || 0;
  const warmupCount = parseInt(document.getElementById('warmupInput').value, 10) || 0;
  const timeoutMs = parseInt(document.getElementById('timeoutInput').value, 10) || 5000;
  const linkCapStr = document.getElementById('linkCapacityInput').value.trim();
  const linkCapacityMbps = linkCapStr ? parseFloat(linkCapStr) : null;
  const alternatingOrder = document.getElementById('altOrderCheck') ? document.getElementById('altOrderCheck').checked : false;

  // Frontend validations
  if (!targetHost) {
    showValidationError('Target host cannot be empty.');
    return;
  }
  if ((protocol === 'TCP' || protocol === 'BOTH') && (isNaN(tcpPort) || tcpPort < 1 || tcpPort > 65535)) {
    showValidationError('Invalid TCP port. Must be between 1 and 65535.');
    return;
  }
  if ((protocol === 'UDP' || protocol === 'BOTH') && (isNaN(udpPort) || udpPort < 1 || udpPort > 65535)) {
    showValidationError('Invalid UDP port. Must be between 1 and 65535.');
    return;
  }
  if (isNaN(payloadSize) || payloadSize < 0) {
    showValidationError('Payload size cannot be negative.');
    return;
  }
  if ((protocol === 'UDP' || protocol === 'BOTH') && payloadSize > 65463) {
    showValidationError('UDP datagram payload size cannot exceed 65,463 bytes (accounting for 44-byte protocol header).');
    return;
  }
  if (isNaN(packetCount) || packetCount < 1) {
    showValidationError('Packet count must be at least 1.');
    return;
  }

  const config = {
    targetHost,
    tcpPort,
    udpPort,
    protocol,
    testMode,
    payloadSize,
    packetCount,
    sendingRatePps,
    warmupCount,
    timeoutMs,
    linkCapacityMbps,
    alternatingOrder
  };

  try {
    const runBtn = document.getElementById('runTestBtn');
    if (runBtn) {
      runBtn.disabled = true;
      runBtn.innerHTML = `<span class="status-dot active" style="width:6px; height:6px;"></span> Running...`;
    }

    resetCharts();
    clearLivePerformanceCards();
    showProgressBar(true);

    const started = await startTest(config);
    currentActiveTestId = started.id;
    checkActiveTestLoop();
  } catch (err) {
    showValidationError(err.message);
    const runBtn = document.getElementById('runTestBtn');
    if (runBtn) {
      runBtn.disabled = false;
      runBtn.innerHTML = 'Run Experiment';
    }
    showProgressBar(false);
  }
}

async function handleCancelTest() {
  if (!currentActiveTestId) return;
  try {
    await cancelTest(currentActiveTestId);
    setProgressBarMessage('Cancelling experiment...');
  } catch (err) {
    alert('Failed to cancel: ' + err.message);
  }
}

async function checkActiveTestLoop() {
  try {
    const active = await fetchActiveTest();
    if (active && active.active) {
      currentActiveTestId = active.id;
      showProgressBar(true);
      updateProgressBar(active.progress || 0, active.message || 'Running test...');

      // Keep polling active test
      if (activeTestPollTimer) clearTimeout(activeTestPollTimer);
      activeTestPollTimer = setTimeout(checkActiveTestLoop, 300);
    } else {
      // Test ended or no active test
      if (currentActiveTestId) {
        // Fetch completed experiment details to display final metrics & charts
        const completed = await fetchTestDetails(currentActiveTestId);
        if (completed) {
          displayCompletedExperiment(completed);
        }
        currentActiveTestId = null;
        loadHistoryTable();
        pollMetricsSummary();
        populateComparisonDropdowns();
      }

      showProgressBar(false);
      const runBtn = document.getElementById('runTestBtn');
      if (runBtn) {
        runBtn.disabled = false;
        runBtn.innerHTML = 'Run Experiment';
      }
    }
  } catch (err) {
    console.debug('Active test check error:', err);
  }
}

function displayCompletedExperiment(exp) {
  // Update live performance cards
  setCardValue('cardAvgRtt', exp.avgRttMs != null ? exp.avgRttMs.toFixed(2) : 'N/A', 'ms');
  setCardValue('cardThroughput', exp.throughputMbps != null ? exp.throughputMbps.toFixed(2) : 'N/A', 'Mbps');
  setCardValue('cardTransferRate', exp.dataRateMBs != null ? exp.dataRateMBs.toFixed(2) : 'N/A', 'MB/s');
  setCardValue('cardPacketLoss', exp.packetLossPct != null ? exp.packetLossPct.toFixed(1) : 'N/A', '%');
  setCardValue('cardJitter', exp.jitterMs != null ? exp.jitterMs.toFixed(2) : 'N/A', 'ms');
  setCardValue('cardDeliveryRatio', exp.deliveryRatioPct != null ? exp.deliveryRatioPct.toFixed(1) : 'N/A', '%');
  setCardValue('cardDuration', exp.durationMs != null ? exp.durationMs.toFixed(0) : 'N/A', 'ms');

  // Bandwidth utilization
  if (exp.bandwidthUtilPct != null) {
    setCardValue('cardBandwidthUtil', exp.bandwidthUtilPct.toFixed(1), '%');
  } else {
    setCardValue('cardBandwidthUtil', 'N/A', '');
  }

  // Update charts
  if (exp.samples && exp.samples.length > 0) {
    updateRttChart(exp.samples);
    updateThroughputChart(exp.samples);
  }
  updatePacketLossChart(exp.packetsSent, exp.packetsReceived, exp.packetLossPct);
}

function clearLivePerformanceCards() {
  setCardValue('cardAvgRtt', '...', '');
  setCardValue('cardThroughput', '...', '');
  setCardValue('cardTransferRate', '...', '');
  setCardValue('cardPacketLoss', '...', '');
  setCardValue('cardJitter', '...', '');
  setCardValue('cardDeliveryRatio', '...', '');
  setCardValue('cardDuration', '...', '');
  setCardValue('cardBandwidthUtil', '...', '');
}

function setCardValue(cardId, value, unit) {
  const el = document.getElementById(cardId);
  if (!el) return;
  el.innerHTML = `${value} <span class="metric-unit">${unit}</span>`;
}

function showProgressBar(show) {
  const pCard = document.getElementById('progressCard');
  if (pCard) pCard.style.display = show ? 'block' : 'none';
}

function updateProgressBar(percent, message) {
  const fill = document.getElementById('progressBarFill');
  const pctEl = document.getElementById('progressPercentText');
  const msgEl = document.getElementById('progressStatusMessage');
  const rounded = Math.min(100, Math.max(0, Math.round(percent)));

  if (fill) fill.style.width = `${rounded}%`;
  if (pctEl) pctEl.textContent = `${rounded}%`;
  if (msgEl) msgEl.textContent = message;
}

function setProgressBarMessage(message) {
  const msgEl = document.getElementById('progressStatusMessage');
  if (msgEl) msgEl.textContent = message;
}

function showValidationError(msg) {
  const box = document.getElementById('validationBox');
  if (box) {
    box.textContent = msg;
    box.style.display = 'block';
  }
}

function hideValidationError() {
  const box = document.getElementById('validationBox');
  if (box) box.style.display = 'none';
}

// --- Comparison Tab Logic ---

export async function populateComparisonDropdowns() {
  try {
    const tcpList = await fetchHistory({ protocol: 'TCP', limit: 20 });
    const udpList = await fetchHistory({ protocol: 'UDP', limit: 20 });

    const tcpSelect = document.getElementById('compareTcpSelect');
    const udpSelect = document.getElementById('compareUdpSelect');

    if (tcpSelect && tcpList.items) {
      tcpSelect.innerHTML = tcpList.items.map(t => `
        <option value="${t.id}">${t.name} - ${new Date(t.createdAt).toLocaleTimeString()} (${t.throughputMbps ? t.throughputMbps.toFixed(1) + ' Mbps' : 'RTT ' + t.avgRttMs + 'ms'})</option>
      `).join('');
    }

    if (udpSelect && udpList.items) {
      udpSelect.innerHTML = udpList.items.map(u => `
        <option value="${u.id}">${u.name} - ${new Date(u.createdAt).toLocaleTimeString()} (${u.throughputMbps ? u.throughputMbps.toFixed(1) + ' Mbps' : 'RTT ' + u.avgRttMs + 'ms'})</option>
      `).join('');
    }
  } catch (err) {
    console.debug('Failed to populate comparison dropdowns:', err);
  }
}

async function handleRunComparison() {
  const tcpId = document.getElementById('compareTcpSelect').value;
  const udpId = document.getElementById('compareUdpSelect').value;
  const resultContainer = document.getElementById('comparisonResultsArea');

  if (!tcpId || !udpId) {
    alert('Please select both a TCP and a UDP experiment to compare.');
    return;
  }

  try {
    resultContainer.innerHTML = `<div style="text-align:center; padding: 2rem; color: var(--cyan-primary);">Computing comparison analysis...</div>`;
    const res = await fetchComparison(tcpId, udpId);

    const tcp = res.tcpExperiment;
    const udp = res.udpExperiment;

    const warnHtml = res.warnings && res.warnings.length > 0 ? `
      <div style="background: rgba(245, 158, 11, 0.12); border: 1px solid rgba(245, 158, 11, 0.4); color: #fbbf24; padding: 0.75rem 1rem; border-radius: var(--radius-sm); margin-bottom: 1rem; font-size: 0.82rem;">
        <strong>Notice:</strong> ${res.warnings.join(' ')}
      </div>
    ` : '';

    resultContainer.innerHTML = `
      ${warnHtml}
      <div class="analysis-box">
        <h3>Automated Network Protocol Analysis</h3>
        <p>${res.technicalAnalysis}</p>
      </div>

      <div class="table-responsive" style="margin-top:1.25rem;">
        <table class="data-table">
          <thead>
            <tr>
              <th>Performance Metric</th>
              <th>TCP Result</th>
              <th>UDP Result</th>
              <th>Delta Difference</th>
              <th>Advantage</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Measured Throughput</strong></td>
              <td style="color:var(--blue-primary); font-weight:600;">${tcp.throughputMbps != null ? tcp.throughputMbps.toFixed(2) + ' Mbps' : 'N/A'}</td>
              <td style="color:var(--cyan-primary); font-weight:600;">${udp.throughputMbps != null ? udp.throughputMbps.toFixed(2) + ' Mbps' : 'N/A'}</td>
              <td>${res.throughputDifferenceMbps != null ? (res.throughputDifferenceMbps > 0 ? '+' : '') + res.throughputDifferenceMbps.toFixed(2) + ' Mbps' : 'N/A'}</td>
              <td><span class="badge ${res.fasterProtocol === 'TCP' ? 'badge-tcp' : (res.fasterProtocol === 'UDP' ? 'badge-udp' : 'badge-neutral')}">${res.fasterProtocol || 'N/A'}</span></td>
            </tr>
            <tr>
              <td><strong>Average RTT Latency</strong></td>
              <td style="color:var(--blue-primary); font-weight:600;">${tcp.avgRttMs != null ? tcp.avgRttMs.toFixed(2) + ' ms' : 'N/A'}</td>
              <td style="color:var(--cyan-primary); font-weight:600;">${udp.avgRttMs != null ? udp.avgRttMs.toFixed(2) + ' ms' : 'N/A'}</td>
              <td>${res.avgRttDifferenceMs != null ? (res.avgRttDifferenceMs > 0 ? '+' : '') + res.avgRttDifferenceMs.toFixed(2) + ' ms' : 'N/A'}</td>
              <td><span class="badge ${res.lowerLatencyProtocol === 'TCP' ? 'badge-tcp' : (res.lowerLatencyProtocol === 'UDP' ? 'badge-udp' : 'badge-neutral')}">${res.lowerLatencyProtocol || 'N/A'}</span></td>
            </tr>
            <tr>
              <td><strong>Packet Loss</strong></td>
              <td>${tcp.packetLossPct != null ? tcp.packetLossPct.toFixed(1) + '%' : 'N/A'}</td>
              <td>${udp.packetLossPct != null ? udp.packetLossPct.toFixed(1) + '%' : 'N/A'}</td>
              <td>${res.packetLossDifferencePct != null ? res.packetLossDifferencePct.toFixed(1) + '%' : 'N/A'}</td>
              <td><span class="badge ${res.lowerLossProtocol === 'TCP' ? 'badge-tcp' : (res.lowerLossProtocol === 'UDP' ? 'badge-udp' : 'badge-neutral')}">${res.lowerLossProtocol || 'N/A'}</span></td>
            </tr>
            <tr>
              <td><strong>Jitter Estimate</strong></td>
              <td>${tcp.jitterMs != null ? tcp.jitterMs.toFixed(2) + ' ms' : 'N/A'}</td>
              <td>${udp.jitterMs != null ? udp.jitterMs.toFixed(2) + ' ms' : 'N/A'}</td>
              <td>${res.jitterDifferenceMs != null ? res.jitterDifferenceMs.toFixed(2) + ' ms' : 'N/A'}</td>
              <td>-</td>
            </tr>
            <tr>
              <td><strong>Transmission Duration</strong></td>
              <td>${tcp.durationMs != null ? tcp.durationMs.toFixed(0) + ' ms' : 'N/A'}</td>
              <td>${udp.durationMs != null ? udp.durationMs.toFixed(0) + ' ms' : 'N/A'}</td>
              <td>${res.durationDifferenceMs != null ? res.durationDifferenceMs.toFixed(0) + ' ms' : 'N/A'}</td>
              <td>-</td>
            </tr>
          </tbody>
        </table>
      </div>
    `;

    updateComparisonChart(res);
  } catch (err) {
    resultContainer.innerHTML = `<div style="color:var(--rose-danger); padding:1rem;">Error comparing experiments: ${err.message}</div>`;
  }
}

// --- Diagnostics Tab Logic ---

export async function loadDiagnosticsTab() {
  const container = document.getElementById('diagnosticsContent');
  if (!container) return;

  try {
    const diag = await fetchDiagnostics();
    if (!diag) return;

    const s = diag.serverStatus || {};
    const events = diag.recentEvents || [];

    container.innerHTML = `
      <div class="grid-top" style="grid-template-columns: 1fr 1fr; margin-bottom:1.5rem;">
        <div class="card">
          <div class="card-title"><h2>JVM & System Status</h2></div>
          <div style="font-family:var(--font-mono); font-size:0.82rem; line-height:2;">
            <div><span style="color:var(--text-muted)">JVM Memory:</span> ${diag.jvmTotalMemoryMB - diag.jvmFreeMemoryMB} MB used / ${diag.jvmTotalMemoryMB} MB allocated (Max: ${diag.jvmMaxMemoryMB} MB)</div>
            <div><span style="color:var(--text-muted)">Active Threads:</span> ${diag.activeThreads}</div>
            <div><span style="color:var(--text-muted)">Uptime:</span> ${s.uptimeSeconds} seconds</div>
            <div><span style="color:var(--text-muted)">Web Server:</span> ${s.httpBindHost}:${s.httpPort}</div>
          </div>
        </div>

        <div class="card">
          <div class="card-title"><h2>Receiver Statistics</h2></div>
          <div style="font-family:var(--font-mono); font-size:0.82rem; line-height:2;">
            <div><span style="color:var(--text-muted)">TCP Receiver:</span> ${s.tcpBindHost}:${s.tcpPort} (${s.tcpRunning ? 'RUNNING' : 'STOPPED'})</div>
            <div><span style="color:var(--text-muted)">TCP Active Connections:</span> ${s.tcpActiveConnections} | Total Messages: ${s.tcpMessagesProcessed}</div>
            <div><span style="color:var(--text-muted)">UDP Receiver:</span> ${s.udpBindHost}:${s.udpPort} (${s.udpRunning ? 'RUNNING' : 'STOPPED'})</div>
            <div><span style="color:var(--text-muted)">UDP Packets Received:</span> ${s.udpPacketsReceived} | Sent: ${s.udpPacketsSent}</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title"><h2>Recent Diagnostic Event Logs</h2></div>
        <div class="table-responsive" style="max-height:350px; overflow-y:auto;">
          <table class="data-table">
            <thead>
              <tr>
                <th>Event Type</th>
                <th>Message</th>
                <th>Details</th>
                <th>Time</th>
              </tr>
            </thead>
            <tbody>
              ${events.map(ev => `
                <tr>
                  <td><span class="badge badge-neutral">${ev.eventType}</span></td>
                  <td>${ev.message}</td>
                  <td style="color:var(--text-muted); font-size:0.75rem;">${ev.details || '-'}</td>
                  <td>${new Date(ev.timestampMs).toLocaleTimeString()}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </div>
    `;
  } catch (err) {
    container.innerHTML = `<div style="color:var(--rose-danger)">Error loading diagnostics: ${err.message}</div>`;
  }
}
