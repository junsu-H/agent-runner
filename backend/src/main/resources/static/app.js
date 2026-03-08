import React, { useEffect, useState } from 'https://esm.sh/react@18.3.1';
import { createRoot } from 'https://esm.sh/react-dom@18.3.1/client';
import htm from 'https://esm.sh/htm@3.1.1';

const html = htm.bind(React.createElement);

function App() {
  const [projectPath, setProjectPath] = useState('~/workspace/agent-runner');
  const [issueKey, setIssueKey] = useState('UNICORN-00000');
  const [workflowDef, setWorkflowDef] = useState(null);
  const [loadingDef, setLoadingDef] = useState(true);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const loadDefinition = async () => {
      setLoadingDef(true);
      try {
        const res = await fetch('/api/workflows/linked-list/definition');
        if (!res.ok) {
          throw new Error(`definition fetch failed: ${res.status}`);
        }
        const data = await res.json();
        setWorkflowDef(data);
      } catch (e) {
        setError(e.message || '워크플로우 정의를 가져오지 못했습니다.');
      } finally {
        setLoadingDef(false);
      }
    };

    loadDefinition();
  }, []);

  const runWorkflow = async (event) => {
    event.preventDefault();
    setRunning(true);
    setError('');
    setResult(null);

    try {
      const res = await fetch('/api/workflows/linked-list/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          projectPath,
          issueKey: issueKey.trim() ? issueKey.trim() : null,
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || `요청 실패(${res.status})`);
      }

      setResult(data);
    } catch (e) {
      setError(e.message || '워크플로우 실행 중 오류가 발생했습니다.');
    } finally {
      setRunning(false);
    }
  };

  const statusClass = result?.success ? 'status-chip status-ok' : 'status-chip status-fail';

  return html`
    <main className="page">
      <section className="header">
        <h1>Agent Runner Linked-List Workflow</h1>
        <p>
          Spring 서버에서 <code>domain-analsis.sh → pr-review.sh</code> 순서로 실행합니다.
        </p>
      </section>

      <section className="card">
        <form onSubmit=${runWorkflow}>
          <div className="grid">
            <div className="field full">
              <label>Project Path</label>
              <input
                value=${projectPath}
                onChange=${(e) => setProjectPath(e.target.value)}
                placeholder="/Users/miri/workspace/your-project"
                required
              />
            </div>
            <div className="field">
              <label>Issue Key</label>
              <input
                value=${issueKey}
                onChange=${(e) => setIssueKey(e.target.value)}
                placeholder="UNICORN-12345"
              />
            </div>
            <div className="field">
              <label>Workflow Type</label>
              <input value="linked-list" disabled />
            </div>
          </div>

          <div className="btn-row">
            <button type="submit" disabled=${running || loadingDef}>
              ${running ? '실행 중...' : '워크플로우 실행'}
            </button>
            ${result
              ? html`<span className=${statusClass}>${result.success ? 'SUCCESS' : 'FAILED'}</span>`
              : null}
          </div>
        </form>
      </section>

      <section className="card">
        <h3>Step Definition</h3>
        ${loadingDef
          ? html`<p className="note">로딩 중...</p>`
          : workflowDef
          ? html`
              <p className="note">
                ${workflowDef.name} (${workflowDef.executionMode})
              </p>
              <ul>
                ${workflowDef.steps?.map(
                  (step) => html`<li key=${step}>${step}</li>`
                )}
              </ul>
            `
          : html`<p className="note">정의 정보를 불러오지 못했습니다.</p>`}
      </section>

      ${error ? html`<section className="error">${error}</section>` : null}

      ${result
        ? html`
            <section className="card">
              <h3>Run Result</h3>
              <p className="note mono">runDirectory: ${result.runDirectory}</p>
              <table className="table">
                <thead>
                  <tr>
                    <th>Step</th>
                    <th>Command</th>
                    <th>Exit</th>
                    <th>Success</th>
                    <th>Log</th>
                  </tr>
                </thead>
                <tbody>
                  ${result.steps?.map(
                    (step) => html`
                      <tr key=${step.step}>
                        <td>${step.step}</td>
                        <td className="mono">${step.command}</td>
                        <td>${step.exitCode}</td>
                        <td>${String(step.success)}</td>
                        <td className="mono">${step.logFile}</td>
                      </tr>
                    `
                  )}
                </tbody>
              </table>
            </section>
          `
        : null}
    </main>
  `;
}

createRoot(document.getElementById('root')).render(html`<${App} />`);
