// 页面逻辑阅读顺序：状态与元素 → 事件绑定 → 请求处理 → 各模块渲染 → 通用工具。
// 统一保存登录用户、会话选择和页面加载状态。
const state = {
    currentUser: null,
    chatSessionId: null,
    chatSessions: [],
    toastTimer: null,
    authMode: "login",
    activePage: "market",
    marketLoaded: false,
    marketRequestId: 0,
    marketData: null,
    marketChatBusy: false,
    // 财报页只在用户进入该页面后加载，避免首页每次都请求巨潮资讯。
    reportLoaded: false,
    reportStocks: [],
    selectedReportStock: null,
    financialReports: [],
    selectedFinancialReport: null,
    reportAnalyzing: false,
    ragBusy: false,
    ragEpoch: 0,
    ragDocuments: new Map()
};

const authShell = document.getElementById("authShell");
const appShell = document.getElementById("appShell");
const navItems = document.querySelectorAll("[data-page]");
const authTitle = document.getElementById("authTitle");
const authSwitchBtn = document.getElementById("authSwitchBtn");
const loginForm = document.getElementById("loginForm");
const registerForm = document.getElementById("registerForm");
const userBadge = document.getElementById("userBadge");
const logoutBtn = document.getElementById("logoutBtn");
const chatBoard = document.getElementById("chatBoard");
const chatForm = document.getElementById("chatForm");
const chatInput = document.getElementById("chatInput");
const memoryBadge = document.getElementById("memoryBadge");
const currentSessionTitle = document.getElementById("currentSessionTitle");
const chatSessionList = document.getElementById("chatSessionList");
const newSessionBtn = document.getElementById("newSessionBtn");
const favoriteForm = document.getElementById("favoriteForm");
const favoriteList = document.getElementById("favoriteList");
const marketForm = document.getElementById("marketForm");
const marketSymbolInput = document.getElementById("marketSymbolInput");
const marketDataStatus = document.getElementById("marketDataStatus");
const marketUpdatedAt = document.getElementById("marketUpdatedAt");
const marketIndexList = document.getElementById("marketIndexList");
const klineCanvas = document.getElementById("klineCanvas");
const marketChartTitle = document.getElementById("marketChartTitle");
const marketNewsList = document.getElementById("marketNewsList");
const marketAiInsight = document.getElementById("marketAiInsight");
const stockIdeasList = document.getElementById("stockIdeasList");
const marketRiskList = document.getElementById("marketRiskList");
const marketContextBadge = document.getElementById("marketContextBadge");
const marketAssistantBoard = document.getElementById("marketAssistantBoard");
const marketAssistantForm = document.getElementById("marketAssistantForm");
const marketAssistantInput = document.getElementById("marketAssistantInput");
const marketAssistantSubmitBtn = document.getElementById("marketAssistantSubmitBtn");
const reportWorkflowStatus = document.getElementById("reportWorkflowStatus");
const reportStockList = document.getElementById("reportStockList");
const reportListStatus = document.getElementById("reportListStatus");
const financialReportList = document.getElementById("financialReportList");
const selectedFinancialReport = document.getElementById("selectedFinancialReport");
const analyzeFinancialReportBtn = document.getElementById("analyzeFinancialReportBtn");
const financialReportOutput = document.getElementById("financialReportOutput");
const financialReportHistory = document.getElementById("financialReportHistory");
const toast = document.getElementById("toast");
const reportRagForm = document.getElementById("reportRagForm");
const reportRagQuestion = document.getElementById("reportRagQuestion");
const reportRagSubmit = document.getElementById("reportRagSubmit");
const reportRagOutput = document.getElementById("reportRagOutput");

document.addEventListener("DOMContentLoaded", () => {
    bindAuth();
    bindNavigation();
    bindQuickActions();
    bindForms();
    if (typeof ResizeObserver !== "undefined") {
        new ResizeObserver(() => {
            if (state.activePage === "market" && state.marketData) {
                drawKLineChart(pickArray(state.marketData, "kLines", "KLines", "klines").map(normalizeKLinePoint));
            }
        }).observe(klineCanvas);
    }
    checkCurrentUser();
});

// 绑定登录、注册切换和退出事件。
function bindAuth() {
    authSwitchBtn.addEventListener("click", () => {
        state.authMode = state.authMode === "login" ? "register" : "login";
        renderAuthMode();
    });

    loginForm.addEventListener("submit", handleLoginSubmit);
    registerForm.addEventListener("submit", handleRegisterSubmit);
    logoutBtn.addEventListener("click", handleLogout);
}

// 绑定业务表单及按钮事件。
function bindForms() {
    chatForm.addEventListener("submit", handleChatSubmit);
    favoriteForm.addEventListener("submit", handleFavoriteSubmit);
    newSessionBtn.addEventListener("click", () => createChatSession(true).catch(() => {}));
    marketForm.addEventListener("submit", handleMarketSubmit);
    if (marketAssistantForm && marketAssistantSubmitBtn) {
        marketAssistantForm.addEventListener("submit", handleMarketAssistantSubmit);
        marketAssistantSubmitBtn.addEventListener("click", handleMarketAssistantSubmit);
    }
    analyzeFinancialReportBtn.addEventListener("click", handleFinancialReportAnalyze);
    reportRagForm.addEventListener("submit", handleReportRagQuestion);
}

