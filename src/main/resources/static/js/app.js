/**
 * Main application bootstrapper connecting tabs, history, and dashboard.
 */

import { initDashboard, populateComparisonDropdowns, loadDiagnosticsTab } from './dashboard.js';
import { setFilters, setPage, openExperimentModal, closeModal, exportExperimentJson } from './history.js';

document.addEventListener('DOMContentLoaded', () => {
  // Setup tab navigation
  document.querySelectorAll('.nav-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

      tab.classList.add('active');
      const targetId = tab.getAttribute('data-tab');
      const pane = document.getElementById(targetId);
      if (pane) pane.classList.add('active');

      if (targetId === 'tab-comparison') {
        populateComparisonDropdowns();
      } else if (targetId === 'tab-diagnostics') {
        loadDiagnosticsTab();
      }
    });
  });

  // Setup history filter triggers
  const protoFilter = document.getElementById('historyProtocolFilter');
  const modeFilter = document.getElementById('historyModeFilter');
  const searchFilter = document.getElementById('historySearchInput');

  function triggerFilters() {
    setFilters(
      protoFilter ? protoFilter.value : 'ALL',
      modeFilter ? modeFilter.value : 'ALL',
      searchFilter ? searchFilter.value.trim() : ''
    );
  }

  if (protoFilter) protoFilter.addEventListener('change', triggerFilters);
  if (modeFilter) modeFilter.addEventListener('change', triggerFilters);
  if (searchFilter) searchFilter.addEventListener('input', debounce(triggerFilters, 300));

  // Modal backdrop click to close
  const modal = document.getElementById('experimentDetailModal');
  if (modal) {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) closeModal();
    });
  }

  // Initialize dashboard
  initDashboard();
});

// Helper globals
window.viewExperimentDetails = (id) => {
  openExperimentModal(id);
};

window.closeExperimentModal = () => {
  closeModal();
};

window.changeHistoryPage = (page) => {
  setPage(page);
};

window.downloadExperimentJson = (id) => {
  exportExperimentJson(id);
};

window.selectForComparison = (id, protocol) => {
  // Switch to comparison tab
  const compTab = document.querySelector('[data-tab="tab-comparison"]');
  if (compTab) compTab.click();

  setTimeout(() => {
    if (protocol === 'TCP') {
      const tcpSelect = document.getElementById('compareTcpSelect');
      if (tcpSelect) tcpSelect.value = id;
    } else if (protocol === 'UDP') {
      const udpSelect = document.getElementById('compareUdpSelect');
      if (udpSelect) udpSelect.value = id;
    }
  }, 200);
};

function debounce(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}
