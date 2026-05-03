(ns eventpulse.views)

(defn layout [title body]
  (str "<!doctype html><html lang=\"en\"><head>"
       "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
       "<title>" title "</title>"
       "<link rel=\"stylesheet\" href=\"/styles.css?v=20260503-2\">"
       "</head><body>" body "<script src=\"/app.js?v=20260503-2\"></script></body></html>"))

(defn dashboard [authenticated?]
  (layout
   "Clojure EventPulse"
   (str
    "<main class=\"shell\">"
    "<header class=\"topbar\"><div><p class=\"eyebrow\">Clojure Event Stream Inspector</p>"
    "<h1>Clojure EventPulse</h1></div><nav><a href=\"/docs\">Docs</a><a href=\"/health\">Health</a>"
    (if authenticated?
      "<a href=\"/logout\">Logout</a>"
      "<a href=\"/login\">Admin login</a>")
    "</nav></header>"
    (if authenticated?
      "<section class=\"auth-banner admin\"><strong>Admin mode</strong><span>Real event data is visible in this session.</span></section>"
      "<section class=\"auth-banner demo\"><strong>Public demo mode</strong><span>Showing mock data. Real VPS events require admin login.</span></section>")
    "<section class=\"status-line\"><span id=\"streamStatus\" class=\"pill muted\">Connecting</span><span id=\"lastRefresh\">Loading dashboard data</span></section>"
    "<section class=\"cards\" aria-label=\"Event summary\">"
    "<article><span>Total</span><strong id=\"statTotal\">0</strong></article>"
    "<article><span>Last 5m</span><strong id=\"stat5\">0</strong></article>"
    "<article><span>Last 15m</span><strong id=\"stat15\">0</strong></article>"
    "<article><span>Last 60m</span><strong id=\"stat60\">0</strong></article>"
    "<article><span>Warnings</span><strong id=\"statWarnings\">0</strong></article>"
    "<article><span>Errors</span><strong id=\"statErrors\">0</strong></article>"
    "<article><span>Sources</span><strong id=\"statSources\">0</strong></article>"
    "<article><span>Last event</span><strong id=\"statLast\">-</strong></article>"
    "</section>"
    "<section class=\"panel filters\"><div><label>Source<input id=\"filterSource\" placeholder=\"any source\"></label></div>"
    "<div><label>Level<select id=\"filterLevel\"><option value=\"\">any level</option><option>debug</option><option>info</option><option>warning</option><option>error</option><option>critical</option></select></label></div>"
    "<div><label>Type<input id=\"filterType\" placeholder=\"any type\"></label></div>"
    "<div><label>Limit<input id=\"filterLimit\" type=\"number\" min=\"1\" max=\"500\" value=\"100\"></label></div>"
    "<button id=\"applyFilters\">Apply</button><button id=\"clearFilters\" class=\"secondary\">Clear</button></section>"
    "<section class=\"grid\">"
    "<article class=\"panel\"><div class=\"panel-head\"><h2>Live event stream</h2><span id=\"eventCountLabel\">0 events</span></div>"
    "<div id=\"errorBox\" class=\"error hidden\"></div><div class=\"table-wrap\"><table><thead><tr><th>Time</th><th>Level</th><th>Source</th><th>Type</th><th>Message</th><th>Metadata</th></tr></thead><tbody id=\"eventsBody\"><tr><td colspan=\"6\" class=\"empty\">No events yet.</td></tr></tbody></table></div></article>"
    "<aside class=\"panel side\"><h2>Levels</h2><div id=\"levelBreakdown\" class=\"bars\"></div><h2>Top sources</h2><div id=\"topSources\" class=\"list\"></div><h2>Top types</h2><div id=\"topTypes\" class=\"list\"></div></aside>"
    "</section></main>")))

(defn login [error?]
  (layout
   "Clojure EventPulse Admin Login"
   (str
    "<main class=\"shell login-shell\">"
    "<section class=\"login-panel panel\">"
    "<p class=\"eyebrow\">Admin access</p><h1>Clojure EventPulse</h1>"
    "<p class=\"login-copy\">Sign in to view real event data. Public visitors only see demo data.</p>"
    (when error? "<div class=\"error\">Invalid admin credentials.</div>")
    "<form method=\"post\" action=\"/login\" class=\"login-form\">"
    "<label>Username<input name=\"username\" autocomplete=\"username\" required></label>"
    "<label>Password<input name=\"password\" type=\"password\" autocomplete=\"current-password\" required></label>"
    "<button type=\"submit\">Login</button>"
    "</form><p><a href=\"/\">Back to dashboard</a></p>"
    "</section></main>")))

(defn docs []
  (layout
   "Clojure EventPulse Docs"
   (str
    "<main class=\"shell docs\">"
    "<header class=\"topbar\"><div><p class=\"eyebrow\">Documentation</p><h1>Clojure EventPulse</h1></div><nav><a href=\"/\">Dashboard</a><a href=\"/login\">Admin login</a></nav></header>"
    "<section class=\"panel\"><h2>What it does</h2><p>Clojure EventPulse receives JSON events from VPS projects, validates them, stores recent history in SQLite, and streams updates to the dashboard with Server-Sent Events.</p></section>"
    "<section class=\"panel\"><h2>Event JSON</h2><pre><code>{\n  \"source\": \"weather-sim\",\n  \"level\": \"warning\",\n  \"type\": \"high_cpu\",\n  \"message\": \"Simulation tick delayed\",\n  \"metadata\": {\n    \"cpu\": 91,\n    \"service\": \"simulation\"\n  }\n}</code></pre><p>Required fields: source, level, type, message. Optional field: metadata object.</p></section>"
    "<section class=\"panel\"><h2>Authentication</h2><p>POST requests require the header <code>X-API-Key: &lt;your-api-key&gt;</code>. The real key is stored only in <code>.env</code>.</p>"
    "<pre><code>curl -X POST https://clojure.micutu.com/api/events \\\n  -H 'Content-Type: application/json' \\\n  -H 'X-API-Key: REPLACE_WITH_API_KEY' \\\n  -d '{\"source\":\"weather-sim\",\"level\":\"warning\",\"type\":\"high_cpu\",\"message\":\"Simulation tick delayed\",\"metadata\":{\"cpu\":91}}'</code></pre></section>"
    "<section class=\"panel\"><h2>Endpoints</h2><ul><li>GET /health</li><li>GET /api/events?source=&amp;level=&amp;type=&amp;limit=</li><li>GET /api/events/stats</li><li>GET /api/events/stream</li><li>POST /api/events</li><li>GET /docs</li></ul></section>"
    "<section class=\"panel\"><h2>Allowed levels</h2><p><code>debug</code>, <code>info</code>, <code>warning</code>, <code>error</code>, <code>critical</code></p></section>"
    "</main>")))