// 把快捷问题填入聊天框并提交。
function bindQuickActions() {
    document.querySelectorAll("[data-prompt]").forEach((button) => {
        button.addEventListener("click", () => {
            chatInput.value = button.dataset.prompt;
            chatForm.requestSubmit();
        });
    });
}

// 绑定顶部页面导航。
function bindNavigation() {
    navItems.forEach((button) => {
        button.addEventListener("click", () => switchPage(button.dataset.page));
    });
}

// 切换可见页面，首次进入时按需加载市场或财报数据。
function switchPage(page) {
    state.activePage = page;
    navItems.forEach((button) => button.classList.toggle("active", button.dataset.page === page));
    document.querySelectorAll(".page-section").forEach((section) => {
        section.classList.toggle("active", section.id === `${page}Page`);
    });
    if (page === "market" && !state.marketLoaded && state.currentUser) {
        loadMarketToday();
    }
    // 收藏股票和财报公告属于相对重的外部数据，延迟到用户真正打开财报页再加载。
    if (page === "reports" && !state.reportLoaded && state.currentUser) {
        loadFinancialReportWorkspace();
    }
}

// 启动时检查 Session，决定显示登录页还是应用。
async function checkCurrentUser() {
    try {
        const response = await request("/api/auth/me", {suppressAuthRedirect: true});
        await showApp(response.data);
    } catch (error) {
        showAuth();
    }
}

// 读取登录字段并提交。
async function handleLoginSubmit(event) {
    event.preventDefault();
    const payload = {
        username: document.getElementById("loginUsername").value.trim(),
        password: document.getElementById("loginPassword").value
    };

    await submitAuth("login", payload, loginForm, "登录");
}

// 读取注册字段并提交。
async function handleRegisterSubmit(event) {
    event.preventDefault();
    const payload = {
        username: document.getElementById("registerUsername").value.trim(),
        nickname: document.getElementById("registerNickname").value.trim(),
        password: document.getElementById("registerPassword").value
    };

    await submitAuth("register", payload, registerForm, "注册");
}

