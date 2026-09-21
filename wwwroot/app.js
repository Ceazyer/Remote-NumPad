(() => {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(`${protocol}//${location.host}/ws`);

  document.querySelectorAll("button[data-key]").forEach((button) => {
    button.addEventListener("click", () => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(button.dataset.key);
      }
    });
  });
})();

