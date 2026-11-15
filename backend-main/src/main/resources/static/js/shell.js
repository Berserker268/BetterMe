var btn = document.getElementById("avatarBtn");
var menu = document.getElementById("avatarMenu");
if (btn && menu) {
  btn.addEventListener("click", function (e) {
    e.stopPropagation();
    menu.classList.toggle("open");
  });
  document.addEventListener("click", function () { menu.classList.remove("open"); });
}