// 登录和注册共享请求流程，各自的字段仍由对应表单处理。
async function submitAuth(action, payload, form, actionName) {
    try {
        const response = await request(`/api/auth/${action}`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        form.reset();
        showToast(`${actionName}成功`);
        await showApp(response.data);
    } catch (error) {
        showToast(error.message || `${actionName}失败`);
    }
}

// 退出登录，清理前端登录状态。
async function handleLogout() {
    try {
        await request("/api/auth/logout", {method: "POST"});
    } catch (error) {
        // 即使后端会话已经过期，前端也可以回到登录页。
    }
    showToast("已退出登录");
    showAuth();
}

// 在当前会话发送消息，并显示回复及更新会话列表。
async function handleChatSubmit(event) {
    event.preventDefault();
    const message = chatInput.value.trim();
    if (!message) {
        showToast("请输入聊天内容");
        return;
    }

    try {
        await ensureActiveSession();
    } catch (error) {
        showToast(error.message || "创建聊天会话失败");
        return;
    }

    appendMessage("user", message);
    chatInput.value = "";

    try {
        const response = await request("/api/chat", {
            method: "POST",
            body: JSON.stringify({
                chatSessionId: state.chatSessionId,
                message
            })
        });

        state.chatSessionId = response.data.chatSessionId;
        persistLastSessionId(state.chatSessionId);
        updateMemoryBadge();
        appendMessage("agent", response.data.answer);
        await refreshChatSessions();
        loadDashboard();
    } catch (error) {
        const messageText = error.message || "系统暂时无法完成这次对话";
        appendMessage("agent", `请求失败：${messageText}`);
        showToast(messageText);
    }
}

// 新增收藏，并刷新相关列表。
async function handleFavoriteSubmit(event) {
    event.preventDefault();
    const payload = {
        name: document.getElementById("favoriteName").value.trim(),
        symbol: document.getElementById("favoriteSymbol").value.trim(),
        category: document.getElementById("favoriteCategory").value,
        note: document.getElementById("favoriteNote").value.trim()
    };

    try {
        await request("/api/favorites", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        favoriteForm.reset();
        showToast("收藏成功");
        loadFavorites();
        loadDashboard();
        invalidateFinancialReportWorkspace();
    } catch (error) {
        showToast(error.message || "收藏失败");
    }
}

// 按输入的标的重新加载市场数据。
async function handleMarketSubmit(event) {
    event.preventDefault();
    await loadMarketToday(marketSymbolInput.value.trim());
}

// 携带当前市场快照追问，并更新等待中的消息。
async function handleMarketAssistantSubmit(event) {
    if (event) {
        event.preventDefault();
    }
    const question = marketAssistantInput.value.trim();
    if (!question) {
        showToast("请输入要追问的问题");
        return;
    }
    if (!state.marketData) {
        showToast("先刷新今日市场数据，再追问");
        return;
    }
    if (state.marketChatBusy) {
        return;
    }

    state.marketChatBusy = true;
    marketAssistantSubmitBtn.disabled = true;
    marketAssistantInput.value = "";
    appendMarketAssistantMessage("user", question);
    const pending = appendMarketAssistantMessage("agent", "正在结合当前页面数据思考...");

    try {
        const response = await request("/api/market/chat", {
            method: "POST",
            body: JSON.stringify({
                question,
                symbol: state.marketData.watchedSymbol,
                context: compactMarketContext(state.marketData)
            })
        });
        pending.textContent = response.data.answer;
    } catch (error) {
        pending.textContent = error.message || "市场追问失败";
        showToast(error.message || "市场追问失败");
    } finally {
        state.marketChatBusy = false;
        marketAssistantSubmitBtn.disabled = false;
    }
}

// 显示登录后的界面，并加载当前用户的数据。
async function showApp(user) {
    state.currentUser = user;
    authShell.hidden = true;
    appShell.hidden = false;
    userBadge.textContent = `${user.nickname || user.username} @${user.username}`;
    switchPage(state.activePage || "market");
    await loadChatSessions();
    loadDashboard();
    loadFavorites();
}

// 回到登录页，清理会话和财报状态。
function showAuth() {
    state.currentUser = null;
    state.marketRequestId++;
    state.marketData = null;
    state.marketLoaded = false;
    state.chatSessionId = null;
    state.chatSessions = [];
    resetFinancialReportState();
    appShell.hidden = true;
    authShell.hidden = false;
    chatBoard.innerHTML = "";
    renderAuthMode();
}

// 清空财报页缓存及选择状态。
function resetFinancialReportState() {
    state.ragDocuments.clear();
    state.reportLoaded = false;
    state.reportStocks = [];
    state.selectedReportStock = null;
    state.financialReports = [];
    state.selectedFinancialReport = null;
    state.reportAnalyzing = false;
    resetReportRag();
}

// 收藏变更后使财报缓存失效，必要时立即刷新。
function invalidateFinancialReportWorkspace() {
    resetFinancialReportState();
    if (state.activePage === "reports" && state.currentUser) {
        // 收藏变动后，财报页展示的股票列表必须跟着当前账号重新同步。
        loadFinancialReportWorkspace();
    }
}

// 切换登录和注册表单的显示状态。
function renderAuthMode() {
    const isLogin = state.authMode === "login";
    authTitle.textContent = isLogin ? "登录你的账号" : "注册新账号";
    loginForm.hidden = !isLogin;
    registerForm.hidden = isLogin;
    authSwitchBtn.textContent = isLogin ? "还没有账号？去注册" : "已有账号？去登录";
}

// 加载仪表盘统计数量。
async function loadDashboard() {
    try {
        const response = await request("/api/dashboard");
        const data = response.data;
        document.getElementById("favoriteCount").textContent = data.favoriteCount;
        document.getElementById("chatSessionCount").textContent = data.chatSessionCount;
    } catch (error) {
        showToast(error.message || "仪表盘加载失败");
    }
}

// 请求行情快照并更新加载状态。
async function loadMarketToday(symbol = marketSymbolInput.value.trim() || "000001") {
    const requestId = ++state.marketRequestId;
    const user = state.currentUser;
    marketDataStatus.textContent = "正在加载市场数据...";
    marketAiInsight.textContent = "正在生成市场简报...";
    marketContextBadge.textContent = "正在同步页面上下文...";
    try {
        const response = await request(`/api/market/today?symbol=${encodeURIComponent(symbol)}`);
        if (requestId !== state.marketRequestId || user !== state.currentUser) return;
        state.marketData = response.data;
        renderMarketToday(response.data);
        state.marketLoaded = true;
    } catch (error) {
        if (requestId !== state.marketRequestId || user !== state.currentUser) return;
        state.marketData = null;
        state.marketLoaded = false;
        marketIndexList.innerHTML = `<div class="empty-state">指数行情暂时不可用，请重试。</div>`;
        drawKLineChart([]);
        marketUpdatedAt.textContent = "页面刷新失败";
        marketChartTitle.textContent = "K 线";
        document.getElementById("marketChartMeta").textContent = "行情暂时不可用";
        marketDataStatus.textContent = error.message || "今日市场加载失败";
        marketAiInsight.textContent = "市场数据暂时不可用，请稍后再试。";
        marketContextBadge.textContent = "市场上下文不可用";
        showToast(error.message || "今日市场加载失败");
    }
}

// 把市场快照渲染为指数、图表、新闻和研究提示。
function renderMarketToday(data) {
    const indices = pickArray(data, "indices");
    const news = pickArray(data, "news");
    const stockIdeas = pickArray(data, "stockIdeas");
    const riskChecklist = pickArray(data, "riskChecklist");
    const kLines = pickArray(data, "kLines", "KLines", "klines").map(normalizeKLinePoint)
        .filter((point) => point.date && [point.open, point.close, point.high, point.low].every(Number.isFinite));

    marketSymbolInput.value = data.watchedSymbol || marketSymbolInput.value;
    marketDataStatus.textContent = data.dataStatus || "已加载";
    marketUpdatedAt.textContent = `页面刷新：${formatDateTime(data.generatedAt)}`;
    marketChartTitle.textContent = `${data.watchedName || data.watchedSymbol} K 线`;
    document.getElementById("marketChartMeta").textContent = kLines.length
        ? `日线走势，红涨绿跌。来源：${data.kLineSource || "未注明"} · 最后交易日：${kLines[kLines.length - 1].date}`
        : "行情暂时不可用，请稍后刷新。";
    marketAiInsight.textContent = `${data.aiInsight}\n\n${data.disclaimer || ""}`;
    marketContextBadge.textContent = `已同步：${data.watchedName || data.watchedSymbol}，${formatDateTime(data.generatedAt)}`;

    marketIndexList.innerHTML = indices.map((item) => {
        const change = Number(item.changePercent);
        const trend = Number.isNaN(change) ? "" : change >= 0 ? "up" : "down";
        return `
            <article class="market-index-card ${trend}">
                <span>${escapeHtml(item.name)}</span>
                <strong>${escapeHtml(item.latest)}</strong>
                <p>${escapeHtml(item.change)} / ${escapeHtml(item.changePercent)}%</p>
                <small>${escapeHtml(item.source || "来源未注明")} · 行情时间：${escapeHtml(item.quotedAt ? formatDateTime(item.quotedAt) : "未提供")}</small>
            </article>
        `;
    }).join("") || `<div class="empty-state">指数行情暂时不可用，请稍后刷新。</div>`;

    marketNewsList.innerHTML = news.map((item) => `
        <article class="market-news-item">
            <div>
                <span class="pill">${escapeHtml(item.impactTag || "市场要闻")}</span>
                <strong>${escapeHtml(item.title)}</strong>
            </div>
            <p>${escapeHtml(item.summary || "")}</p>
            ${item.url ? `<a href="${escapeHtml(item.url)}" target="_blank" rel="noreferrer">查看来源</a>` : ""}
        </article>
    `).join("");

    stockIdeasList.innerHTML = stockIdeas.map((item) => `
        <article class="stock-idea-item">
            <div class="collection-item-header">
                <div>
                    <strong>${escapeHtml(item.name)}</strong>
                    <div class="pill">${escapeHtml(item.symbol)} / ${escapeHtml(item.theme)}</div>
                </div>
            </div>
            <p>${escapeHtml(item.reason)}</p>
            <small>风险：${escapeHtml(item.risk)}</small>
        </article>
    `).join("");

    marketRiskList.innerHTML = riskChecklist.map((item) => `
        <div class="risk-item">${escapeHtml(item)}</div>
    `).join("");

    drawKLineChart(kLines);
}

// 裁剪页面上下文，控制市场追问的请求大小。
function compactMarketContext(data) {
    return {
        generatedAt: data.generatedAt,
        watchedSymbol: data.watchedSymbol,
        watchedName: data.watchedName,
        dataStatus: data.dataStatus,
        kLineSource: data.kLineSource,
        aiInsight: data.aiInsight,
        disclaimer: data.disclaimer,
        indices: pickArray(data, "indices"),
        kLines: pickArray(data, "kLines", "KLines", "klines").slice(-90).map(normalizeKLinePoint),
        news: pickArray(data, "news"),
        stockIdeas: pickArray(data, "stockIdeas"),
        riskChecklist: pickArray(data, "riskChecklist")
    };
}

// 兼容不同响应字段名，选择有效数组。
function pickArray(data, ...keys) {
    for (const key of keys) {
        if (Array.isArray(data?.[key])) {
            return data[key];
        }
    }
    return [];
}

// 统一 K 线数据字段，供图表使用。
function normalizeKLinePoint(point) {
    return {
        date: point.date,
        open: Number(point.open),
        close: Number(point.close),
        high: Number(point.high),
        low: Number(point.low),
        volume: Number(point.volume),
        amount: point.amount == null ? null : Number(point.amount)
    };
}

// 在 Canvas 绘制现有 K 线图。
function drawKLineChart(points) {
    const ctx = klineCanvas.getContext("2d");
    const rect = klineCanvas.getBoundingClientRect();
    const width = Math.max(320, rect.width || 900);
    const height = 360;
    const dpr = window.devicePixelRatio || 1;
    klineCanvas.width = width * dpr;
    klineCanvas.height = height * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, width, height);

    ctx.fillStyle = "rgba(255, 255, 255, 0.035)";
    ctx.fillRect(0, 0, width, height);

    if (!points.length) {
        ctx.fillStyle = "#95b1ab";
        ctx.font = "14px sans-serif";
        ctx.fillText("暂无 K 线数据", 24, 42);
        return;
    }

    const padding = {top: 20, right: 24, bottom: 32, left: 54};
    const highs = points.map((item) => item.high);
    const lows = points.map((item) => item.low);
    const max = Math.max(...highs);
    const min = Math.min(...lows);
    const range = Math.max(1, max - min);
    const chartWidth = width - padding.left - padding.right;
    const chartHeight = height - padding.top - padding.bottom;
    const step = chartWidth / points.length;
    const bodyWidth = Math.max(4, Math.min(12, step * 0.58));
    const y = (value) => padding.top + (max - value) / range * chartHeight;

    ctx.strokeStyle = "rgba(255,255,255,0.1)";
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
        const lineY = padding.top + chartHeight / 4 * i;
        ctx.beginPath();
        ctx.moveTo(padding.left, lineY);
        ctx.lineTo(width - padding.right, lineY);
        ctx.stroke();
    }

    points.forEach((point, index) => {
        const x = padding.left + step * index + step / 2;
        const up = point.close >= point.open;
        const color = up ? "#ff8e7a" : "#6ee7c8";
        ctx.strokeStyle = color;
        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.moveTo(x, y(point.high));
        ctx.lineTo(x, y(point.low));
        ctx.stroke();
        const top = Math.min(y(point.open), y(point.close));
        const bottom = Math.max(y(point.open), y(point.close));
        ctx.fillRect(x - bodyWidth / 2, top, bodyWidth, Math.max(2, bottom - top));
    });

    ctx.fillStyle = "#95b1ab";
    ctx.font = "12px sans-serif";
    ctx.fillText(max.toFixed(2), 8, y(max) + 4);
    ctx.fillText(min.toFixed(2), 8, y(min) + 4);
    ctx.fillText(points[0].date, padding.left, height - 12);
    ctx.fillText(points[points.length - 1].date, width - padding.right - 86, height - 12);
}

