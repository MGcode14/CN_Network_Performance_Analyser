/**
 * History view, filtering, and experiment detail modal management.
 */

import { fetchHistory, fetchTestDetails } from './api.js';

let currentProtocolFilter = 'ALL';
let currentModeFilter = 'ALL';
let currentSearch = '';
let currentPage = 0;
const pageSize = 15;

export async function loadHistoryTable() {
  const container = document.getElementById('historyTableBody');
  const countEl = document.getElementById('historyTotalCount');
  if (!container) return;

  try {
    const data = await fetchHistory({
      protocol: currentProtocolFilter,
      mode: currentModeFilter,
      search: currentSearch,
      limit: pageSize,
      offset: currentPage * pageSize
    });

    if (countEl) countEl.textContent = `${data.total} total`;

    if (!data.items || data.items.length === 0) {
      container.innerHTML = `<tr><td colspan="12" style="text-align:center; color: var(--text-muted); padding: 2rem;">No experiments found.</td></tr>`;
      return;
    }

    container.innerHTML = data.items.map(exp => {
      const timeStr = new Date(exp.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
      const dateStr = new Date(exp.createdAt).toLocaleDateString();
      const badgeProto = exp.protocol === 'TCP' ? 'badge-tcp' : (exp.protocol === 'UDP' ? 'badge-udp' : 'badge-both');
      const badgeStatus = exp.status === 'COMPLETED' ? 'badge-success' : (exp.status === 'FAILED' ? 'badge-danger' : 'badge-neutral');

      const tpStr = exp.throughputMbps !== null && exp.throughputMbps !== undefined ? `${exp.throughputMbps.toFixed(2)} Mbps` : 'N/A';
      const rttStr = exp.avgRttMs !== null && exp.avgRttMs !== undefined ? `${exp.avgRttMs.toFixed(2)} ms` : 'N/A';
      const lossStr = exp.packetLossPct !== null && exp.packetLossPct !== undefined ? `${exp.packetLossPct.toFixed(1)}%` : 'N/A';

      return `
        <tr>
          <td><span style="font-size:0.75rem; color:var(--text-muted)">${exp.id.substring(0, 8)}...</span></td>
          <td>${dateStr} ${timeStr}</td>
          <td>${exp.targetHost}:${exp.targetPort}</td>
          <td><span class="badge ${badgeProto}">${exp.protocol}</span></td>
          <td>${exp.testMode}</td>
          <td>${formatBytes(exp.payloadSize)}</td>
          <td>${exp.packetsSent || 0} / ${exp.packetsReceived || 0}</td>
          <td style="color:var(--blue-primary); font-weight:600;">${tpStr}</td>
          <td style="color:var(--cyan-primary); font-weight:600;">${rttStr}</td>
          <td>${lossStr}</td>
          <td><span class="badge ${badgeStatus}">${exp.status}</span></td>
          <td>
            <button class="btn btn-secondary btn-sm" onclick="window.viewExperimentDetails('${exp.id}')">Details</button>
            <button class="btn btn-secondary btn-sm" onclick="window.selectForComparison('${exp.id}', '${exp.protocol}')">Compare</button>
          </td>
        </tr>
      `;
    }).join('');

    renderPagination(data.total);
  } catch (err) {
    console.error('Failed to load history:', err);
    container.innerHTML = `<tr><td colspan="12" style="color:var(--rose-danger); text-align:center;">Error loading history: ${err.message}</td></tr>`;
  }
}

function renderPagination(total) {
  const paginEl = document.getElementById('historyPagination');
  if (!paginEl) return;
  const totalPages = Math.ceil(total / pageSize);
  if (totalPages <= 1) {
    paginEl.innerHTML = '';
    return;
  }

  let html = `<div style="display:flex; gap:0.4rem; justify-content:flex-end; align-items:center; margin-top:0.75rem;">`;
  html += `<button class="btn btn-secondary btn-sm" ${currentPage === 0 ? 'disabled' : ''} onclick="window.changeHistoryPage(${currentPage - 1})">Prev</button>`;
  html += `<span style="font-size:0.75rem; color:var(--text-muted); font-family:var(--font-mono);">Page ${currentPage + 1} of ${totalPages}</span>`;
  html += `<button class="btn btn-secondary btn-sm" ${currentPage >= totalPages - 1 ? 'disabled' : ''} onclick="window.changeHistoryPage(${currentPage + 1})">Next</button>`;
  html += `</div>`;
  paginEl.innerHTML = html;
}

export function setFilters(protocol, mode, search) {
  currentProtocolFilter = protocol;
  currentModeFilter = mode;
  currentSearch = search;
  currentPage = 0;
  loadHistoryTable();
}

export function setPage(page) {
  currentPage = page;
  loadHistoryTable();
}

export async function openExperimentModal(id) {
  const modal = document.getElementById('experimentDetailModal');
  const body = document.getElementById('modalDetailBody');
  if (!modal || !body) return;

  body.innerHTML = `<div style="text-align:center; padding: 2rem; color: var(--cyan-primary);">Loading experiment details...</div>`;
  modal.classList.add('active');

  try {
    const exp = await fetchTestDetails(id);
    if (!exp) {
      body.innerHTML = `<div style="color:var(--rose-danger);">Experiment not found.</div>`;
      return;
    }

    const tpStr = exp.throughputMbps !== null ? `${exp.throughputMbps.toFixed(3)} Mbps` : 'N/A';
    const rateStr = exp.dataRateMBs !== null ? `${exp.dataRateMBs.toFixed(3)} MB/s` : 'N/A';
    const avgRttStr = exp.avgRttMs !== null ? `${exp.avgRttMs.toFixed(3)} ms` : 'N/A';
    const minRttStr = exp.minRttMs !== null ? `${exp.minRttMs.toFixed(3)} ms` : 'N/A';
    const medianRttStr = exp.medianRttMs !== null ? `${exp.medianRttMs.toFixed(3)} ms` : 'N/A';
    const p95RttStr = exp.p95RttMs !== null ? `${exp.p95RttMs.toFixed(3)} ms` : 'N/A';
    const maxRttStr = exp.maxRttMs !== null ? `${exp.maxRttMs.toFixed(3)} ms` : 'N/A';
    const jitterStr = exp.jitterMs !== null ? `${exp.jitterMs.toFixed(3)} ms` : 'N/A';
    const lossStr = exp.packetLossPct !== null ? `${exp.packetLossPct.toFixed(2)}%` : 'N/A';
    const delivStr = exp.deliveryRatioPct !== null ? `${exp.deliveryRatioPct.toFixed(2)}%` : 'N/A';
    const connSetupStr = exp.connectionSetupTimeMs !== null ? `${exp.connectionSetupTimeMs.toFixed(3)} ms` : 'N/A';
    const utilStr = exp.bandwidthUtilPct !== null ? `${exp.bandwidthUtilPct.toFixed(2)}%` : 'N/A';

    body.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:1rem; flex-wrap:wrap; gap:0.5rem;">
        <div>
          <h3 style="font-size:1.1rem; color:#fff;">${exp.name}</h3>
          <p style="font-size:0.75rem; color:var(--text-muted); font-family:var(--font-mono);">${exp.id}</p>
        </div>
        <div>
          <button class="btn btn-secondary btn-sm" onclick="window.downloadExperimentJson('${exp.id}')">Export JSON</button>
        </div>
      </div>

      <div class="metric-cards-grid" style="margin-bottom:1.25rem;">
        <div class="metric-card">
          <div class="metric-title">Throughput</div>
          <div class="metric-value cyan">${tpStr}</div>
          <div class="metric-sub">${rateStr}</div>
        </div>
        <div class="metric-card">
          <div class="metric-title">Average RTT</div>
          <div class="metric-value blue">${avgRttStr}</div>
          <div class="metric-sub">Min: ${minRttStr} | Max: ${maxRttStr}</div>
        </div>
        <div class="metric-card">
          <div class="metric-title">Packet Loss</div>
          <div class="metric-value ${exp.packetLossPct > 0 ? 'rose' : 'green'}">${lossStr}</div>
          <div class="metric-sub">Delivery: ${delivStr}</div>
        </div>
        <div class="metric-card">
          <div class="metric-title">Jitter Estimate</div>
          <div class="metric-value amber">${jitterStr}</div>
          <div class="metric-sub">RTT Variation</div>
        </div>
      </div>

      <div style="background:var(--bg-surface-elevated); border:1px solid var(--border-subtle); border-radius:var(--radius-sm); padding:1rem; margin-bottom:1rem;">
        <h4 style="font-size:0.8rem; text-transform:uppercase; color:var(--text-secondary); margin-bottom:0.5rem;">Statistical Breakdown</h4>
        <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap:0.75rem; font-family:var(--font-mono); font-size:0.8rem;">
          <div><span style="color:var(--text-muted)">Min RTT:</span> ${minRttStr}</div>
          <div><span style="color:var(--text-muted)">Mean RTT:</span> ${avgRttStr}</div>
          <div><span style="color:var(--text-muted)">Median (P50):</span> ${medianRttStr}</div>
          <div><span style="color:var(--text-muted)">P95 RTT:</span> ${p95RttStr}</div>
          <div><span style="color:var(--text-muted)">Max RTT:</span> ${maxRttStr}</div>
          <div><span style="color:var(--text-muted)">Conn Setup:</span> ${connSetupStr}</div>
          <div><span style="color:var(--text-muted)">Duration:</span> ${exp.durationMs != null ? exp.durationMs.toFixed(1) + ' ms' : 'N/A'}</div>
          <div><span style="color:var(--text-muted)">Link Util:</span> ${utilStr}</div>
        </div>
      </div>

      ${exp.errorMessage ? `<div style="background:rgba(244,63,94,0.1); border:1px solid rgba(244,63,94,0.3); border-radius:var(--radius-sm); padding:0.75rem; color:#fda4af; font-size:0.8rem; margin-bottom:1rem;"><strong>Status Note / Error:</strong> ${exp.errorMessage}</div>` : ''}

      <div style="max-height: 250px; overflow-y: auto; border: 1px solid var(--border-subtle); border-radius: var(--radius-sm);">
        <table class="data-table">
          <thead>
            <tr>
              <th>Seq #</th>
              <th>RTT (ms)</th>
              <th>Instant Throughput (Mbps)</th>
              <th>Timestamp</th>
            </tr>
          </thead>
          <tbody>
            ${(exp.samples && exp.samples.length > 0) ? exp.samples.map(s => `
              <tr>
                <td>#${s.sampleSeq}</td>
                <td>${s.rttMs !== null ? s.rttMs.toFixed(3) : '-'}</td>
                <td>${s.instantThroughputMbps !== null ? s.instantThroughputMbps.toFixed(2) : '-'}</td>
                <td>${new Date(s.timestampMs).toLocaleTimeString()}</td>
              </tr>
            `).join('') : `<tr><td colspan="4" style="text-align:center; color:var(--text-muted);">No individual samples recorded.</td></tr>`}
          </tbody>
        </table>
      </div>
    `;
  } catch (err) {
    body.innerHTML = `<div style="color:var(--rose-danger);">Error: ${err.message}</div>`;
  }
}

export function closeModal() {
  const modal = document.getElementById('experimentDetailModal');
  if (modal) modal.classList.remove('active');
}

export function exportExperimentJson(id) {
  fetchTestDetails(id).then(exp => {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(exp, null, 2));
    const dlAnchorElem = document.createElement('a');
    dlAnchorElem.setAttribute("href", dataStr);
    dlAnchorElem.setAttribute("download", `experiment_${id}.json`);
    dlAnchorElem.click();
  });
}

function formatBytes(bytes) {
  if (bytes === 0) return '0 B';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}
