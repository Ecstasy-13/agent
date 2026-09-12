(() => {
    'use strict';

    const ENDPOINTS = {
        chat: '/api/v2/agent/chat',
        clearConversation: (userId, conversationId) =>
            `/api/v2/agent/users/${encodeURIComponent(userId)}/conversations/${encodeURIComponent(conversationId)}`,
        memory: (userId) => `/agent/memory/${encodeURIComponent(userId)}`,
        documents: '/agent/knowledge/documents',
        knowledgeUpload: '/agent/knowledge/upload',
        knowledgeAsk: '/agent/knowledge/ask',
        toolList: '/agent/tool/list',
        toolCall: '/agent/tool/call',
        mcpConnect: '/agent/mcp/connect',
        mcpServers: '/agent/mcp/servers',
        mcpTools: '/agent/mcp/tools',
        mcpCall: '/agent/mcp/call',
        health: '/actuator/health',
    };

    const MODE_META = {
        chat: { label: 'V2', title: 'Agent V2', description: 'POST /api/v2/agent/chat · conversation + runtime trace' },
        rag: { label: 'RAG', title: 'RAG', description: 'Retrieval + answer sources' },
        tool: { label: 'TOOL', title: 'Tool', description: 'Function calling + execution steps' },
    };

    const initialUserId = localStorage.getItem('agent.runtime.userId') || '001';

    const state = {
        view: 'runtime',
        mode: localStorage.getItem('agent.runtime.mode') || 'chat',
        userId: initialUserId,
        conversationId: localStorage.getItem(`agent.runtime.conversationId.${initialUserId}`) || null,
        messages: [],
        memory: [],
        documents: [],
        tools: [],
        mcpServers: [],
        mcpTools: [],
        activities: [],
        busy: false,
        lastRunDuration: null,
        lastRunId: null,
        lastUsage: null,
    };

    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

    const els = {
        pageTitle: $('#pageTitle'),
        userId: $('#userId'),
        newSession: $('#newSession'),
        healthPill: $('#healthPill'),
        healthText: $('#healthText'),
        refreshAll: $('#refreshAll'),
        inspectorToggle: $('#inspectorToggle'),
        inspector: $('#inspector'),
        closeInspector: $('#closeInspector'),
        messages: $('#messages'),
        welcome: $('#welcome'),
        chatForm: $('#chatForm'),
        messageInput: $('#messageInput'),
        sendBtn: $('#sendBtn'),
        modeBadge: $('#modeBadge'),
        modeDescription: $('#modeDescription'),
        runStateBadge: $('#runStateBadge'),
        inspectorMode: $('#inspectorMode'),
        inspectorUser: $('#inspectorUser'),
        inspectorConversation: $('#inspectorConversation'),
        inspectorRunId: $('#inspectorRunId'),
        inspectorTokens: $('#inspectorTokens'),
        lastRunDuration: $('#lastRunDuration'),
        refreshCapabilities: $('#refreshCapabilities'),
        toolCount: $('#toolCount'),
        inspectorDocCount: $('#inspectorDocCount'),
        mcpCount: $('#mcpCount'),
        memoryList: $('#memoryList'),
        refreshMemory: $('#refreshMemory'),
        clearMemory: $('#clearMemory'),
        activityList: $('#activityList'),
        clearActivity: $('#clearActivity'),
        openRagRuntime: $('#openRagRuntime'),
        openToolRuntime: $('#openToolRuntime'),
        docName: $('#docName'),
        docContent: $('#docContent'),
        uploadDoc: $('#uploadDoc'),
        refreshDocs: $('#refreshDocs'),
        docList: $('#docList'),
        docCount: $('#docCount'),
        chunkCount: $('#chunkCount'),
        refreshTools: $('#refreshTools'),
        toolList: $('#toolList'),
        refreshMcp: $('#refreshMcp'),
        mcpName: $('#mcpName'),
        mcpEndpoint: $('#mcpEndpoint'),
        mcpConnect: $('#mcpConnect'),
        mcpServer: $('#mcpServer'),
        mcpTool: $('#mcpTool'),
        mcpArgs: $('#mcpArgs'),
        mcpCall: $('#mcpCall'),
        mcpServers: $('#mcpServers'),
        mcpResult: $('#mcpResult'),
        toastHost: $('#toastHost'),
    };

    class ApiError extends Error {
        constructor(message, status = 0, payload = null) {
            super(message);
            this.name = 'ApiError';
            this.status = status;
            this.payload = payload;
        }
    }

    async function request(path, options = {}) {
        const init = { ...options };
        init.headers = { ...(options.headers || {}) };
        if (init.body && !init.headers['Content-Type']) {
            init.headers['Content-Type'] = 'application/json';
        }

        const response = await fetch(path, init);
        const contentType = response.headers.get('content-type') || '';
        let payload = null;

        if (response.status !== 204) {
            if (contentType.includes('application/json')) {
                payload = await response.json();
            } else {
                const text = await response.text();
                payload = text ? { message: text } : null;
            }
        }

        if (!response.ok) {
            const message = payload?.message || `HTTP ${response.status}`;
            throw new ApiError(message, response.status, payload);
        }

        // Legacy /agent/* contract: { code, message, data }
        if (payload && Object.prototype.hasOwnProperty.call(payload, 'code')) {
            if (payload.code !== 200) {
                throw new ApiError(payload.message || 'Request failed', response.status, payload);
            }
            return payload.data;
        }

        // Actuator and future runtime endpoints can return native JSON directly.
        return payload;
    }

    function post(path, body) {
        return request(path, { method: 'POST', body: JSON.stringify(body) });
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function valueToText(value) {
        if (value === null || value === undefined) return '—';
        if (typeof value === 'string') return value;
        try {
            return JSON.stringify(value, null, 2);
        } catch (_) {
            return String(value);
        }
    }

    function currentUserId() {
        return els.userId.value.trim();
    }

    function runId() {
        return `run_${Date.now().toString(36)}`;
    }

    function formatDuration(ms) {
        if (ms === null || ms === undefined) return '—';
        if (ms < 1000) return `${Math.round(ms)} ms`;
        return `${(ms / 1000).toFixed(ms < 10000 ? 2 : 1)} s`;
    }

    function nowLabel() {
        return new Intl.DateTimeFormat('zh-CN', {
            hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
        }).format(new Date());
    }

    function toast(message, type = 'success') {
        const node = document.createElement('div');
        node.className = `toast ${type}`;
        node.textContent = message;
        els.toastHost.appendChild(node);
        window.setTimeout(() => node.remove(), 3200);
    }

    function addActivity(title, meta, status = 'success') {
        state.activities.unshift({ title, meta, status, time: nowLabel() });
        state.activities = state.activities.slice(0, 12);
        renderActivity();
    }

    function renderActivity() {
        if (!state.activities.length) {
            els.activityList.innerHTML = '<div class="empty-state">No client activity yet.</div>';
            return;
        }
        els.activityList.innerHTML = state.activities.map((item) => `
            <div class="activity-item ${escapeHtml(item.status)}">
                <span class="activity-mark"></span>
                <div>
                    <div class="activity-title">${escapeHtml(item.title)}</div>
                    <div class="activity-meta">${escapeHtml(item.time)} · ${escapeHtml(item.meta)}</div>
                </div>
            </div>
        `).join('');
    }

    function setRunState(status) {
        const label = status === 'running' ? 'RUNNING' : status === 'done' ? 'DONE' : status === 'error' ? 'ERROR' : 'IDLE';
        els.runStateBadge.className = `state-badge ${status}`;
        els.runStateBadge.textContent = label;
    }

    function setBusy(busy) {
        state.busy = busy;
        els.messageInput.disabled = busy;
        els.sendBtn.disabled = busy;
        els.sendBtn.querySelector('span').textContent = busy ? 'Running' : 'Run';
        setRunState(busy ? 'running' : 'idle');
    }

    function updateRuntimeMeta() {
        const meta = MODE_META[state.mode];
        els.modeBadge.textContent = meta.label;
        els.modeDescription.textContent = meta.description;
        els.inspectorMode.textContent = meta.title;
        els.inspectorUser.textContent = state.userId || '—';
        els.inspectorConversation.textContent = state.conversationId || '—';
        els.inspectorRunId.textContent = state.lastRunId || '—';
        els.inspectorTokens.textContent = state.lastUsage?.totalTokens ?? '—';
        els.lastRunDuration.textContent = formatDuration(state.lastRunDuration);
        $$('.mode-button').forEach((button) => {
            button.classList.toggle('active', button.dataset.mode === state.mode);
        });
    }

    function setMode(mode) {
        if (!MODE_META[mode]) return;
        state.mode = mode;
        localStorage.setItem('agent.runtime.mode', mode);
        updateRuntimeMeta();
        els.messageInput.placeholder = mode === 'rag'
            ? 'Ask a question grounded in the knowledge base…'
            : mode === 'tool'
                ? 'Describe a task that may require a tool…'
                : 'Send a message to the runtime…';
        els.messageInput.focus();
    }

    function switchView(view) {
        state.view = view;
        const titles = { runtime: 'Runtime Console', knowledge: 'Knowledge Base', capabilities: 'Capabilities' };
        els.pageTitle.textContent = titles[view] || 'Agent Runtime';
        $$('.nav-item').forEach((button) => button.classList.toggle('active', button.dataset.view === view));
        $$('.view').forEach((node) => { node.hidden = node.id !== `view-${view}`; });
        if (window.innerWidth <= 960) els.inspector.classList.remove('open');
    }

    function renderMessages() {
        const welcomeTemplate = els.welcome;
        els.messages.innerHTML = '';

        if (!state.messages.length && welcomeTemplate) {
            els.messages.appendChild(welcomeTemplate);
            bindPromptSuggestions();
            return;
        }

        state.messages.forEach((item) => {
            const node = document.createElement('article');
            node.className = `message ${item.role}${item.error ? ' error' : ''}`;
            const isUser = item.role === 'user';
            const modeLabel = item.mode ? MODE_META[item.mode]?.label : null;
            const traceCount = item.trace?.length || 0;

            let traceHtml = '';
            if (traceCount) {
                traceHtml = `
                    <details class="trace">
                        <summary>${item.mode === 'rag' ? 'Retrieval sources' : 'Execution trace'} · ${traceCount}</summary>
                        <div class="trace-body">
                            ${item.trace.map((step) => `
                                <div class="trace-item">
                                    <div class="trace-title">${escapeHtml(step.title)}</div>
                                    ${step.args !== undefined ? `<div class="trace-label">${escapeHtml(step.argsLabel || 'Input')}</div><div class="trace-value">${escapeHtml(valueToText(step.args))}</div>` : ''}
                                    ${step.result !== undefined ? `<div class="trace-label">${escapeHtml(step.resultLabel || 'Result')}</div><div class="trace-value">${escapeHtml(valueToText(step.result))}</div>` : ''}
                                </div>
                            `).join('')}
                        </div>
                    </details>`;
            }

            node.innerHTML = `
                <div class="message-avatar">${isUser ? 'YOU' : 'AI'}</div>
                <div class="message-main">
                    <div class="message-head">
                        <span class="message-role">${isUser ? 'You' : 'Agent'}</span>
                        ${modeLabel && !isUser ? `<span class="run-chip">${escapeHtml(modeLabel)}</span>` : ''}
                        ${item.duration !== undefined ? `<span class="message-meta">${escapeHtml(formatDuration(item.duration))}</span>` : ''}
                        ${item.usage?.totalTokens != null ? `<span class="message-meta">${escapeHtml(String(item.usage.totalTokens))} tokens</span>` : ''}
                        ${item.runId ? `<span class="message-meta">${escapeHtml(item.runId)}</span>` : ''}
                    </div>
                    <div class="message-body">${escapeHtml(item.content)}</div>
                    ${traceHtml}
                </div>`;
            els.messages.appendChild(node);
        });
        els.messages.scrollTop = els.messages.scrollHeight;
    }

    function showTyping() {
        const node = document.createElement('article');
        node.id = 'typingIndicator';
        node.className = 'message assistant';
        node.innerHTML = `
            <div class="message-avatar">AI</div>
            <div class="message-main">
                <div class="message-head"><span class="message-role">Agent</span><span class="run-chip">${escapeHtml(MODE_META[state.mode].label)}</span></div>
                <div class="typing-dots"><span></span><span></span><span></span></div>
            </div>`;
        els.messages.appendChild(node);
        els.messages.scrollTop = els.messages.scrollHeight;
    }

    function hideTyping() {
        $('#typingIndicator')?.remove();
    }

    function bindPromptSuggestions() {
        $$('[data-prompt]', els.messages).forEach((button) => {
            button.addEventListener('click', () => {
                const targetMode = button.dataset.modeTarget;
                if (targetMode) setMode(targetMode);
                els.messageInput.value = button.dataset.prompt || '';
                autoResizeComposer();
                els.messageInput.focus();
            });
        });
    }

    async function executeRun(text) {
        const mode = state.mode;
        if (mode === 'chat') {
            const data = await post(ENDPOINTS.chat, {
                userId: state.userId,
                conversationId: state.conversationId || null,
                message: text,
            });

            if (data?.conversationId) {
                state.conversationId = data.conversationId;
                localStorage.setItem(`agent.runtime.conversationId.${state.userId}`, data.conversationId);
            }

            state.lastRunId = data?.runId || null;
            state.lastUsage = data?.usage || null;

            const trace = (data?.steps || []).map((step) => ({
                title: `${step.index ?? '·'} · ${step.type || step.name || 'STEP'}${step.durationMs != null ? ` · ${formatDuration(step.durationMs)}` : ''}`,
                argsLabel: 'Name',
                args: step.name || '—',
                resultLabel: 'Attributes',
                result: step.attributes || {},
            }));

            return {
                answer: data?.answer || '',
                trace,
                runId: data?.runId || null,
                duration: data?.totalDurationMs,
                usage: data?.usage || null,
            };
        }
        if (mode === 'rag') {
            const data = await post(ENDPOINTS.knowledgeAsk, { question: text });
            const trace = (data?.sources || []).map((source) => ({
                title: source.document || 'Knowledge source',
                argsLabel: 'Similarity',
                args: source.score,
                resultLabel: 'Chunk',
                result: source.text,
            }));
            return { answer: data?.answer || '', trace };
        }
        if (mode === 'tool') {
            const data = await post(ENDPOINTS.toolCall, { message: text });
            const trace = (data?.steps || []).map((step) => ({
                title: step.tool || step.name || 'Tool call',
                argsLabel: 'Arguments',
                args: step.arguments ?? step.attributes ?? {},
                resultLabel: 'Result',
                result: step.result ?? step.attributes ?? {},
            }));
            return { answer: data?.answer || '', trace };
        }
        throw new Error(`Unsupported mode: ${mode}`);
    }

    async function sendMessage() {
        if (state.busy) return;
        const userId = currentUserId();
        const text = els.messageInput.value.trim();
        if (!userId) {
            toast('请先填写 User ID', 'error');
            els.userId.focus();
            return;
        }
        if (!text) return;

        state.userId = userId;
        localStorage.setItem('agent.runtime.userId', userId);
        updateRuntimeMeta();

        const activeMode = state.mode;
        const fallbackRunId = runId();
        state.messages.push({ role: 'user', content: text });
        els.messageInput.value = '';
        autoResizeComposer();
        renderMessages();
        setBusy(true);
        showTyping();

        const startedAt = performance.now();
        try {
            const result = await executeRun(text);
            const clientDuration = performance.now() - startedAt;
            const duration = result.duration ?? clientDuration;
            const effectiveRunId = result.runId || fallbackRunId;
            state.lastRunDuration = duration;
            state.lastRunId = effectiveRunId;
            state.lastUsage = result.usage || state.lastUsage;
            state.messages.push({
                role: 'assistant',
                content: result.answer || '(empty response)',
                mode: activeMode,
                trace: result.trace,
                duration,
                runId: effectiveRunId,
                usage: result.usage || null,
            });
            setRunState('done');
            addActivity(`${MODE_META[activeMode].title} run completed`, `${effectiveRunId} · ${formatDuration(duration)}`, 'success');
            if (activeMode === 'chat') window.setTimeout(loadMemory, 500);
        } catch (error) {
            const duration = performance.now() - startedAt;
            state.lastRunDuration = duration;
            state.messages.push({
                role: 'assistant', content: error.message, mode: activeMode, duration, runId: fallbackRunId, error: true,
            });
            state.lastRunId = fallbackRunId;
            setRunState('error');
            addActivity(`${MODE_META[activeMode].title} run failed`, `${fallbackRunId} · ${error.message}`, 'error');
        } finally {
            hideTyping();
            renderMessages();
            state.busy = false;
            els.messageInput.disabled = false;
            els.sendBtn.disabled = false;
            els.sendBtn.querySelector('span').textContent = 'Run';
            updateRuntimeMeta();
            els.messageInput.focus();
        }
    }

    function autoResizeComposer() {
        els.messageInput.style.height = 'auto';
        els.messageInput.style.height = `${Math.min(els.messageInput.scrollHeight, 160)}px`;
    }

    async function startNewSession() {
        const userId = currentUserId();
        if (!userId) return;

        const conversationId = state.conversationId;
        try {
            if (conversationId) {
                await request(ENDPOINTS.clearConversation(userId, conversationId), { method: 'DELETE' });
            }

            localStorage.removeItem(`agent.runtime.conversationId.${userId}`);
            state.conversationId = null;
            state.messages = [];
            state.lastRunDuration = null;
            state.lastRunId = null;
            state.lastUsage = null;
            renderMessages();
            updateRuntimeMeta();
            setRunState('idle');
            addActivity('Conversation reset', conversationId ? conversationId : `user ${userId}`, 'success');
            toast(conversationId ? '当前 Conversation 已清空' : '已开始新的 Conversation');
            switchView('runtime');
        } catch (error) {
            toast(`清空 Conversation 失败：${error.message}`, 'error');
            addActivity('Conversation clear failed', error.message, 'error');
        }
    }

    async function loadMemory({ quiet = true } = {}) {
        const userId = currentUserId();
        if (!userId) return;
        try {
            state.memory = await request(ENDPOINTS.memory(userId)) || [];
            renderMemory();
            if (!quiet) addActivity('Memory refreshed', `${state.memory.length} items`, 'success');
        } catch (error) {
            state.memory = [];
            renderMemory(error.message);
            if (!quiet) addActivity('Memory refresh failed', error.message, 'error');
        }
    }

    function renderMemory(errorMessage = '') {
        if (errorMessage) {
            els.memoryList.innerHTML = `<div class="empty-state">${escapeHtml(errorMessage)}</div>`;
            return;
        }
        if (!state.memory.length) {
            els.memoryList.innerHTML = '<div class="empty-state">No long-term memory.</div>';
            return;
        }
        els.memoryList.innerHTML = state.memory.map((item) => `
            <div class="memory-item">
                <span class="memory-key">${escapeHtml(item.key)}</span>
                <span class="memory-value">${escapeHtml(item.value)}</span>
            </div>
        `).join('');
    }

    async function clearMemory() {
        const userId = currentUserId();
        if (!userId) return;
        if (!window.confirm(`确定清空用户 ${userId} 的短期会话和长期记忆吗？`)) return;
        try {
            await request(ENDPOINTS.memory(userId), { method: 'DELETE' });
            state.messages = [];
            state.memory = [];
            renderMessages();
            renderMemory();
            addActivity('User memory cleared', `user ${userId}`, 'success');
            toast('用户记忆已清空');
        } catch (error) {
            toast(`清空记忆失败：${error.message}`, 'error');
            addActivity('Memory clear failed', error.message, 'error');
        }
    }

    async function loadDocuments({ quiet = true } = {}) {
        try {
            state.documents = await request(ENDPOINTS.documents) || [];
            renderDocuments();
            if (!quiet) addActivity('Knowledge index refreshed', `${state.documents.length} documents`, 'success');
        } catch (error) {
            state.documents = [];
            renderDocuments(error.message);
            if (!quiet) addActivity('Knowledge refresh failed', error.message, 'error');
        }
        updateCapabilityCounts();
    }

    function renderDocuments(errorMessage = '') {
        const chunks = state.documents.reduce((sum, doc) => sum + (Number(doc.chunkCount) || 0), 0);
        els.docCount.textContent = String(state.documents.length);
        els.chunkCount.textContent = String(chunks);
        if (errorMessage) {
            els.docList.innerHTML = `<div class="empty-state">${escapeHtml(errorMessage)}</div>`;
            return;
        }
        if (!state.documents.length) {
            els.docList.innerHTML = '<div class="empty-state">No documents indexed.</div>';
            return;
        }
        els.docList.innerHTML = state.documents.map((doc) => `
            <div class="resource-item">
                <div class="resource-item-head">
                    <span class="resource-name">${escapeHtml(doc.name)}</span>
                    <span class="resource-tag">${escapeHtml(doc.chunkCount)} chunks</span>
                </div>
                <div class="resource-desc">${escapeHtml(doc.id || 'Indexed document')}</div>
            </div>
        `).join('');
    }

    async function uploadDocument() {
        const name = els.docName.value.trim();
        const content = els.docContent.value.trim();
        if (!name || !content) {
            toast('请填写文档名称和内容', 'error');
            return;
        }
        els.uploadDoc.disabled = true;
        const previous = els.uploadDoc.textContent;
        els.uploadDoc.textContent = 'Ingesting…';
        const startedAt = performance.now();
        try {
            const data = await post(ENDPOINTS.knowledgeUpload, { name, content });
            els.docName.value = '';
            els.docContent.value = '';
            await loadDocuments();
            const duration = performance.now() - startedAt;
            addActivity('Document ingested', `${name} · ${data?.chunkCount ?? '?'} chunks · ${formatDuration(duration)}`, 'success');
            toast(`已入库：${name}`);
        } catch (error) {
            addActivity('Document ingest failed', error.message, 'error');
            toast(`文档入库失败：${error.message}`, 'error');
        } finally {
            els.uploadDoc.disabled = false;
            els.uploadDoc.textContent = previous;
        }
    }

    async function loadTools({ quiet = true } = {}) {
        try {
            state.tools = await request(ENDPOINTS.toolList) || [];
            renderTools();
            if (!quiet) addActivity('Tools refreshed', `${state.tools.length} registered`, 'success');
        } catch (error) {
            state.tools = [];
            renderTools(error.message);
            if (!quiet) addActivity('Tool refresh failed', error.message, 'error');
        }
        updateCapabilityCounts();
    }

    function renderTools(errorMessage = '') {
        if (errorMessage) {
            els.toolList.innerHTML = `<div class="empty-state">${escapeHtml(errorMessage)}</div>`;
            return;
        }
        if (!state.tools.length) {
            els.toolList.innerHTML = '<div class="empty-state">No registered tools.</div>';
            return;
        }
        els.toolList.innerHTML = state.tools.map((tool) => `
            <div class="resource-item">
                <div class="resource-item-head">
                    <span class="resource-name">${escapeHtml(tool.name)}</span>
                    <span class="resource-tag">TOOL</span>
                </div>
                <div class="resource-desc">${escapeHtml(tool.description || 'No description')}</div>
            </div>
        `).join('');
    }

    async function loadMcp({ quiet = true } = {}) {
        const results = await Promise.allSettled([
            request(ENDPOINTS.mcpServers),
            request(ENDPOINTS.mcpTools),
        ]);

        const serverResult = results[0];
        const toolResult = results[1];
        let hasError = false;
        if (serverResult.status === 'fulfilled') state.mcpServers = serverResult.value || [];
        else { state.mcpServers = []; hasError = true; }
        if (toolResult.status === 'fulfilled') state.mcpTools = toolResult.value || [];
        else { state.mcpTools = []; hasError = true; }

        renderMcpServers(serverResult.status === 'rejected' ? serverResult.reason.message : '');
        fillMcpSelects();
        updateCapabilityCounts();
        if (!quiet) {
            addActivity(
                hasError ? 'MCP refresh partially failed' : 'MCP refreshed',
                `${state.mcpServers.length} servers · ${state.mcpTools.length} tools`,
                hasError ? 'error' : 'success',
            );
        }
    }

    function renderMcpServers(errorMessage = '') {
        if (errorMessage) {
            els.mcpServers.innerHTML = `<div class="empty-state">${escapeHtml(errorMessage)}</div>`;
            return;
        }
        if (!state.mcpServers.length) {
            els.mcpServers.innerHTML = '<div class="empty-state">No MCP servers connected.</div>';
            return;
        }
        els.mcpServers.innerHTML = state.mcpServers.map((server) => `
            <div class="resource-item">
                <div class="resource-item-head">
                    <span class="resource-name">${escapeHtml(server.name)}</span>
                    <span class="resource-tag">${escapeHtml(server.toolCount ?? 0)} tools</span>
                </div>
                <div class="resource-desc">${escapeHtml(server.endpoint || 'Built-in')}</div>
            </div>
        `).join('');
    }

    function fillMcpSelects() {
        const previousServer = els.mcpServer.value;
        els.mcpServer.innerHTML = '';
        state.mcpServers.forEach((server) => {
            const option = document.createElement('option');
            option.value = server.name;
            option.textContent = server.name;
            els.mcpServer.appendChild(option);
        });
        if (previousServer && state.mcpServers.some((server) => server.name === previousServer)) {
            els.mcpServer.value = previousServer;
        }
        fillMcpTools();
    }

    function fillMcpTools() {
        const server = els.mcpServer.value;
        els.mcpTool.innerHTML = '';
        state.mcpTools
            .filter((tool) => tool.server === server)
            .forEach((tool) => {
                const option = document.createElement('option');
                option.value = tool.name;
                option.textContent = tool.description ? `${tool.name} — ${tool.description}` : tool.name;
                els.mcpTool.appendChild(option);
            });
    }

    async function connectMcp() {
        const name = els.mcpName.value.trim();
        const endpoint = els.mcpEndpoint.value.trim();
        if (!name) {
            toast('请填写 MCP server 名称', 'error');
            return;
        }
        els.mcpConnect.disabled = true;
        try {
            await post(ENDPOINTS.mcpConnect, { name, endpoint });
            els.mcpName.value = '';
            els.mcpEndpoint.value = '';
            await loadMcp();
            addActivity('MCP server connected', `${name}${endpoint ? ` · ${endpoint}` : ''}`, 'success');
            toast(`MCP server 已连接：${name}`);
        } catch (error) {
            addActivity('MCP connect failed', error.message, 'error');
            toast(`MCP 连接失败：${error.message}`, 'error');
        } finally {
            els.mcpConnect.disabled = false;
        }
    }

    async function invokeMcp() {
        const server = els.mcpServer.value;
        const tool = els.mcpTool.value;
        if (!server || !tool) {
            toast('请选择 MCP server 和 tool', 'error');
            return;
        }

        const argsText = els.mcpArgs.value.trim();
        if (argsText) {
            try { JSON.parse(argsText); }
            catch (error) {
                toast(`Arguments JSON 不合法：${error.message}`, 'error');
                return;
            }
        }

        els.mcpCall.disabled = true;
        els.mcpResult.textContent = 'Invoking…';
        const startedAt = performance.now();
        try {
            const data = await post(ENDPOINTS.mcpCall, {
                server,
                tool,
                // Keep the current backend contract: arguments is a JSON string, not an object.
                arguments: argsText || null,
            });
            const duration = performance.now() - startedAt;
            els.mcpResult.textContent = data?.result ?? '(empty result)';
            addActivity('MCP tool invoked', `${server}/${tool} · ${formatDuration(duration)}`, 'success');
        } catch (error) {
            els.mcpResult.textContent = `Invocation failed: ${error.message}`;
            addActivity('MCP invocation failed', `${server}/${tool} · ${error.message}`, 'error');
        } finally {
            els.mcpCall.disabled = false;
        }
    }

    function updateCapabilityCounts() {
        els.toolCount.textContent = String(state.tools.length);
        els.inspectorDocCount.textContent = String(state.documents.length);
        els.mcpCount.textContent = String(state.mcpServers.length);
    }

    async function checkHealth() {
        els.healthPill.className = 'status-pill checking';
        els.healthText.textContent = 'Checking runtime';
        try {
            const data = await request(ENDPOINTS.health);
            const status = String(data?.status || 'UP').toUpperCase();
            const up = status === 'UP';
            els.healthPill.className = `status-pill ${up ? 'up' : 'down'}`;
            els.healthText.textContent = up ? 'Runtime healthy' : `Runtime ${status}`;
        } catch (error) {
            // Actuator may be disabled/exposed differently. Do not treat that as Agent API failure.
            els.healthPill.className = 'status-pill checking';
            els.healthText.textContent = 'Health unavailable';
            els.healthPill.title = `Actuator health unavailable: ${error.message}`;
        }
    }

    async function refreshCapabilities({ quiet = false } = {}) {
        await Promise.all([loadDocuments(), loadTools(), loadMcp()]);
        if (!quiet) addActivity('Capabilities refreshed', `${state.tools.length} tools · ${state.documents.length} docs · ${state.mcpServers.length} MCP`, 'success');
    }

    async function refreshAll() {
        els.refreshAll.disabled = true;
        try {
            await Promise.all([checkHealth(), loadMemory(), refreshCapabilities({ quiet: true })]);
            addActivity('Runtime state refreshed', 'health + memory + capabilities', 'success');
        } finally {
            els.refreshAll.disabled = false;
        }
    }

    function syncUserContext() {
        const userId = currentUserId();
        state.userId = userId;
        localStorage.setItem('agent.runtime.userId', userId);
        state.conversationId = userId
            ? (localStorage.getItem(`agent.runtime.conversationId.${userId}`) || null)
            : null;
        state.messages = [];
        state.lastRunDuration = null;
        state.lastRunId = null;
        state.lastUsage = null;
        renderMessages();
        updateRuntimeMeta();
        loadMemory();
        addActivity('User context changed', userId || '(empty)', 'success');
    }

    function bindEvents() {
        $$('.nav-item').forEach((button) => button.addEventListener('click', () => switchView(button.dataset.view)));
        $$('.mode-button').forEach((button) => button.addEventListener('click', () => setMode(button.dataset.mode)));

        els.chatForm.addEventListener('submit', (event) => {
            event.preventDefault();
            sendMessage();
        });
        els.messageInput.addEventListener('input', autoResizeComposer);
        els.messageInput.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                sendMessage();
            }
        });
        els.userId.addEventListener('change', syncUserContext);
        els.newSession.addEventListener('click', startNewSession);
        els.refreshMemory.addEventListener('click', () => loadMemory({ quiet: false }));
        els.clearMemory.addEventListener('click', clearMemory);
        els.clearActivity.addEventListener('click', () => { state.activities = []; renderActivity(); });
        els.refreshAll.addEventListener('click', refreshAll);
        els.refreshCapabilities.addEventListener('click', () => refreshCapabilities());

        els.openRagRuntime.addEventListener('click', () => { switchView('runtime'); setMode('rag'); });
        els.openToolRuntime.addEventListener('click', () => { switchView('runtime'); setMode('tool'); });
        els.uploadDoc.addEventListener('click', uploadDocument);
        els.refreshDocs.addEventListener('click', () => loadDocuments({ quiet: false }));
        els.refreshTools.addEventListener('click', () => loadTools({ quiet: false }));
        els.refreshMcp.addEventListener('click', () => loadMcp({ quiet: false }));
        els.mcpConnect.addEventListener('click', connectMcp);
        els.mcpCall.addEventListener('click', invokeMcp);
        els.mcpServer.addEventListener('change', fillMcpTools);

        els.inspectorToggle.addEventListener('click', () => els.inspector.classList.toggle('open'));
        els.closeInspector.addEventListener('click', () => els.inspector.classList.remove('open'));

        document.addEventListener('keydown', (event) => {
            if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
                event.preventDefault();
                switchView('runtime');
                els.messageInput.focus();
            }
            if (event.key === 'Escape') els.inspector.classList.remove('open');
        });
    }

    async function init() {
        els.userId.value = state.userId;
        bindEvents();
        bindPromptSuggestions();
        setMode(state.mode);
        switchView('runtime');
        renderMessages();
        renderMemory();
        renderDocuments();
        renderTools();
        renderMcpServers();
        renderActivity();
        updateCapabilityCounts();
        updateRuntimeMeta();
        await refreshAll();
        els.messageInput.focus();
    }

    init();
})();