// 读取会话列表并恢复最近会话，必要时自动新建。
async function loadChatSessions(allowAutoCreate = true) {
    try {
        const response = await request("/api/chat/sessions");
        state.chatSessions = response.data || [];
        let selectedId = state.chatSessionId || loadLastSessionId();
        if (selectedId && !state.chatSessions.some((item) => item.id === selectedId)) {
            selectedId = null;
        }
        if (!selectedId && allowAutoCreate && state.chatSessions.length === 0) {
            const created = await request("/api/chat/sessions", {
                method: "POST",
                body: JSON.stringify({title: "新的聊天"})
            });
            state.chatSessions = [created.data];
            selectedId = created.data.id;
        }
        if (!selectedId && state.chatSessions.length > 0) {
            selectedId = state.chatSessions[0].id;
        }
        renderChatSessions();
        if (selectedId) {
            await selectChatSession(selectedId, true);
        } else {
            chatBoard.innerHTML = "";
            renderWelcomeMessage();
            updateMemoryBadge();
        }
    } catch (error) {
        showToast(error.message || "聊天会话加载失败");
    }
}

// 更新会话列表，不改变当前选择。
async function refreshChatSessions() {
    const response = await request("/api/chat/sessions");
    state.chatSessions = response.data || [];
    renderChatSessions();
    updateMemoryBadge();
}

