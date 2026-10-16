/**
 * Chart.js manager for Network Performance Analyzer.
 */

let rttChart = null;
let throughputChart = null;
let comparisonChart = null;
let packetLossChart = null;

const darkGridColor = 'rgba(255, 255, 255, 0.06)';
const textColor = '#94a3b8';

export function initCharts() {
  if (typeof window.Chart === 'undefined') {
    console.warn('Chart.js is not loaded yet.');
    return;
  }

  // Setup RTT Chart
  const rttCtx = document.getElementById('rttChartCanvas');
  if (rttCtx) {
    rttChart = new window.Chart(rttCtx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [{
          label: 'RTT (ms)',
          data: [],
          borderColor: '#00e5ff',
          backgroundColor: 'rgba(0, 229, 255, 0.1)',
          fill: true,
          tension: 0.3,
          borderWidth: 2,
          pointRadius: 2,
          pointHoverRadius: 5
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 300 },
        scales: {
          x: {
            title: { display: true, text: 'Sample Sequence', color: textColor },
            grid: { color: darkGridColor },
            ticks: { color: textColor, maxTicksLimit: 12 }
          },
          y: {
            title: { display: true, text: 'Round-Trip Time (ms)', color: textColor },
            grid: { color: darkGridColor },
            ticks: { color: textColor },
            beginAtZero: true
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#0c1222',
            borderColor: '#00e5ff',
            borderWidth: 1,
            titleColor: '#fff',
            bodyColor: '#94a3b8'
          }
        }
      }
    });
  }

  // Setup Throughput Chart
  const tpCtx = document.getElementById('throughputChartCanvas');
  if (tpCtx) {
    throughputChart = new window.Chart(tpCtx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [{
          label: 'Throughput (Mbps)',
          data: [],
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59, 130, 246, 0.12)',
          fill: true,
          tension: 0.2,
          borderWidth: 2,
          pointRadius: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 300 },
        scales: {
          x: {
            title: { display: true, text: 'Interval / Progress', color: textColor },
            grid: { color: darkGridColor },
            ticks: { color: textColor, maxTicksLimit: 10 }
          },
          y: {
            title: { display: true, text: 'Throughput (Mbps)', color: textColor },
            grid: { color: darkGridColor },
            ticks: { color: textColor },
            beginAtZero: true
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#0c1222',
            borderColor: '#3b82f6',
            borderWidth: 1
          }
        }
      }
    });
  }

  // Setup Protocol Comparison Chart
  const compCtx = document.getElementById('comparisonChartCanvas');
  if (compCtx) {
    comparisonChart = new window.Chart(compCtx, {
      type: 'bar',
      data: {
        labels: ['Throughput (Mbps)', 'Avg RTT (ms)', 'Jitter (ms)'],
        datasets: [
          {
            label: 'TCP',
            data: [0, 0, 0],
            backgroundColor: 'rgba(59, 130, 246, 0.75)',
            borderColor: '#3b82f6',
            borderWidth: 1
          },
          {
            label: 'UDP',
            data: [0, 0, 0],
            backgroundColor: 'rgba(0, 229, 255, 0.75)',
            borderColor: '#00e5ff',
            borderWidth: 1
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: {
            grid: { color: darkGridColor },
            ticks: { color: textColor }
          },
          y: {
            grid: { color: darkGridColor },
            ticks: { color: textColor },
            beginAtZero: true
          }
        },
        plugins: {
          legend: {
            labels: { color: textColor }
          }
        }
      }
    });
  }

  // Setup Packet Loss / Delivery Doughnut
  const lossCtx = document.getElementById('lossChartCanvas');
  if (lossCtx) {
    packetLossChart = new window.Chart(lossCtx, {
      type: 'doughnut',
      data: {
        labels: ['Received Packets', 'Lost Packets'],
        datasets: [{
          data: [100, 0],
          backgroundColor: ['#10b981', '#f43f5e'],
          borderWidth: 2,
          borderColor: '#0c1222'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: { color: textColor }
          }
        },
        cutout: '70%'
      }
    });
  }
}

export function updateRttChart(samples) {
  if (!rttChart || !samples) return;
  const labels = [];
  const data = [];
  samples.forEach((s) => {
    if (s.rttMs !== null && s.rttMs !== undefined) {
      labels.push(`#${s.sampleSeq}`);
      data.push(s.rttMs);
    }
  });
  rttChart.data.labels = labels;
  rttChart.data.datasets[0].data = data;
  rttChart.update();
}

export function updateThroughputChart(samples) {
  if (!throughputChart || !samples) return;
  const labels = [];
  const data = [];
  samples.forEach((s) => {
    if (s.instantThroughputMbps !== null && s.instantThroughputMbps !== undefined) {
      labels.push(`#${s.sampleSeq}`);
      data.push(s.instantThroughputMbps);
    }
  });
  throughputChart.data.labels = labels;
  throughputChart.data.datasets[0].data = data;
  throughputChart.update();
}

export function updateComparisonChart(comparison) {
  if (!comparisonChart || !comparison) return;
  const tcp = comparison.tcpExperiment || {};
  const udp = comparison.udpExperiment || {};

  comparisonChart.data.datasets[0].data = [
    tcp.throughputMbps || 0,
    tcp.avgRttMs || 0,
    tcp.jitterMs || 0
  ];
  comparisonChart.data.datasets[1].data = [
    udp.throughputMbps || 0,
    udp.avgRttMs || 0,
    udp.jitterMs || 0
  ];
  comparisonChart.update();
}

export function updatePacketLossChart(sent, received, lossPct) {
  if (!packetLossChart) return;
  const recv = received || 0;
  const lost = Math.max(0, (sent || 0) - recv);
  packetLossChart.data.datasets[0].data = [recv, lost];
  packetLossChart.update();
}

export function resetCharts() {
  if (rttChart) {
    rttChart.data.labels = [];
    rttChart.data.datasets[0].data = [];
    rttChart.update();
  }
  if (throughputChart) {
    throughputChart.data.labels = [];
    throughputChart.data.datasets[0].data = [];
    throughputChart.update();
  }
}
