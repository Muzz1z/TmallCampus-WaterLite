(function () {
  'use strict';
  var allowedPath = '/app/tmall-xiaoyuan/tmxy-m-share/laundry/waterDetail';
  var exactPage = function () {
    return location.origin === 'https://share.confong.cn' && location.pathname === allowedPath;
  };
  if (!exactPage()) return { applied: false, reason: 'outside-water-page' };
  if (window.__waterliteUiV3) { window.__waterliteUiV3.refresh(); return { applied: true, ready:window.__waterliteUiV3.ready, complete:document.readyState==='complete', existing: true }; }

  var styleId = 'waterlite-water-style-v3';
  var toggleId = 'waterlite-water-toggle-v3';
  var mode = 'simple';
  var state = {ready:false,refresh:refresh,setMode:function(value){mode=value==='original'?'original':'simple';refresh();}};
  var readyQueued = false;
  var css = [
    'body[data-waterlite-simple="1"]{background:#f6f9fc!important;}',
    'body[data-waterlite-simple="1"] [class*="waterContainer--"]{background:#f6f9fc!important;min-height:100vh!important;}',
    'body[data-waterlite-simple="1"] [class*="navigator--"]{background:#1a6fe6!important;}',
    'body[data-waterlite-simple="1"] [class*="showerBody--"]{min-height:0!important;height:auto!important;background:transparent!important;}',
    'body[data-waterlite-simple="1"] [class*="waterWallet--"]{height:auto!important;padding:16px 16px 12px!important;background:transparent!important;margin:0!important;}',
    'body[data-waterlite-simple="1"] [class*="walletBox--"]{background:#fff!important;border:1px solid #e7edf5!important;border-radius:16px!important;padding:14px!important;margin:0!important;box-shadow:none!important;height:auto!important;}',
    'body[data-waterlite-simple="1"] [class*="walletName--"]{font-size:12px!important;overflow-wrap:anywhere;}',
    'body[data-waterlite-simple="1"] [class*="amount--"]{font-size:26px!important;}',
    'body[data-waterlite-simple="1"] [class*="deviceContent--"]{background:transparent!important;padding:0 16px 24px!important;margin:0!important;border-radius:0!important;height:auto!important;min-height:0!important;}',
    'body[data-waterlite-simple="1"] [class*="topAddress--"]{background:#fff!important;border:1px solid #e7edf5!important;border-radius:16px!important;padding:16px!important;display:flex!important;flex-direction:column!important;align-items:flex-start!important;gap:10px!important;height:auto!important;}',
    'body[data-waterlite-simple="1"] [class*="addressDeviceInfo--"]{flex-wrap:wrap!important;height:auto!important;line-height:1.5!important;}',
    'body[data-waterlite-simple="1"] [class*="centerDeviceBox--"]{background:transparent!important;padding:0!important;margin:16px 0 0!important;min-height:0!important;height:auto!important;}',
    'body[data-waterlite-simple="1"] [class*="deviceImageBox--"],body[data-waterlite-simple="1"] [class*="waterCouponContainer--"],body[data-waterlite-simple="1"] [class*="ruleTitle--"],body[data-waterlite-simple="1"] [class*="nativeAdBox--"]{display:none!important;}',
    'body[data-waterlite-simple="1"] [class*="bottomButtonBox--"]{background:#fff!important;padding:16px!important;margin:16px 0 0!important;border:1px solid #e7edf5!important;border-radius:16px!important;box-sizing:border-box!important;}',
    'body[data-waterlite-simple="1"] [class*="unlockButton--"]{width:100%!important;min-height:52px!important;background:#1a6fe6!important;border-radius:14px!important;box-shadow:none!important;}',
    'body[data-waterlite-simple="1"] [class*="tips--"]{font-size:12px!important;line-height:1.6!important;margin-top:12px!important;}',
    '#'+toggleId+'{position:absolute;right:14px;bottom:12px;padding:6px 10px;border:1px solid rgba(255,255,255,.55);border-radius:10px;background:rgba(255,255,255,.12);color:#fff;font:inherit;font-size:12px;line-height:18px;z-index:2;}'
  ].join('\n');

  function refresh() {
    if (!document.body) return;
    if (!exactPage()) { document.body.removeAttribute('data-waterlite-simple'); state.ready=false; return; }
    if (!document.getElementById(styleId)) {
      var style = document.createElement('style'); style.id = styleId; style.textContent = css; (document.head||document.documentElement).appendChild(style);
    }
    document.body.setAttribute('data-waterlite-simple', mode === 'simple' ? '1' : '0');
    var root = document.querySelector('[class*="waterContainer--"]');
    if (!root || !document.querySelector('[class*="walletBox--"]') || !document.querySelector('[class*="bottomButtonBox--"]')) return;
    var nav = root.querySelector('[class*="navigator--"]');
    if (nav && !document.getElementById(toggleId)) {
      var button = document.createElement('button'); button.id = toggleId; button.type = 'button';
      button.addEventListener('click', function (event) {
        event.stopPropagation(); mode = mode === 'simple' ? 'original' : 'simple';
        refresh();
      });
      nav.appendChild(button);
    }
    var toggle = document.getElementById(toggleId);
    if (toggle) {
      var text = mode === 'simple' ? '原版' : '简洁';
      if (toggle.textContent !== text) toggle.textContent = text;
      toggle.setAttribute('aria-label', mode === 'simple' ? '显示原版界面、优惠券和认证入口' : '显示简洁设备界面');
    }
    if (!state.ready && !readyQueued) {
      readyQueued=true;
      requestAnimationFrame(function(){requestAnimationFrame(function(){
        readyQueued=false;
        if(exactPage() && document.getElementById(styleId) && document.querySelector('[class*="walletBox--"]') && document.querySelector('[class*="bottomButtonBox--"]')) state.ready=true;
      });});
    }
  }
  var queued = false;
  var observer = new MutationObserver(function () {
    if (queued) return; queued = true;
    requestAnimationFrame(function () { queued = false; refresh(); });
  });
  observer.observe(document.documentElement, {childList:true, subtree:true});
  window.__waterliteUiV3 = state;
  refresh();
  return {applied:!!document.body && document.body.getAttribute('data-waterlite-simple') === '1',ready:state.ready,complete:document.readyState==='complete',mode:mode};
})();
