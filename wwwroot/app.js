(() => {
  const connectionLabel = document.querySelector("[data-connection]");
  let socket;
  let reconnectTimer;

  const updateConnection = (text, state) => {
    connectionLabel.textContent = text;
    connectionLabel.classList.toggle("is-connected", state === "connected");
    connectionLabel.classList.toggle("is-disconnected", state === "disconnected");
  };

  const connect = () => {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    socket = new WebSocket(`${protocol}//${location.host}/ws`);
    updateConnection("连接中…", "connecting");

    socket.addEventListener("open", () => updateConnection("已连接", "connected"));
    socket.addEventListener("close", () => {
      updateConnection("等待连接", "disconnected");
      window.clearTimeout(reconnectTimer);
      reconnectTimer = window.setTimeout(connect, 1200);
    });
    socket.addEventListener("error", () => updateConnection("连接异常", "disconnected"));
  };

  document.querySelectorAll("button[data-command]").forEach((button) => {
    button.addEventListener("click", () => {
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send(button.dataset.command);
      }
    });
  });

  connect();
})();