// 新建会话，并按调用参数决定是否切换过去。
async function createChatSession(selectAfterCreate) {
    try {
        const response = await request("/api/chat/sessions", {
            method: "POST",
            body: JSON.stringify({title: "新的聊天"})
        });
        state.chatSessions.unshift(response.data);
        showToast("已新建聊天");
        if (selectAfterCreate) {
            await selectChatSession(response.data.id, true);
        } else {
            renderChatSessions();
        }
        loadDashboard();
        return response.data;
    } catch (error) {
        showToast(error.message || "新建聊天失败");
        throw error;
    }
}

// 发送消息前确保存在当前会话。
async function ensureActiveSession() {
    if (state.chatSessionId) {
        return;
    }
    const created = await createChatSession(true);
    state.chatSessionId = created.id;
}

// 切换会话并按需加载历史消息。
async function selectChatSession(sessionId, shouldLoadMessages) {
    state.chatSessionId = Number(sessionId);
    persistLastSessionId(state.chatSessionId);
    renderChatSessions();
    updateMemoryBadge();
    if (shouldLoadMessages) {
        await loadChatMessages();
    }
}

// 恢复当前会话中可展示的消息。
async function loadChatMessages() {
    chatBoard.innerHTML = "";
    try {
        const response = await request(`/api/chat/sessions/${state.chatSessionId}/messages`);
        const messages = response.data || [];
        if (!messages.length) {
            renderWelcomeMessage();
            return;
        }
        messages.forEach((message) => appendMessage(message.role, message.content));
    } catch (error) {
        renderWelcomeMessage();
        showToast(error.message || "聊天记录加载失败");
    }
}

// 渲染会话列表与选中状态。
function renderChatSessions() {
    if (!state.chatSessions.length) {
        chatSessionList.innerHTML = `<div class="empty-state">还没有聊天会话。</div>`;
        currentSessionTitle.textContent = "未选择会话";
        return;
    }

    const selected = state.chatSessions.find((item) => item.id === state.chatSessionId);
    currentSessionTitle.textContent = selected ? selected.title : "未选择会话";
    chatSessionList.innerHTML = state.chatSessions.map((item) => `
        <article class="session-item ${item.id === state.chatSessionId ? "active" : ""}">
            <button type="button" class="session-main" data-select-session-id="${item.id}">
                <strong>${escapeHtml(item.title)}</strong>
                <span>${formatDateTime(item.updatedAt)}</span>
            </button>
            <button type="button" class="delete-btn" data-delete-session-id="${item.id}">删除</button>
        </article>
    `).join("");

    chatSessionList.querySelectorAll("[data-select-session-id]").forEach((button) => {
        button.addEventListener("click", () => selectChatSession(button.dataset.selectSessionId, true));
    });
    chatSessionList.querySelectorAll("[data-delete-session-id]").forEach((button) => {
        button.addEventListener("click", () => deleteChatSession(Number(button.dataset.deleteSessionId)));
    });
}

// 删除会话并更新当前选择。
async function deleteChatSession(id) {
    if (!window.confirm("删除后这个会话的记忆也会一起删除，确定吗？")) {
        return;
    }

    try {
        await request(`/api/chat/sessions/${id}`, {method: "DELETE"});
        if (state.chatSessionId === id) {
            state.chatSessionId = null;
        }
        showToast("会话已删除");
        await loadChatSessions(false);
        loadDashboard();
    } catch (error) {
        showToast(error.message || "删除会话失败");
    }
}

// 读取并展示收藏列表。
async function loadFavorites() {
    try {
        const response = await request("/api/favorites");
        renderFavorites(response.data);
    } catch (error) {
        favoriteList.innerHTML = `<div class="empty-state">${escapeHtml(error.message || "收藏列表加载失败")}</div>`;
    }
}

// 加载财报页所需的收藏股票及分析历史。
async function loadFinancialReportWorkspace() {
    // 两类数据互不依赖：收藏股票决定左侧列表，历史报告用于右侧历史区，可以并行加载。
    await Promise.all([
        loadReportFavoriteStocks(),
        loadFinancialReportAnalyses()
    ]);
    state.reportLoaded = true;
}

