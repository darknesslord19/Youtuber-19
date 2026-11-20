/* Darkness Lord: sayfa yüklenmeden ÖNCE çalışır. YouTube kendi kodunu yüklerken
   pause fonksiyonunu ve görünürlük bilgisini kopyalayıp saklıyor; bu yüzden
   engelleme en başta kurulmalı. */
(function(){
if (window.__ytproEarly) return; window.__ytproEarly = 1;
function bg(){ try { return localStorage.getItem("bgplay") == "true"; } catch (e) { return false; } }
window.ytproBgAudio = false;
window.ytproEarlyLog = [];
var oPause = HTMLMediaElement.prototype.pause;
HTMLMediaElement.prototype.pause = function(){
  if (window.ytproBgAudio && !window.PIPause) {
    try {
      var st = (new Error().stack || "").split("\n").slice(2, 4).map(function(s){ return s.trim().replace(/https?:\/\/[^\s)]*\//g, "").slice(0, 60); }).join(" < ");
      window.ytproEarlyLog.push("erken engel: " + st);
      if (window.ytproEarlyLog.length > 20) window.ytproEarlyLog.shift();
    } catch (e) {}
    return;
  }
  return oPause.apply(this, arguments);
};
try {
  var dp = Document.prototype;
  var vs = Object.getOwnPropertyDescriptor(dp, "visibilityState");
  var hd = Object.getOwnPropertyDescriptor(dp, "hidden");
  Object.defineProperty(document, "visibilityState", { configurable: true, get: function(){ return bg() ? "visible" : vs.get.call(document); } });
  Object.defineProperty(document, "hidden", { configurable: true, get: function(){ return bg() ? false : hd.get.call(document); } });
  var hf = document.hasFocus.bind(document);
  document.hasFocus = function(){ return bg() ? true : hf(); };
} catch (e) {}
["visibilitychange", "webkitvisibilitychange", "pagehide", "freeze"].forEach(function(ev){
  var stop = function(e){ if (bg()) e.stopImmediatePropagation(); };
  window.addEventListener(ev, stop, true);
  document.addEventListener(ev, stop, true);
});
})();
