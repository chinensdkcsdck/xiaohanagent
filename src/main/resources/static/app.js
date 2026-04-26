const moduleDefs = [
  {
    key: "booking",
    title: "挂号与候补",
    subtitle: "先查号源，再挂号或加入候补",
    apis: [
      { name: "预约挂号", method: "POST", url: "/api/appointments/book", fields: ["department", "date", "time", "doctorName"] },
      { name: "取消预约", method: "POST", url: "/api/appointments/cancel", fields: ["department", "date", "time", "doctorName"] },
      { name: "改约", method: "POST", url: "/api/appointments/reschedule", fields: ["department", "oldDate", "oldTime", "newDate", "newTime", "doctorName"] },
      { name: "号源查询", method: "GET", url: "/api/appointments/availability", fields: ["department", "date", "time", "doctorName"] },
      { name: "门诊候补", method: "POST", url: "/api/waitlists/appointments", fields: ["bizType", "date", "time", "doctorName", "note"] },
      { name: "检查候补", method: "POST", url: "/api/waitlists/exams", fields: ["bizType", "date", "time", "doctorName", "note"] }
    ]
  },
  {
    key: "exam",
    title: "检查与复诊",
    subtitle: "检查预约、取消与复诊确认",
    apis: [
      { name: "检查预约", method: "POST", url: "/api/exams/book", fields: ["examType", "date", "time", "doctorName"] },
      { name: "检查取消", method: "POST", url: "/api/exams/cancel", fields: ["examType", "date", "time", "doctorName"] },
      { name: "复诊计划创建", method: "POST", url: "/api/followups/plans", fields: ["department", "date", "time", "doctorName", "reason"] },
      { name: "复诊确认", method: "POST", url: "/api/followups/plans/{planId}/confirm", fields: ["planId"], plain: true }
    ]
  },
  {
    key: "knowledge",
    title: "就诊准备与知识",
    subtitle: "维护准备指引与知识库同步",
    apis: [
      { name: "就诊准备查询", method: "GET", url: "/api/preparations", fields: ["department", "examType"] },
      { name: "就诊准备维护", method: "POST", url: "/api/preparations", fields: ["department", "examType", "prepContent", "riskNotice"] },
      { name: "流程知识同步", method: "POST", url: "/api/knowledge/ingest-workflow", fields: [], plain: true },
      { name: "项目知识同步", method: "POST", url: "/api/knowledge/ingest-project-docs", fields: [], plain: true }
    ]
  },
  {
    key: "advanced",
    title: "扩展业务",
    subtitle: "分诊、费用、工单和运营看板",
    apis: [
      { name: "分诊导诊", method: "POST", url: "/api/triage", fields: ["symptoms", "age", "chronicDiseases"] },
      { name: "检查提醒", method: "POST", url: "/api/exam-journey/reminder", fields: ["examType", "appointmentTime"] },
      { name: "慢病随访", method: "POST", url: "/api/chronic/followup-plan", fields: ["diseaseType", "latestIndicators", "medicationStatus"] },
      { name: "费用预估", method: "POST", url: "/api/cost/estimate", fields: ["department", "examType", "insuranceType"] },
      { name: "用药安全", method: "POST", url: "/api/medication/safety", fields: ["medications", "allergies", "conditions"] },
      { name: "报告解读", method: "POST", url: "/api/reports/explain", fields: ["reportText"] },
      { name: "工单创建", method: "POST", url: "/api/tickets", fields: ["category", "content", "priority"] },
      { name: "运营看板", method: "GET", url: "/api/operations/daily-summary", fields: [] }
    ]
  }
];

const labels = {
  department: "科室",
  date: "日期",
  time: "时段",
  doctorName: "医生",
  oldDate: "原日期",
  oldTime: "原时段",
  newDate: "新日期",
  newTime: "新时段",
  bizType: "业务类型",
  note: "备注",
  examType: "检查项目",
  planId: "计划ID",
  prepContent: "准备内容",
  riskNotice: "风险提示",
  symptoms: "症状描述",
  age: "年龄",
  chronicDiseases: "慢病史",
  appointmentTime: "预约时间",
  diseaseType: "慢病类型",
  latestIndicators: "最新指标",
  medicationStatus: "用药情况",
  insuranceType: "医保类型",
  medications: "当前用药",
  allergies: "过敏史",
  conditions: "既往病史",
  reportText: "报告原文",
  category: "工单分类",
  content: "工单内容",
  priority: "优先级",
  reason: "复诊原因"
};

const longTextFields = new Set(["prepContent", "riskNotice", "symptoms", "latestIndicators", "medicationStatus", "medications", "allergies", "conditions", "reportText", "content"]);
const dateFields = new Set(["date", "oldDate", "newDate"]);
const numberFields = new Set(["age", "planId"]);
const timeFields = new Set(["time", "oldTime", "newTime"]);

const state = {
  user: null,
  activeModuleKey: moduleDefs[0].key,
  activeApiName: moduleDefs[0].apis[0].name,
  forms: {},
  chatMessages: [{ role: "bot", text: "请先登录后开始挂号或咨询。" }]
};

