/**
 * API client module for Network Performance Analyzer REST endpoints.
 */

const BASE_URL = '';

export async function fetchServerStatus() {
  const res = await fetch(`${BASE_URL}/api/server/status`);
  if (!res.ok) throw new Error(`Failed to fetch server status: ${res.statusText}`);
  return await res.json();
}

export async function startServer() {
  const res = await fetch(`${BASE_URL}/api/server/start`, { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to start server: ${res.statusText}`);
  return await res.json();
}

export async function stopServer() {
  const res = await fetch(`${BASE_URL}/api/server/stop`, { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to stop server: ${res.statusText}`);
  return await res.json();
}

export async function startTest(testConfig) {
  const res = await fetch(`${BASE_URL}/api/tests`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(testConfig)
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.message || `Failed to start test: ${res.statusText}`);
  }
  return data;
}

export async function fetchActiveTest() {
  const res = await fetch(`${BASE_URL}/api/tests`);
  if (!res.ok) return null;
  return await res.json();
}

export async function fetchTestDetails(id) {
  const res = await fetch(`${BASE_URL}/api/tests/${encodeURIComponent(id)}`);
  if (!res.ok) throw new Error(`Failed to load experiment ${id}`);
  return await res.json();
}

export async function cancelTest(id) {
  const res = await fetch(`${BASE_URL}/api/tests/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
  return await res.json();
}

export async function fetchSamples(id) {
  const res = await fetch(`${BASE_URL}/api/tests/${encodeURIComponent(id)}/samples`);
  if (!res.ok) return [];
  return await res.json();
}

export async function fetchComparison(tcpId, udpId) {
  const res = await fetch(`${BASE_URL}/api/comparisons?tcpId=${encodeURIComponent(tcpId)}&udpId=${encodeURIComponent(udpId)}`);
  if (!res.ok) {
    const err = await res.json();
    throw new Error(err.message || 'Comparison failed');
  }
  return await res.json();
}

export async function fetchHistory(params = {}) {
  const query = new URLSearchParams(params).toString();
  const res = await fetch(`${BASE_URL}/api/history?${query}`);
  if (!res.ok) throw new Error(`Failed to fetch history`);
  return await res.json();
}

export async function fetchMetricsSummary() {
  const res = await fetch(`${BASE_URL}/api/metrics/summary`);
  if (!res.ok) return null;
  return await res.json();
}

export async function fetchDiagnostics() {
  const res = await fetch(`${BASE_URL}/api/diagnostics`);
  if (!res.ok) return null;
  return await res.json();
}