// 读取可查询财报的收藏股票。
async function loadReportFavoriteStocks() {
    reportWorkflowStatus.textContent = "正在读取你的收藏股票...";
    try {
        const response = await request("/api/financial-reports/favorites");
        state.reportStocks = response.data || [];
        renderReportStocks();
        if (!state.selectedReportStock && state.reportStocks.length) {
            await selectReportStock(state.reportStocks[0].symbol);
        } else if (!state.reportStocks.length) {
            reportWorkflowStatus.textContent = "还没有可分析的收藏股票。请先在“智能分析”页面收藏股票。";
            financialReportList.innerHTML = `<div class="empty-state">收藏股票后，这里会展示最近财报公告。</div>`;
        }
    } catch (error) {
        reportWorkflowStatus.textContent = error.message || "收藏股票读取失败";
        reportStockList.innerHTML = `<div class="empty-state">${escapeHtml(error.message || "收藏股票读取失败")}</div>`;
    }
}

// 渲染财报页的股票选择列表。
function renderReportStocks() {
    if (!state.reportStocks.length) {
        reportStockList.innerHTML = `<div class="empty-state">还没有收藏股票。</div>`;
        return;
    }
    reportStockList.innerHTML = state.reportStocks.map((stock) => `
        <button type="button" class="report-stock-item ${state.selectedReportStock?.symbol === stock.symbol ? "active" : ""}" data-report-stock="${escapeHtml(stock.symbol)}">
            <strong>${escapeHtml(stock.name)}</strong>
            <span>${escapeHtml(stock.symbol)} / ${escapeHtml(stock.category || "股票")}</span>
            <small>${escapeHtml(stock.note || "暂无收藏备注")}</small>
        </button>
    `).join("");
    reportStockList.querySelectorAll("[data-report-stock]").forEach((button) => {
        button.addEventListener("click", () => selectReportStock(button.dataset.reportStock));
    });
}

// 切换股票并请求其财报公告。
async function selectReportStock(symbol) {
    const stock = state.reportStocks.find((item) => item.symbol === symbol);
    if (!stock) {
        return;
    }
    state.selectedReportStock = stock;
    state.selectedFinancialReport = null;
    state.financialReports = [];
    renderReportStocks();
    renderSelectedFinancialReport();
    reportListStatus.textContent = `正在获取 ${stock.name} 的财报公告...`;
    financialReportList.innerHTML = `<div class="empty-state">正在连接巨潮资讯...</div>`;
    try {
        const response = await request(`/api/financial-reports/stocks/${encodeURIComponent(stock.symbol)}/reports?name=${encodeURIComponent(stock.name)}`);
        const data = response.data;
        state.financialReports = data.reports || [];
        reportListStatus.textContent = data.status || "财报公告已加载";
        reportWorkflowStatus.textContent = `当前标的：${stock.name} / ${stock.symbol}`;
        renderFinancialReports();
    } catch (error) {
        reportListStatus.textContent = error.message || "财报公告获取失败";
        financialReportList.innerHTML = `<div class="empty-state">${escapeHtml(error.message || "财报公告获取失败")}</div>`;
    }
}

// 渲染当前股票的财报公告列表。
function renderFinancialReports() {
    financialReportList.scrollTop = 0;
    if (!state.financialReports.length) {
        financialReportList.innerHTML = `<div class="empty-state">暂时没有取到财报公告。可以确认股票代码是否为 6 位 A 股代码，或稍后重试。</div>`;
        return;
    }
    financialReportList.innerHTML = state.financialReports.map((report, index) => `
        <article class="financial-report-item ${state.selectedFinancialReport?.reportId === report.reportId ? "active" : ""}">
            <button type="button" class="financial-report-main" data-report-index="${index}">
                <div>
                    <span class="pill">${escapeHtml(report.reportType || "财报")}</span>
                    <strong>${escapeHtml(report.title || "未命名公告")}</strong>
                    <p>${escapeHtml(report.reportPeriod || "-")} / ${escapeHtml(report.publishTime || "-")} / ${escapeHtml(report.source || "-")}</p>
                </div>
            </button>
            ${report.pdfUrl ? `<a href="${escapeHtml(report.pdfUrl)}" target="_blank" rel="noreferrer">PDF</a>` : ""}
        </article>
    `).join("");
    financialReportList.querySelectorAll("[data-report-index]").forEach((button) => {
        button.addEventListener("click", () => selectFinancialReport(Number(button.dataset.reportIndex)));
    });
}

// 选择一份公告，更新待分析区域。
function selectFinancialReport(index) {
    state.selectedFinancialReport = state.financialReports[index];
    renderFinancialReports();
    renderSelectedFinancialReport();
}

// 展示所选公告信息及原始 PDF 链接。
function renderSelectedFinancialReport() {
    resetReportRag();
    const report = state.selectedFinancialReport;
    analyzeFinancialReportBtn.disabled = !report || state.reportAnalyzing;
    if (!report) {
        selectedFinancialReport.textContent = "请选择一份财报公告。";
        return;
    }
    selectedFinancialReport.innerHTML = `
        <strong>${escapeHtml(report.title)}</strong>
        <p>${escapeHtml(report.companyName)} / ${escapeHtml(report.symbol)} / ${escapeHtml(report.reportType)} / ${escapeHtml(report.publishTime || "-")}</p>
        ${report.pdfUrl ? `<a href="${escapeHtml(report.pdfUrl)}" target="_blank" rel="noreferrer">打开原始 PDF</a>` : ""}
    `;
}