initForms();
loadMe().finally(render);

function initForms() {
  moduleDefs.forEach((m) => {
    m.apis.forEach((api) => {
      state.forms[api.name] = {};
      api.fields.forEach((field) => {
        state.forms[api.name][field] = "";
      });
    });
  });
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function currentModule() {
  return moduleDefs.find((m) => m.key === state.activeModuleKey) || moduleDefs[0];
}

function currentApi() {
  const module = currentModule();
  return module.apis.find((a) => a.name === state.activeApiName) || module.apis[0];
}

function setActiveModule(key) {
  const mod = moduleDefs.find((m) => m.key === key);
  if (!mod) return;
  state.activeModuleKey = key;
  state.activeApiName = mod.apis[0].name;
  render();
}

function setActiveApi(name) {
  state.activeApiName = name;
  render();
}

function render() {
  const app = document.getElementById("app");
  app.innerHTML = "";

  const page = el("div", "page");
  page.append(buildTopBar());

  if (!state.user) {
    page.append(buildGuestView());
  } else {
    page.append(buildMainView());
  }

  app.append(page);
}

function buildTopBar() {
  const top = el("header", "topbar");
  const brand = el("div", "brand");
  brand.append(el("div", "brand-mark"));
  const text = el("div", "brand-text");
  text.append(el("div", "brand-title", "小涵医疗预约中心"));
  text.append(el("div", "brand-subtitle", state.user ? `已登录：${state.user.username}` : "请登录后开始预约"));
  brand.append(text);
  top.append(brand);

  if (state.user) {
    const right = el("div", "top-actions");
    const identity = el("div", "identity", `账号 ${state.user.account} · 用户ID ${state.user.userId}`);
    const logoutBtn = el("button", "btn btn-ghost", "退出登录");
    logoutBtn.onclick = logout;
    right.append(identity, logoutBtn);
    top.append(right);
  }

  return top;
}

function buildGuestView() {
  const wrap = el("section", "guest");

  const intro = el("div", "hero");
  intro.append(el("h2", "hero-title", "从预约到就诊，全流程一站式"));
  intro.append(el("p", "hero-copy", "先登录，再选择挂号模块，按步骤完成查号源、预约、改约、候补。"));

  const steps = el("div", "steps");
  ["1. 登录账号", "2. 选择业务模块", "3. 填写信息并提交"].forEach((s) => {
    steps.append(el("div", "step", s));
  });
  intro.append(steps);

  const login = el("div", "login-card");
  login.append(el("h3", "card-title", "用户登录"));
  login.append(el("p", "card-subtitle", "演示账号：zhangsan / 123456"));

  const account = document.createElement("input");
  account.id = "login-account";
  account.className = "field-input";
  account.placeholder = "请输入账号";

  const password = document.createElement("input");
  password.id = "login-password";
  password.className = "field-input";
  password.placeholder = "请输入密码";
  password.type = "password";

  const msg = el("div", "login-msg");

  const loginBtn = el("button", "btn btn-primary", "登录");
  loginBtn.onclick = async () => {
    const body = { account: account.value.trim(), password: password.value.trim() };
    if (!body.account || !body.password) {
      msg.textContent = "请输入账号和密码";
      return;
    }
    try {
      const res = await fetch("/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      const data = await res.json();
      if (!data.success) {
        msg.textContent = data.message || "登录失败";
        return;
      }
      state.user = data.user;
      state.chatMessages = [{ role: "bot", text: `欢迎回来，${state.user.username}。请先在右侧选择“挂号与候补”。` }];
      render();
    } catch (e) {
      msg.textContent = `登录失败：${e.message}`;
    }
  };

  login.append(account, password, loginBtn, msg);
  wrap.append(intro, login);

  return wrap;
}

function buildMainView() {
  const grid = el("section", "main-grid");
  grid.append(buildChatPanel(), buildBizPanel());
  return grid;
}

function buildChatPanel() {
  const panel = el("section", "panel chat-panel");
  panel.append(el("h3", "panel-title", "智能导诊对话"));

  const board = el("div", "chat-board");
  state.chatMessages.forEach((m) => {
    const line = el("div", `msg ${m.role}`);
    line.append(el("span", "role", m.role === "user" ? "你" : "小涵"));
    line.append(document.createTextNode(m.text));
    board.append(line);
  });

  const inputWrap = el("div", "chat-input-wrap");
  const input = document.createElement("textarea");
  input.id = "chat-message";
  input.className = "field-input";
  input.rows = 4;
  input.placeholder = "例如：我想预约下周一上午内科";
  const sendBtn = el("button", "btn btn-primary", "发送对话");
  sendBtn.onclick = () => sendChat(input.value);
  inputWrap.append(input, sendBtn);

  panel.append(board, inputWrap);
  return panel;
}

function buildBizPanel() {
  const panel = el("section", "panel biz-panel");
  panel.append(el("h3", "panel-title", "业务接口"));

  const split = el("div", "biz-split");
  split.append(buildModuleList(), buildApiDetail());

  panel.append(split);
  return panel;
}

function buildModuleList() {
  const left = el("aside", "module-list");
  moduleDefs.forEach((m) => {
    const card = el("button", `module-card${m.key === state.activeModuleKey ? " active" : ""}`);
    card.onclick = () => setActiveModule(m.key);
    card.append(el("div", "module-title", m.title));
    card.append(el("div", "module-subtitle", m.subtitle));
    left.append(card);
  });
  return left;
}

function buildApiDetail() {
  const right = el("main", "api-detail");
  const module = currentModule();
  const api = currentApi();

  const nav = el("div", "api-nav");
  module.apis.forEach((a) => {
    const chip = el("button", `api-chip${a.name === api.name ? " active" : ""}`, a.name);
    chip.onclick = () => setActiveApi(a.name);
    nav.append(chip);
  });

  const meta = el("div", "api-meta", `${api.method} ${api.url}`);
  const form = el("div", "form-grid");

  api.fields.forEach((field) => {
    const item = el("div", "field");
    item.append(el("label", "field-label", labels[field] || field));

    let control;
    if (timeFields.has(field)) {
      control = document.createElement("select");
      control.className = "field-input";
      ["", "上午", "下午"].forEach((v) => {
        const option = document.createElement("option");
        option.value = v;
        option.textContent = v || "请选择";
        if (state.forms[api.name][field] === v) option.selected = true;
        control.append(option);
      });
    } else if (longTextFields.has(field)) {
      control = document.createElement("textarea");
      control.className = "field-input";
      control.rows = 3;
      control.placeholder = `请输入${labels[field] || field}`;
      control.value = state.forms[api.name][field] || "";
    } else {
      control = document.createElement("input");
      control.className = "field-input";
      control.placeholder = `请输入${labels[field] || field}`;
      control.value = state.forms[api.name][field] || "";
      if (dateFields.has(field)) control.type = "date";
      if (numberFields.has(field)) control.type = "number";
    }

    control.oninput = (e) => {
      state.forms[api.name][field] = e.target.value;
    };

    item.append(control);
    form.append(item);
  });

  const actions = el("div", "actions");
  const runBtn = el("button", "btn btn-accent", "确定");
  const result = el("pre", "result-box");
  result.id = "api-result";

  runBtn.onclick = async () => {
    result.textContent = "请求中...";
    try {
      const payload = {};
      Object.entries(state.forms[api.name]).forEach(([k, v]) => {
        if (v !== "" && v !== null && v !== undefined) payload[k] = v;
      });

      let url = api.url;
      if (url.includes("{planId}")) {
        url = url.replace("{planId}", encodeURIComponent(payload.planId || ""));
      }

      const options = { method: api.method, headers: { Accept: "application/json, text/plain, */*" } };

      if (api.method === "GET") {
        const q = new URLSearchParams(payload).toString();
        if (q) url += `?${q}`;
      } else if (!api.plain) {
        options.headers["Content-Type"] = "application/json";
        options.body = JSON.stringify(payload);
      }

      const res = await fetch(url, options);
      const text = await res.text();
      let pretty = text;
      try {
        pretty = JSON.stringify(JSON.parse(text), null, 2);
      } catch (_) {
        // keep raw text
      }
      result.textContent = `状态码: ${res.status}\n${pretty}`;
    } catch (e) {
      result.textContent = `请求失败\n${e.message}`;
    }
  };

  actions.append(runBtn);
  right.append(nav, meta, form, actions, result);
  return right;
}

async function sendChat(rawText) {
  const message = (rawText || "").trim();
  if (!message) return;

  state.chatMessages.push({ role: "user", text: message });
  const bot = { role: "bot", text: "" };
  state.chatMessages.push(bot);
  render();

  try {
    const res = await fetch("/xiaohan/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
      body: JSON.stringify({ message })
    });

    if (!res.ok || !res.body) {
      bot.text = `对话失败，状态码 ${res.status}`;
      render();
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let cache = "";

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      cache += decoder.decode(value, { stream: true });
      const lines = cache.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
      cache = lines.pop() || "";
      lines.forEach((line) => {
        if (line.startsWith("data:")) {
          bot.text += line.slice(5).trimStart();
        }
      });
      render();
    }
  } catch (e) {
    bot.text = `对话失败：${e.message}`;
    render();
  }
}

async function loadMe() {
  try {
    const res = await fetch("/auth/me");
    const data = await res.json();
    if (data.success) {
      state.user = data.user;
      state.chatMessages = [{ role: "bot", text: `欢迎回来，${state.user.username}。` }];
    }
  } catch (_) {
    // ignore
  }
}

async function logout() {
  try {
    await fetch("/auth/logout", { method: "POST" });
  } catch (_) {
    // ignore
  }
  state.user = null;
  state.chatMessages = [{ role: "bot", text: "你已退出登录。" }];
  render();
}
