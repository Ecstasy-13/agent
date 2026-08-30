/* ==========================================================================
   Java 智能 Agent 系统 —— 前端逻辑
   后端接口（同源，无需跨域）：
     POST   /agent/chat              对话
     GET    /agent/memory/{userId}   查询长期记忆
     DELETE /agent/session/{userId}  清空短期会话
     DELETE /agent/memory/{userId}   清空全部记忆
     POST   /agent/knowledge/upload  知识库文档入库
     POST   /agent/knowledge/ask     知识库提问
     GET    /agent/knowledge/documents 列出文档
     POST   /agent/tool/call         工具调用对话
     GET    /agent/tool/list         列出工具
     POST   /agent/mcp/connect       MCP 连接
     GET    /agent/mcp/servers       MCP 服务器列表
     GET    /agent/mcp/tools         MCP 工具列表
     POST   /agent/mcp/call          MCP 工具调用
   响应统一结构：{ code, message, data }
   ========================================================================== */

(function () {
    'use strict';

    // ---------- 通用工具 ----------
    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    async function api(path, options) {
        const res = await fetch(path, options);
        let body = null;
        try {
            body = await res.json();
        } catch (e) {
            throw new Error('服务返回了非 JSON 内容（HTTP ' + res.status + '）');
        }
        if (body.code !== 200) {
            throw new Error(body.message || '请求失败（HTTP ' + res.status + '）');
        }
        return body.data;
    }

    function clear(el) {
        while (el.firstChild) el.removeChild(el.firstChild);
    }

    function empty(el, text) {
        clear(el);
        const p = document.createElement('p');
        p.className = 'empty';
        p.textContent = text;
        el.appendChild(p);
    }

    // ==========================================================================
    // 功能导航切换
    // ==========================================================================
    const navItems = document.querySelectorAll('.nav-item');
    function switchView(name) {
        navItems.forEach((b) => b.classList.toggle('active', b.dataset.view === name));
        document.querySelectorAll('.view').forEach((v) => {
            v.hidden = v.id !== 'view-' + name;
        });
    }
    navItems.forEach((b) => b.addEventListener('click', () => switchView(b.dataset.view)));

    // ==========================================================================
    // 一、对话视图
    // ==========================================================================
    const userIdInput = document.getElementById('userId');
    const messagesEl = document.getElementById('messages');
    const chatForm = document.getElementById('chatForm');
    const messageInput = document.getElementById('messageInput');
    const sendBtn = document.getElementById('sendBtn');
    const memoryListEl = document.getElementById('memoryList');
    const memoryEmptyEl = document.getElementById('memoryEmpty');
    const refreshMemoryBtn = document.getElementById('refreshMemory');
    const newSessionBtn = document.getElementById('newSession');
    const clearMemoryBtn = document.getElementById('clearMemory');

    let history = [];
    const welcomeEl = messagesEl.querySelector('.welcome');

    function currentUserId() {
        return userIdInput.value.trim();
    }

    function setBusy(busy) {
        sendBtn.disabled = busy;
        messageInput.disabled = busy;
        sendBtn.textContent = busy ? '思考中…' : '发送';
    }

    function scrollToBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function renderMessages() {
        clear(messagesEl);
        if (history.length === 0) {
            if (welcomeEl) messagesEl.appendChild(welcomeEl);
            return;
        }
        for (const item of history) {
            const div = document.createElement('div');
            div.className = 'msg ' + item.role;
            if (item.error) div.classList.add('error');

            const role = document.createElement('div');
            role.className = 'msg-role';
            role.textContent = item.role === 'user' ? '我' : 'Agent';

            const bubble = document.createElement('div');
            bubble.className = 'bubble';
            bubble.textContent = item.content;

            div.appendChild(role);
            div.appendChild(bubble);
            messagesEl.appendChild(div);
        }
        scrollToBottom();
    }

    function showTyping() {
        const div = document.createElement('div');
        div.className = 'msg assistant';
        div.id = 'typing-indicator';
        div.innerHTML =
            '<div class="msg-role">Agent</div>' +
            '<div class="bubble"><div class="typing"><span></span><span></span><span></span></div></div>';
        messagesEl.appendChild(div);
        scrollToBottom();
    }

    function hideTyping() {
        const t = document.getElementById('typing-indicator');
        if (t) t.remove();
    }

    function renderMemory(items) {
        clear(memoryListEl);
        if (!items || items.length === 0) {
            memoryListEl.appendChild(memoryEmptyEl);
            return;
        }
        for (const item of items) {
            const row = document.createElement('div');
            row.className = 'memory-item';

            const key = document.createElement('span');
            key.className = 'memory-key';
            key.textContent = item.key;

            const value = document.createElement('span');
            value.className = 'memory-value';
            value.textContent = item.value;

            row.appendChild(key);
            row.appendChild(value);
            memoryListEl.appendChild(row);
        }
    }

    function loadMemory() {
        const userId = currentUserId();
        if (!userId) return;
        api('/agent/memory/' + encodeURIComponent(userId))
            .then(renderMemory)
            .catch((e) => console.warn('加载记忆失败：', e.message));
    }

    async function sendMessage() {
        const userId = currentUserId();
        const text = messageInput.value.trim();
        if (!userId || !text || sendBtn.disabled) return;

        messageInput.value = '';
        autoResize();

        history.push({ role: 'user', content: text });
        renderMessages();
        setBusy(true);
        showTyping();

        try {
            const data = await api('/agent/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId: userId, message: text }),
            });
            history.push({ role: 'assistant', content: data.answer });
        } catch (e) {
            history.push({ role: 'assistant', content: e.message, error: true });
        } finally {
            hideTyping();
            renderMessages();
            setBusy(false);
            messageInput.focus();
        }
        setTimeout(loadMemory, 1200);
    }

    async function newSession() {
        const userId = currentUserId();
        if (!userId) return;
        try {
            await api('/agent/session/' + encodeURIComponent(userId), { method: 'DELETE' });
        } catch (e) {
            console.warn('清空会话失败：', e.message);
        }
        history = [];
        renderMessages();
    }

    async function clearMemory() {
        const userId = currentUserId();
        if (!userId) return;
        if (!confirm('确定清空该用户的全部记忆（含长期画像）吗？')) return;
        try {
            await api('/agent/memory/' + encodeURIComponent(userId), { method: 'DELETE' });
        } catch (e) {
            alert('清空记忆失败：' + e.message);
            return;
        }
        history = [];
        renderMessages();
        loadMemory();
    }

    function autoResize() {
        messageInput.style.height = 'auto';
        messageInput.style.height = Math.min(messageInput.scrollHeight, 160) + 'px';
    }

    chatForm.addEventListener('submit', (e) => {
        e.preventDefault();
        sendMessage();
    });
    messageInput.addEventListener('input', autoResize);
    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
    userIdInput.addEventListener('change', () => {
        history = [];
        renderMessages();
        loadMemory();
    });
    refreshMemoryBtn.addEventListener('click', loadMemory);
    newSessionBtn.addEventListener('click', newSession);
    clearMemoryBtn.addEventListener('click', clearMemory);

    // ==========================================================================
    // 二、知识库视图（RAG）
    // ==========================================================================
    const docNameEl = document.getElementById('docName');
    const docContentEl = document.getElementById('docContent');
    const uploadDocBtn = document.getElementById('uploadDoc');
    const refreshDocsBtn = document.getElementById('refreshDocs');
    const docListEl = document.getElementById('docList');
    const kbQuestionEl = document.getElementById('kbQuestion');
    const kbAskBtn = document.getElementById('kbAsk');
    const kbAnswerEl = document.getElementById('kbAnswer');
    const kbSourcesEl = document.getElementById('kbSources');

    function renderDocs(docs) {
        clear(docListEl);
        if (!docs || docs.length === 0) {
            empty(docListEl, '暂无文档');
            return;
        }
        for (const d of docs) {
            const row = document.createElement('div');
            row.className = 'doc-item';
            row.innerHTML =
                '<span class="doc-name">' + escapeHtml(d.name) + '</span>' +
                '<span class="doc-meta">分块数 ' + d.chunkCount + '</span>';
            docListEl.appendChild(row);
        }
    }

    function loadDocs() {
        api('/agent/knowledge/documents').then(renderDocs)
            .catch((e) => empty(docListEl, e.message));
    }

    function renderSources(sources) {
        clear(kbSourcesEl);
        if (!sources || sources.length === 0) {
            empty(kbSourcesEl, '暂无来源');
            return;
        }
        for (const s of sources) {
            const row = document.createElement('div');
            row.className = 'step-item';
            row.innerHTML =
                '<div class="step-tool">' + escapeHtml(s.document) + '</div>' +
                '<div>' + escapeHtml(s.text) + '</div>' +
                '<div class="step-args">相似度 ' + s.score + '</div>';
            kbSourcesEl.appendChild(row);
        }
    }

    async function uploadDoc() {
        const name = docNameEl.value.trim();
        const content = docContentEl.value.trim();
        if (!name || !content) {
            alert('请填写文档名称和内容');
            return;
        }
        uploadDocBtn.disabled = true;
        try {
            await api('/agent/knowledge/upload', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name, content: content }),
            });
            docContentEl.value = '';
            docNameEl.value = '';
            loadDocs();
        } catch (e) {
            alert('上传失败：' + e.message);
        } finally {
            uploadDocBtn.disabled = false;
        }
    }

    async function kbAsk() {
        const question = kbQuestionEl.value.trim();
        if (!question) return;
        kbAskBtn.disabled = true;
        kbAnswerEl.textContent = '检索与生成中…';
        try {
            const data = await api('/agent/knowledge/ask', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ question: question }),
            });
            kbAnswerEl.textContent = data.answer;
            renderSources(data.sources);
        } catch (e) {
            kbAnswerEl.textContent = '请求失败：' + e.message;
            renderSources(null);
        } finally {
            kbAskBtn.disabled = false;
        }
    }

    uploadDocBtn.addEventListener('click', uploadDoc);
    refreshDocsBtn.addEventListener('click', loadDocs);
    kbAskBtn.addEventListener('click', kbAsk);

    // ==========================================================================
    // 三、工具调用视图（Function Calling）
    // ==========================================================================
    const toolQuestionEl = document.getElementById('toolQuestion');
    const toolRunBtn = document.getElementById('toolRun');
    const toolAnswerEl = document.getElementById('toolAnswer');
    const toolStepsEl = document.getElementById('toolSteps');
    const toolListEl = document.getElementById('toolList');
    const refreshToolsBtn = document.getElementById('refreshTools');

    function renderTools(tools) {
        clear(toolListEl);
        if (!tools || tools.length === 0) {
            empty(toolListEl, '暂无工具');
            return;
        }
        for (const t of tools) {
            const row = document.createElement('div');
            row.className = 'doc-item';
            row.innerHTML =
                '<span class="doc-name">' + escapeHtml(t.name) + '</span>' +
                escapeHtml(t.description);
            toolListEl.appendChild(row);
        }
    }

    function loadTools() {
        api('/agent/tool/list').then(renderTools)
            .catch((e) => empty(toolListEl, e.message));
    }

    function renderSteps(steps) {
        clear(toolStepsEl);
        if (!steps || steps.length === 0) {
            empty(toolStepsEl, '本次未调用工具');
            return;
        }
        for (const s of steps) {
            const row = document.createElement('div');
            row.className = 'step-item';
            row.innerHTML =
                '<div class="step-tool">' + escapeHtml(s.tool) + '</div>' +
                '<div class="step-args">参数：' + escapeHtml(s.arguments) + '</div>' +
                '<div>结果：' + escapeHtml(s.result) + '</div>';
            toolStepsEl.appendChild(row);
        }
    }

    async function runTool() {
        const message = toolQuestionEl.value.trim();
        if (!message) return;
        toolRunBtn.disabled = true;
        toolAnswerEl.textContent = '调用中…';
        try {
            const data = await api('/agent/tool/call', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: message }),
            });
            toolAnswerEl.textContent = data.answer;
            renderSteps(data.steps);
        } catch (e) {
            toolAnswerEl.textContent = '请求失败：' + e.message;
            renderSteps(null);
        } finally {
            toolRunBtn.disabled = false;
        }
    }

    toolRunBtn.addEventListener('click', runTool);
    refreshToolsBtn.addEventListener('click', loadTools);

    // ==========================================================================
    // 四、MCP 视图
    // ==========================================================================
    const mcpNameEl = document.getElementById('mcpName');
    const mcpEndpointEl = document.getElementById('mcpEndpoint');
    const mcpConnectBtn = document.getElementById('mcpConnect');
    const mcpServerSel = document.getElementById('mcpServer');
    const mcpToolSel = document.getElementById('mcpTool');
    const mcpArgsEl = document.getElementById('mcpArgs');
    const mcpCallBtn = document.getElementById('mcpCall');
    const mcpResultEl = document.getElementById('mcpResult');
    const mcpServersEl = document.getElementById('mcpServers');
    const refreshMcpBtn = document.getElementById('refreshMcp');

    let mcpServers = [];
    let mcpTools = [];

    function renderMcpServers(servers) {
        clear(mcpServersEl);
        if (!servers || servers.length === 0) {
            empty(mcpServersEl, '暂无已连接服务器');
            return;
        }
        for (const s of servers) {
            const row = document.createElement('div');
            row.className = 'doc-item';
            row.innerHTML =
                '<span class="doc-name">' + escapeHtml(s.name) + '</span>' +
                '<span class="doc-meta">' + escapeHtml(s.endpoint || '内置') +
                ' · 工具 ' + s.toolCount + '</span>';
            mcpServersEl.appendChild(row);
        }
    }

    function fillServerSelect() {
        const prev = mcpServerSel.value;
        clear(mcpServerSel);
        for (const s of mcpServers) {
            const opt = document.createElement('option');
            opt.value = s.name;
            opt.textContent = s.name;
            mcpServerSel.appendChild(opt);
        }
        if (prev) mcpServerSel.value = prev;
        fillToolSelect();
    }

    function fillToolSelect() {
        clear(mcpToolSel);
        const server = mcpServerSel.value;
        const tools = mcpTools.filter((t) => t.server === server);
        for (const t of tools) {
            const opt = document.createElement('option');
            opt.value = t.name;
            opt.textContent = t.name + ' — ' + t.description;
            mcpToolSel.appendChild(opt);
        }
    }

    function loadMcp() {
        api('/agent/mcp/servers').then((servers) => {
            mcpServers = servers;
            renderMcpServers(servers);
            fillServerSelect();
        }).catch((e) => empty(mcpServersEl, e.message));

        api('/agent/mcp/tools').then((tools) => {
            mcpTools = tools;
            fillToolSelect();
        }).catch((e) => console.warn('加载 MCP 工具失败：', e.message));
    }

    async function connectMcp() {
        const name = mcpNameEl.value.trim();
        if (!name) {
            alert('请填写服务器名称');
            return;
        }
        mcpConnectBtn.disabled = true;
        try {
            await api('/agent/mcp/connect', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name, endpoint: mcpEndpointEl.value.trim() }),
            });
            mcpNameEl.value = '';
            mcpEndpointEl.value = '';
            loadMcp();
        } catch (e) {
            alert('连接失败：' + e.message);
        } finally {
            mcpConnectBtn.disabled = false;
        }
    }

    async function callMcp() {
        const server = mcpServerSel.value;
        const tool = mcpToolSel.value;
        if (!server || !tool) {
            alert('请选择服务器和工具');
            return;
        }
        let args = mcpArgsEl.value.trim();
        if (args) {
            try {
                JSON.parse(args);
            } catch (e) {
                alert('参数不是合法 JSON：' + e.message);
                return;
            }
        }
        mcpCallBtn.disabled = true;
        mcpResultEl.textContent = '调用中…';
        try {
            const data = await api('/agent/mcp/call', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ server: server, tool: tool, arguments: args || null }),
            });
            mcpResultEl.textContent = data.result;
        } catch (e) {
            mcpResultEl.textContent = '调用失败：' + e.message;
        } finally {
            mcpCallBtn.disabled = false;
        }
    }

    mcpConnectBtn.addEventListener('click', connectMcp);
    mcpCallBtn.addEventListener('click', callMcp);
    refreshMcpBtn.addEventListener('click', loadMcp);
    mcpServerSel.addEventListener('change', fillToolSelect);

    // ==========================================================================
    // 初始化
    // ==========================================================================
    loadMemory();
    loadDocs();
    loadTools();
    loadMcp();
})();