// 切换账号或财报后，旧请求不再写入新页面。
function resetReportRag() {
    state.ragEpoch++;
    state.ragBusy = false;
    reportRagOutput.replaceChildren();
    reportRagQuestion.value = "";
    reportRagSubmit.disabled = !state.selectedFinancialReport;
}

async function handleReportRagQuestion(event) {
    event.preventDefault();
    const report = state.selectedFinancialReport;
    const question = reportRagQuestion.value.trim();
    if (!report || !question || state.ragBusy || !state.currentUser) return;
    const epoch = state.ragEpoch;
    const key = `${report.symbol}:${report.reportId}`;
    state.ragBusy = true;
    reportRagSubmit.disabled = true;
    try {
        let documentId = state.ragDocuments.get(key);
        if (!documentId) {
            reportRagOutput.textContent = "正在准备财报原文，首次处理可能需要几分钟，请勿重复提交…";
            const indexed = await request("/api/financial-reports/rag/index", {
                method: "POST", body: JSON.stringify({symbol: report.symbol, reportId: report.reportId})
            });
            if (epoch !== state.ragEpoch) return;
            documentId = indexed.data.documentId;
            state.ragDocuments.set(key, documentId);
        }
        reportRagOutput.textContent = "正在检索原文并生成回答…";
        const response = await request(`/api/financial-reports/rag/documents/${encodeURIComponent(documentId)}/questions`, {
            method: "POST", body: JSON.stringify({question})
        });
        if (epoch !== state.ragEpoch) return;
        const data = response.data;
        reportRagOutput.replaceChildren();
        const answer = document.createElement("p");
        answer.style.whiteSpace = "pre-wrap";
        answer.textContent = data.answer;
        reportRagOutput.append(answer);
        const coverage = document.createElement("p");
        coverage.textContent = data.coverage;
        reportRagOutput.append(coverage);
        for (const source of data.sources || []) {
            const details = document.createElement("details");
            const summary = document.createElement("summary");
            summary.textContent = `[${source.id}] PDF 第 ${source.pageNumber} 页`;
            const snippet = document.createElement("p");
            snippet.style.whiteSpace = "pre-wrap";
            snippet.textContent = source.snippet;
            details.append(summary, snippet);
            const url = new URL(source.sourceUrl);
            if (url.protocol === "https:" && url.hostname === "static.cninfo.com.cn") {
                const link = document.createElement("a");
                link.href = url.href;
                link.target = "_blank";
                link.rel = "noopener noreferrer";
                link.textContent = "打开该页原文";
                details.append(link);
            }
            reportRagOutput.append(details);
        }
    } catch (error) {
        if (epoch === state.ragEpoch) {
            state.ragDocuments.delete(key);
            reportRagOutput.textContent = error.message || "财报问答失败，请稍后重试";
        }
    } finally {
        if (epoch === state.ragEpoch) {
            state.ragBusy = false;
            reportRagSubmit.disabled = !state.selectedFinancialReport;
        }
    }
}

// 提交所选财报，展示正文分析并刷新历史。
async function handleFinancialReportAnalyze() {
    if (!state.selectedFinancialReport || !state.selectedReportStock || state.reportAnalyzing) {
        return;
    }
    state.reportAnalyzing = true;
    analyzeFinancialReportBtn.disabled = true;
    financialReportOutput.innerHTML = `<p>正在下载财报 PDF、提取正文并进行 AI 分析，请耐心等待...</p><p>长财报将选取重点内容，完成后会显示读取范围和 PDF 页码。</p>`;
    const report = state.selectedFinancialReport;
    try {
        // 后端会再次校验这只股票是否属于当前账号收藏，前端传 favoriteNote 只是为了增强分析上下文。
        const response = await request("/api/financial-reports/analyze", {
            method: "POST",
            body: JSON.stringify({
                ...report,
                favoriteNote: state.selectedReportStock.note || ""
            })
        });
        renderFinancialReportOutput(response.data);
        await loadFinancialReportAnalyses();
        showToast("财报分析报告已保存");
    } catch (error) {
        financialReportOutput.innerHTML = `<p>${escapeHtml(error.message || "财报分析失败")}</p>`;
        showToast(error.message || "财报分析失败");
    } finally {
        state.reportAnalyzing = false;
        analyzeFinancialReportBtn.disabled = false;
    }
}

// 展示分析结果、来源类型和原文链接。
function renderFinancialReportOutput(report) {
    financialReportOutput.innerHTML = `
        <h3>${escapeHtml(report.companyName)} / ${escapeHtml(report.reportType)}</h3>
        <div class="pill">${escapeHtml(report.analysisSource || "AI")}</div>
        <p>${formatFinancialReportContent(report)}</p>
        ${report.pdfUrl ? `<a href="${escapeHtml(report.pdfUrl)}" target="_blank" rel="noreferrer">查看原始 PDF</a>` : ""}
    `;
    financialReportOutput.scrollTop = 0;
}

// 先转义正文，再把 PDF 页码转换为原文定位链接。
function formatFinancialReportContent(report) {
    const content = escapeHtml(report.content || "").replace(/\n/g, "<br>");
    const pdfUrl = report.pdfUrl || "";
    if (!/^https:\/\/static\.cninfo\.com\.cn\/finalpage\/\d{4}-\d{2}-\d{2}\/[a-z0-9_-]+\.pdf$/i.test(pdfUrl)) {
        return content;
    }
    return content.replace(/【PDF第(\d+)页】/g, (label, page) =>
        `<a href="${escapeHtml(pdfUrl)}#page=${page}" target="_blank" rel="noopener noreferrer">${label}</a>`);
}

