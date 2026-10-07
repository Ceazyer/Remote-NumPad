(() => {
  const connectionLabel = document.querySelector("[data-connection]");
  let socket;
  let reconnectTimer;

  const applyTheme = (theme) => {
    const dark = theme === "dark";
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    const toggle = document.querySelector("[data-theme-toggle]");
    const label = dark ? "切换到白色主题" : "切换到黑色主题";
    toggle.setAttribute("aria-label", label);
    toggle.title = label;
    toggle.querySelector("use").setAttribute("href", dark ? "#icon-sun" : "#icon-moon");
    document.querySelector('meta[name="theme-color"]').content = dark ? "#263142" : "#f2f3f7";
  };
  let savedTheme = "light";
  try { savedTheme = localStorage.getItem("remote-numpad-theme") || "light"; } catch { /* Storage can be disabled. */ }
  applyTheme(savedTheme);
  document.querySelector("[data-theme-toggle]").addEventListener("click", () => {
    const theme = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    applyTheme(theme);
    try { localStorage.setItem("remote-numpad-theme", theme); } catch { /* Input still works without storage. */ }
  });

  const updateConnection = (text, state) => {
    connectionLabel.textContent = text;
    connectionLabel.classList.toggle("is-connected", state === "connected");
    connectionLabel.classList.toggle("is-disconnected", state === "disconnected");
  };

  const connect = () => {
    window.clearTimeout(reconnectTimer);
    const previous = socket;
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const activeSocket = new WebSocket(`${protocol}//${location.host}/ws`);
    socket = activeSocket;
    previous?.close();
    updateConnection("连接中…", "connecting");

    activeSocket.addEventListener("open", () => {
      if (socket === activeSocket) updateConnection("已连接", "connected");
    });
    activeSocket.addEventListener("close", () => {
      if (socket !== activeSocket) return;
      updateConnection("等待连接", "disconnected");
      window.clearTimeout(reconnectTimer);
      reconnectTimer = window.setTimeout(connect, 1200);
    });
    activeSocket.addEventListener("error", () => {
      if (socket === activeSocket) updateConnection("连接异常", "disconnected");
    });
  };

  const tabs = [...document.querySelectorAll("[data-page-tab]")];
  const panels = [...document.querySelectorAll("[data-page]")];

  const selectTool = (name) => {
    tabs.forEach((tab) => {
      const selected = tab.dataset.pageTab === name;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
    });
    panels.forEach((panel) => {
      panel.hidden = panel.dataset.page !== name;
    });
  };

  tabs.forEach((tab, index) => {
    tab.addEventListener("click", () => selectTool(tab.dataset.pageTab));
    tab.addEventListener("keydown", (event) => {
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      event.preventDefault();
      const delta = event.key === "ArrowRight" ? 1 : -1;
      const next = tabs[(index + delta + tabs.length) % tabs.length];
      selectTool(next.dataset.pageTab);
      next.focus();
    });
  });

  const settings = document.querySelector("[data-settings-dialog]");
  document.querySelector("[data-server-address]").textContent = location.host;
  document.querySelector("[data-settings-open]").addEventListener("click", () => settings.showModal());
  document.querySelector("[data-settings-close]").addEventListener("click", () => settings.close());
  document.querySelector("[data-reconnect]").addEventListener("click", () => {
    settings.close();
    connect();
  });

  document.querySelectorAll("button[data-command]").forEach((button) => {
    button.addEventListener("click", () => {
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send(button.dataset.command);
      }
    });
  });

  connect();
})();
