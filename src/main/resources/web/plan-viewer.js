(() => {
  const state = window.__SQL_ANALYZER_STATE__ || {};
  let query = '';
  let selectedTitle = state.plan?.root?.title || null;

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatNumber(value) {
    if (value === null || value === undefined || value === '') return '—';
    if (typeof value === 'number') {
      return Number.isInteger(value) ? value.toLocaleString() : value.toFixed(2).replace(/\.00$/, '');
    }
    return String(value);
  }

  function metric(label, value, hint = '') {
    return `
      <div class="summary-card">
        <div class="label">${escapeHtml(label)}</div>
        <div class="value">${escapeHtml(value ?? '—')}</div>
        ${hint ? `<div class="hint">${escapeHtml(hint)}</div>` : ''}
      </div>`;
  }

  function getSearchBlob(node) {
    const extraValues = Object.entries(node.extras || {}).map(([k, v]) => `${k} ${v}`).join(' ');
    return `${node.title} ${node.nodeType} ${node.parentRelationship || ''} ${extraValues}`.toLowerCase();
  }

  function collectMatches(node, term, matches) {
    const selfMatches = !term || getSearchBlob(node).includes(term);
    const childMatches = (node.children || []).some(child => collectMatches(child, term, matches));
    if (selfMatches) {
      matches.add(node.title);
    }
    if (childMatches && term) {
      matches.add(node.title);
    }
    return selfMatches || childMatches;
  }

  function findNode(node, title) {
    if (!node || !title) return node;
    if (node.title === title) return node;
    for (const child of node.children || []) {
      const match = findNode(child, title);
      if (match) return match;
    }
    return node;
  }

  function renderNode(node, matches, depth) {
    const selected = selectedTitle === node.title;
    const matched = !query || matches.has(node.title);
    const extras = Object.entries(node.extras || {})
      .map(([key, value]) => `<div class="extra"><strong>${escapeHtml(key)}</strong><br/>${escapeHtml(value)}</div>`)
      .join('');
    const telemetry = [
      ['Parallel aware', node.parallelAware],
      ['Parent relationship', node.parentRelationship],
      ['Workers', node.workersPlanned || node.workersLaunched ? `${formatNumber(node.workersPlanned)} / ${formatNumber(node.workersLaunched)}` : null],
      ['Shared blocks', node.sharedHitBlocks || node.sharedReadBlocks ? `${formatNumber(node.sharedHitBlocks)} / ${formatNumber(node.sharedReadBlocks)}` : null],
      ['Temp blocks', node.tempReadBlocks || node.tempWrittenBlocks ? `${formatNumber(node.tempReadBlocks)} / ${formatNumber(node.tempWrittenBlocks)}` : null],
      ['I/O time', node.ioReadTime || node.ioWriteTime ? `${formatNumber(node.ioReadTime)} / ${formatNumber(node.ioWriteTime)}` : null],
    ].filter(([, value]) => value !== null && value !== undefined && value !== '');
    const telemetryHtml = telemetry.map(([label, value]) => `
      <div class="metric"><div class="label">${escapeHtml(label)}</div><div class="metric-value">${escapeHtml(value)}</div></div>`).join('');
    const children = (node.children || []).map(child => renderNode(child, matches, depth + 1)).join('');

    return `
      <section class="plan-card ${matched ? 'matched' : 'dimmed'}" style="margin-left:${depth * 8}px">
        <div class="node-top">
          <button class="node-select" data-node-title="${escapeHtml(node.title)}">
            <div class="node-title">${selected ? '● ' : ''}${escapeHtml(node.title)}</div>
          </button>
          <div class="node-chip">${escapeHtml(node.nodeType)}</div>
        </div>
        <div class="metrics">
          <div class="metric"><div class="label">Estimated cost</div><div class="metric-value">${formatNumber(node.startupCost)} → ${formatNumber(node.totalCost)}</div></div>
          <div class="metric"><div class="label">Estimated rows</div><div class="metric-value">${formatNumber(node.planRows)}</div></div>
          <div class="metric"><div class="label">Actual time</div><div class="metric-value">${formatNumber(node.actualStartupTime)} → ${formatNumber(node.actualTotalTime)}</div></div>
          <div class="metric"><div class="label">Actual rows / loops</div><div class="metric-value">${formatNumber(node.actualRows)} / ${formatNumber(node.actualLoops)}</div></div>
          ${telemetryHtml}
        </div>
        ${extras ? `<div class="extras">${extras}</div>` : ''}
        ${children ? `<div class="children">${children}</div>` : ''}
      </section>`;
  }

  function renderDetails(node) {
    const rows = [
      ['Node', node.title],
      ['Estimated cost', `${formatNumber(node.startupCost)} → ${formatNumber(node.totalCost)}`],
      ['Estimated rows', formatNumber(node.planRows)],
      ['Estimated width', formatNumber(node.planWidth)],
      ['Actual time', `${formatNumber(node.actualStartupTime)} → ${formatNumber(node.actualTotalTime)}`],
      ['Actual rows', formatNumber(node.actualRows)],
      ['Actual loops', formatNumber(node.actualLoops)],
      ['Parallel aware', node.parallelAware],
      ['Parent relationship', node.parentRelationship],
      ['Workers planned', formatNumber(node.workersPlanned)],
      ['Workers launched', formatNumber(node.workersLaunched)],
      ['Shared hit blocks', formatNumber(node.sharedHitBlocks)],
      ['Shared read blocks', formatNumber(node.sharedReadBlocks)],
      ['Temp read blocks', formatNumber(node.tempReadBlocks)],
      ['Temp written blocks', formatNumber(node.tempWrittenBlocks)],
      ['I/O read time', formatNumber(node.ioReadTime)],
      ['I/O write time', formatNumber(node.ioWriteTime)],
    ].filter(([, value]) => value !== null && value !== undefined && value !== '—');

    const extras = Object.entries(node.extras || {})
      .map(([key, value]) => `<div class="detail-row"><div class="label">${escapeHtml(key)}</div><div>${escapeHtml(value)}</div></div>`)
      .join('');

    return `
      <section class="details-card">
        <div class="eyebrow">Node details</div>
        <h2>${escapeHtml(node.title)}</h2>
        <div class="details-list">
          ${rows.map(([label, value]) => `<div class="detail-row"><div class="label">${escapeHtml(label)}</div><div>${escapeHtml(value)}</div></div>`).join('')}
          ${extras}
        </div>
      </section>`;
  }

  function render() {
    const app = document.getElementById('app');
    if (state.isLoading) {
      app.innerHTML = `<div class="page"><div class="placeholder loading">Running EXPLAIN for the selected SQL through the active DataGrip connection…</div></div>`;
      return;
    }
    if (state.errorMessage) {
      app.innerHTML = `<div class="page"><div class="error">${escapeHtml(state.errorMessage)}</div></div>`;
      return;
    }
    if (!state.plan) {
      app.innerHTML = '<div class="page"><div class="placeholder">No plan has been loaded yet.</div></div>';
      return;
    }

    const matches = new Set();
    const searchTerm = query.trim().toLowerCase();
    collectMatches(state.plan.root, searchTerm, matches);
    if (!selectedTitle || !matches.has(selectedTitle)) {
      selectedTitle = state.plan.root.title;
    }
    const selectedNode = findNode(state.plan.root, selectedTitle);
    const settingsHtml = Object.entries(state.plan.settings || {})
      .map(([key, value]) => `<div class="setting"><div class="label">${escapeHtml(key)}</div><div>${escapeHtml(value)}</div></div>`)
      .join('');

    app.innerHTML = `
      <div class="page">
        <section class="hero">
          <div class="eyebrow">DataGrip × pev2-inspired viewer</div>
          <h1>${escapeHtml(state.plan.root.title)}</h1>
          <pre class="sql">${escapeHtml(state.sqlText || 'No SQL captured from editor yet.')}</pre>
          <div class="hint" style="margin-top:12px;">Generated command</div>
          <pre class="explain">${escapeHtml(state.explainSql || 'EXPLAIN (FORMAT JSON) …')}</pre>
        </section>
        <section class="summary">
          ${metric('Planning', formatNumber(state.plan.planningTimeMs), 'ms')}
          ${metric('Execution', formatNumber(state.plan.executionTimeMs), 'ms')}
          ${metric('Rows', formatNumber(state.plan.root.planRows), 'estimated')}
          ${metric('Width', formatNumber(state.plan.root.planWidth), 'bytes')}
        </section>
        <section class="search-card">
          <div class="eyebrow">Search & inspect</div>
          <input class="search-input" id="searchInput" placeholder="Search nodes, relations, filters, hash cond, group key..." value="${escapeHtml(query)}" />
          <div class="search-meta">${escapeHtml(matches.size)} matching nodes</div>
        </section>
        <section class="layout">
          <div class="plan-tree">
            ${renderNode(state.plan.root, matches, 0)}
          </div>
          <div>
            <section class="settings-card">
              <div class="eyebrow">Session settings</div>
              ${settingsHtml ? `<div class="settings-list">${settingsHtml}</div>` : '<div class="hint">No PostgreSQL settings were emitted in this plan.</div>'}
            </section>
            ${renderDetails(selectedNode)}
          </div>
        </section>
      </div>`;

    document.getElementById('searchInput')?.addEventListener('input', (event) => {
      query = event.target.value;
      render();
    });
    document.querySelectorAll('[data-node-title]').forEach((element) => {
      element.addEventListener('click', () => {
        selectedTitle = element.getAttribute('data-node-title');
        render();
      });
    });
  }

  render();
})();