// 加载当前用户的财报分析历史。
async function loadFinancialReportAnalyses() {
    try {
        const response = await request("/api/financial-reports/analyses");
        renderFinancialReportHistory(response.data || []);
    } catch (error) {
        financialReportHistory.innerHTML = `<div class="empty-state">${escapeHtml(error.message || "财报分析历史加载失败")}</div>`;
    }
}

// 渲染历史报告及查看按钮。
function renderFinancialReportHistory(items) {
    if (!items.length) {
        financialReportHistory.innerHTML = `<div class="empty-state">还没有财报分析报告。</div>`;
        return;
    }
    financialReportHistory.innerHTML = items.map((item) => `
        <article class="financial-report-history-item">
            <div class="history-item-header">
                <div>
                    <strong>${escapeHtml(item.companyName)} / ${escapeHtml(item.reportType)}</strong>
                    <div class="pill">${escapeHtml(item.symbol)} / ${escapeHtml(item.analysisSource || "AI")}</div>
                </div>
            </div>
            <small>${escapeHtml(item.reportTitle || "")}</small>
            <p>${escapeHtml(item.content || "")}</p>
        </article>
    `).join("");
}

// 渲染收藏卡片及删除按钮。
function renderFavorites(items) {
    if (!items.length) {
        favoriteList.innerHTML = `<div class="empty-state">还没有收藏标的，先加一个你想长期跟踪的方向。</div>`;
        return;
    }

    favoriteList.innerHTML = items.map((item) => `
        <article class="collection-item">
            <div class="collection-item-header">
                <div>
                    <strong>${escapeHtml(item.name)}</strong>
                    <div class="pill">${escapeHtml(item.category)} / ${escapeHtml(item.symbol)}</div>
                </div>
                <button class="delete-btn" onclick="deleteFavorite(${item.id})">删除</button>
            </div>
            <p>${escapeHtml(item.note || "暂无备注")}</p>
        </article>
    `).join("");
}

// 删除收藏，并同步统计和财报页状态。
async function deleteFavorite(id) {
    try {
        await request(`/api/favorites/${id}`, {method: "DELETE"});
        showToast("已删除收藏");
        loadFavorites();
        loadDashboard();
        invalidateFinancialReportWorkspace();
    } catch (error) {
        showToast(error.message || "删除失败");
    }
}

// 为新会话显示原有欢迎消息。
function renderWelcomeMessage() {
    appendMessage("agent", "你好，我是 FinMate。你可以直接问财经概念、财报阅读方法，或者把重点标的加入收藏。");
}

// 向普通聊天区添加一条消息。
function appendMessage(role, content) {
    appendBoardMessage(chatBoard, "chat-message", role, content);
}

// 向市场追问区添加消息并返回节点。
function appendMarketAssistantMessage(role, content) {
    return appendBoardMessage(marketAssistantBoard, "market-assistant-message", role, content);
}

// 两个聊天区共用安全的纯文本渲染；返回节点供市场追问更新占位消息。
function appendBoardMessage(board, className, role, content) {
    const item = document.createElement("div");
    item.className = `${className} ${role}`;
    item.textContent = content;
    board.appendChild(item);
    board.scrollTop = board.scrollHeight;
    return item;
}

// 显示当前会话 ID。
function updateMemoryBadge() {
    memoryBadge.textContent = state.chatSessionId ? `会话ID: ${state.chatSessionId}` : "会话ID：-";
}

// 统一发送 JSON 请求、处理登录失效和后端错误。
async function request(url, options = {}) {
    const suppressAuthRedirect = options.suppressAuthRedirect === true;
    const fetchOptions = {...options};
    delete fetchOptions.suppressAuthRedirect;

    const response = await fetch(url, {
        headers: {
            "Content-Type": "application/json"
        },
        ...fetchOptions
    });

    let result;
    try {
        result = await response.json();
    } catch (error) {
        result = {success: false, message: "请求失败"};
    }

    if (response.status === 401 && !suppressAuthRedirect) {
        showAuth();
    }
    if (!response.ok || !result.success) {
        throw new Error(result.message || "请求失败");
    }
    return result;
}

// 显示短暂提示，并清理上一次的关闭计时器。
function showToast(message) {
    toast.textContent = message;
    toast.classList.add("show");
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(() => toast.classList.remove("show"), 2400);
}

// 为每个用户生成独立的最近会话缓存键。
function lastSessionStorageKey() {
    return `finance-agent-last-chat-session-${state.currentUser.id}`;
}

// 读取当前用户上一次选中的会话。
function loadLastSessionId() {
    if (!state.currentUser) {
        return null;
    }
    const saved = window.localStorage.getItem(lastSessionStorageKey());
    return saved ? Number(saved) : null;
}

// 保存当前用户选中的会话 ID。
function persistLastSessionId(chatSessionId) {
    if (!state.currentUser || !chatSessionId) {
        return;
    }
    window.localStorage.setItem(lastSessionStorageKey(), String(chatSessionId));
}

// 按现有中文格式显示时间。
function formatDateTime(value) {
    if (!value) {
        return "";
    }
    return new Date(value).toLocaleString("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    });
}

// 转义动态内容，避免将用户文本当作 HTML 执行。
function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

window.deleteFavorite = deleteFavorite;
