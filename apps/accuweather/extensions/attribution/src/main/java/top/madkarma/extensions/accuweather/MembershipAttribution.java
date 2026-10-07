// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.extensions.accuweather;

import android.util.Log;
import android.webkit.WebView;

import org.json.JSONObject;

@SuppressWarnings("unused")
public final class MembershipAttribution {
    private MembershipAttribution() {
    }

    public static void install(WebView webView, String attribution) {
        if (webView == null) return;

        try {
            String script = String.format("""
                (() => {
                  if (window.reseamMembershipAttribution) return;
                
                  const attribution = %s;
                  const original = 'Enjoy no ads, advanced forecasts & priority alerts';
                  const matches = element => element.textContent.replace(/\\s+/g, ' ').trim() === original;
                
                  function replaceMessage(root) {
                    if (!root || root.nodeType !== Node.ELEMENT_NODE) return;
                
                    if (matches(root)) {
                      root.textContent = attribution;
                      return;
                    }
                
                    const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
                    while (walker.nextNode()) {
                      if (!matches(walker.currentNode)) continue;
                
                      walker.currentNode.textContent = attribution;
                      return;
                    }
                  }
                
                  const observer = new MutationObserver(records => {
                    for (const record of records) {
                      if (record.type === 'characterData') replaceMessage(record.target.parentElement);
                      else {
                        for (const node of record.addedNodes) {
                          replaceMessage(node.nodeType === Node.TEXT_NODE ? node.parentElement : node);
                        }
                      }
                    }
                  });
                
                  observer.observe(document.documentElement, {childList: true, characterData: true, subtree: true});
                
                  window.reseamMembershipAttribution = observer;
                
                  replaceMessage(document.body);
                })();
                """, JSONObject.quote(attribution));

            webView.evaluateJavascript(script, null);
        } catch (RuntimeException exception) {
            Log.w("ReseamAccuWeather", "Could not install membership attribution", exception);
        }
    }
}
