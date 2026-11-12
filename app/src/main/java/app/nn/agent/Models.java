package app.nn.agent;

/** Models offered per agent. Concrete real models only; each agent resolves its real default cleanly. */
final class Models {
    static final String[][] CLAUDE = {
            {"claude-opus-5-5", "Opus 5.5"},
            {"claude-sonnet-5-5", "Sonnet 5.5"},
            {"claude-haiku-5-5", "Haiku 5.5"},
            {"claude-haiku-4-5-20251001", "Haiku 4.5"},
            {"claude-fable-5-1", "Fable 5.1"},
    };
    /** Used until the helper reports Codex's own model list. */
    static final String[][] CODEX = {
            {"gpt-6.1-sol", "GPT-6.1-Sol"},
            {"gpt-6-astra", "GPT-6-Astra"},
            {"gpt-6-luna", "GPT-6-Luna"},
    };
    /** Used until OpenCode reports its own list (`opencode models`, which includes the free ones). */
    static final String[][] OPENCODE = {
            {"opencode/big-pickle", "opencode/big-pickle"},
    };
    /** Used until Antigravity reports its own list (`agy models`, which depends on the plan). */
    static final String[][] AGY = {
            {"gemini-3.8-flash-high", "Gemini 3.8 Flash (High)"},
            {"gemini-3.8-flash-medium", "Gemini 3.8 Flash (Medium)"},
            {"gemini-3.8-flash-low", "Gemini 3.8 Flash (Low)"},
    };
    /** Used until Grok reports its own list (`grok models`). */
    static final String[][] GROK = {
            {"grok-4.7", "grok-4.7"},
    };
    private static String[][] codexLive, opencodeLive, agyLive, grokLive;

    private Models() {
    }

    private static long refreshedAt;

    /** Re-reads the agents' model lists (new free OpenCode models appear often) when the picker opens, at most every
     *  10 minutes; the helper's answer updates the lists. The API key services' lists too. */
    static void refresh(android.content.Context c) {
        Providers.refresh(c);
        long now = System.currentTimeMillis();
        if (now - refreshedAt < 600_000 || !Linux.installed(c)) return;
        refreshedAt = now;
        try {
            Helper.run(c, "ping");
        } catch (Exception ignored) {
        }
    }

    /** The agent's own real models (no synthetic "Default" row). */
    static String[][] forBackend(String backend) {
        if ("opencode".equals(backend)) return opencodeLive != null ? opencodeLive : OPENCODE;
        if ("agy".equals(backend)) return agyLive != null ? agyLive : AGY;
        if ("grok".equals(backend)) return grokLive != null ? grokLive : GROK;
        if ("codex".equals(backend)) return codexLive != null ? codexLive : CODEX;
        return CLAUDE;
    }

    /**
     * Everything a model picker offers for this agent: its real plan/CLI models (when signed in to a plan, or
     * OpenCode's own), then each API key service it can use, under the service's name: rows {id, name}, heading
     * {null, service name}. Service models are "@service/model".
     */
    static java.util.List<String[]> options(String backend, boolean services) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        String[][] own = forBackend(backend);
        boolean plan = !("claude".equals(backend) || "codex".equals(backend)) || SetupState.planSignedIn(NnApp.context(), backend);
        if (plan) {
            java.util.Collections.addAll(out, own);
        }
        if (!services) return out;
        for (Providers.P p : Providers.forAgent(backend)) {
            org.json.JSONArray models = p.offered();
            if (models.length() == 0) continue;
            out.add(new String[]{null, p.name});
            for (int i = 0; i < models.length(); i++) {
                org.json.JSONObject m = models.optJSONObject(i);
                out.add(new String[]{Providers.ref(p, m.optString("id")), m.optString("name")});
            }
        }
        return out;
    }

    /** Concrete CLI default model reported for this agent, falling back to its primary native model. */
    static String cliDefaultModel(String backend) {
        String cli = Prefs.cliDefaultModel(NnApp.context(), backend);
        if (!cli.isEmpty()) return cli;
        String[][] own = forBackend(backend);
        if ("claude".equals(backend)) return "claude-sonnet-5-5";
        if ("opencode".equals(backend)) {
            for (String[] m : own) if ("opencode/big-pickle".equals(m[0])) return m[0];
        }
        return own.length > 0 ? own[0][0] : "";
    }

    /** What new chats on this agent start on: user-pinned default model, API service fallback, or CLI default. */
    static String agentDefault(String backend) {
        android.content.Context c = NnApp.context();
        String d = Prefs.agentDefault(c, backend);
        if (!d.isEmpty()) {
            Providers.P p = Providers.of(d);
            if (p != null) {
                if (p.fits(backend)) {
                    String m = Providers.model(d);
                    return Providers.ref(p, m.isEmpty() ? p.defaultModel() : m);
                }
            } else {
                return d;
            }
        }
        if (("claude".equals(backend) || "codex".equals(backend)) && !SetupState.planSignedIn(c, backend)) {
            java.util.List<Providers.P> svcs = Providers.forAgent(backend);
            if (!svcs.isEmpty()) {
                Providers.P first = svcs.get(0);
                return Providers.ref(first, first.defaultModel());
            }
        }
        return cliDefaultModel(backend);
    }

    /** The chat's model as it will run: explicit chat pick, or resolved real agent default. */
    static String effective(Sessions.Session s) {
        return (s.model == null || s.model.isEmpty()) ? agentDefault(s.backend) : s.model;
    }

    /** A service model's context size in tokens as its service said, or 0 when not known. */
    static long window(String ref) {
        Providers.P p = Providers.of(ref);
        org.json.JSONObject m = p == null ? null : p.modelInfo(Providers.model(ref));
        return m == null ? 0 : m.optLong("window");
    }

    /** The line under a model in the pickers: its id (or what Default means), with its context size when known. */
    static String detail(String backend, String id, String defaultHint) {
        if (id.isEmpty()) return defaultHint;
        long w = window(id);
        String shown = Providers.of(id) != null ? Providers.model(id) : id;
        return w > 0 ? shown + " · " + NnApp.str(R.string.models_context, tokens(w)) : shown;
    }

    /** 1048576 → "1M", 200000 → "200K", 8104 → "8.1K". */
    static String tokens(long n) {
        if (n >= 1_000_000) return trim(String.format(java.util.Locale.ROOT, "%.1f", n / 1_000_000.0)) + "M";
        if (n >= 10_000) return Math.round(n / 1000.0) + "K";
        if (n >= 1000) return trim(String.format(java.util.Locale.ROOT, "%.1f", n / 1000.0)) + "K";
        return String.valueOf(n);
    }

    private static String trim(String s) {
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** How full the chat's context is (percent), or -1 when its agent hasn't said. */
    static int contextPercent(Sessions.Session s) {
        if (s.ctxWindow <= 0 || s.ctxUsed <= 0) return -1;
        return (int) Math.min(100, Math.round(s.ctxUsed * 100.0 / s.ctxWindow));
    }

    /** OpenCode's models as JSON [[id, name], …] ("provider/model" for both). */
    static void setOpencode(String json) {
        String[][] m = parse(json);
        if (m != null) opencodeLive = m;
    }

    /** Antigravity's models as JSON [[id, name], …]. */
    static void setAgy(String json) {
        String[][] m = parse(json);
        if (m != null) agyLive = m;
    }

    /** Grok's models as JSON [[id, name], …] (its ids are its names). */
    static void setGrok(String json) {
        String[][] m = parse(json);
        if (m != null) grokLive = m;
    }

    /** Codex's model list as reported by the helper: JSON [[slug, name], …]. */
    static void setCodex(String json) {
        String[][] m = parse(json);
        if (m != null) codexLive = m;
    }

    private static String[][] parse(String json) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(json);
            if (a.length() == 0) return null;
            String[][] m = new String[a.length()][];
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONArray e = a.getJSONArray(i);
                m[i] = new String[]{e.getString(0), e.getString(1)};
            }
            return m;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Effort levels accepted for a chat's agent & active model ([value, label]; "" is Auto).
     */
    static String[][] efforts(Sessions.Session s) {
        return efforts(s.backend, effective(s));
    }

    /**
     * Effort levels accepted for an agent backend and model ref ([value, label]; "" is Auto),
     * or null when effort is NOT an independent setting (e.g. Antigravity bakes effort into each
     * model variant ID like gemini-3.8-flash-high, or OpenCode/Grok models without declared levels).
     */
    static String[][] efforts(String backend, String ref) {
        if (backend == null || "agy".equals(backend)) return null;
        Providers.P p = Providers.of(ref);
        if (p != null) {
            org.json.JSONObject m = p.modelInfo(Providers.model(ref));
            org.json.JSONArray e = m == null ? null : m.optJSONArray("efforts");
            if (e != null && e.length() > 0) {
                java.util.List<String[]> out = new java.util.ArrayList<>();
                out.add(new String[]{"", NnApp.str(R.string.models_effort_auto)});
                for (int j = 0; j < e.length(); j++) {
                    String lv = e.optString(j);
                    if (!lv.isEmpty()) out.add(new String[]{lv, effortLabel(lv)});
                }
                return out.size() > 1 ? out.toArray(new String[0][]) : null;
            }
            return agentEfforts(backend);
        }
        if (hasBakedEffort(ref)) return null;
        return agentEfforts(backend);
    }

    /** True when selecting Effort is genuinely independent from selecting Model. */
    static boolean supportsEffort(String backend, String ref) {
        return efforts(backend, ref) != null;
    }

    private static boolean hasBakedEffort(String ref) {
        if (ref == null || ref.isEmpty()) return false;
        String lower = ref.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith("-low") || lower.endsWith("-medium") || lower.endsWith("-high")
                || lower.endsWith("-xhigh") || lower.endsWith("-max") || lower.endsWith("-minimal")
                || lower.contains("(low)") || lower.contains("(medium)") || lower.contains("(high)");
    }

    private static String[][] agentEfforts(String backend) {
        if ("claude".equals(backend)) return new String[][]{{"", NnApp.str(R.string.models_effort_auto)},
                {"low", NnApp.str(R.string.models_effort_low)}, {"medium", NnApp.str(R.string.models_effort_medium)},
                {"high", NnApp.str(R.string.models_effort_high)}, {"xhigh", NnApp.str(R.string.models_effort_xhigh)},
                {"max", NnApp.str(R.string.models_effort_max)}};
        if ("codex".equals(backend)) return new String[][]{{"", NnApp.str(R.string.models_effort_auto)},
                {"low", NnApp.str(R.string.models_effort_low)}, {"medium", NnApp.str(R.string.models_effort_medium)},
                {"high", NnApp.str(R.string.models_effort_high)}};
        return null;
    }

    /** Human label for a reasoning effort level. */
    static String effortLabel(String effort) {
        if (effort == null || effort.isEmpty()) return NnApp.str(R.string.models_effort_auto);
        switch (effort) {
            case "low": return NnApp.str(R.string.models_effort_low);
            case "medium": return NnApp.str(R.string.models_effort_medium);
            case "high": return NnApp.str(R.string.models_effort_high);
            case "xhigh": return NnApp.str(R.string.models_effort_xhigh);
            case "max": return NnApp.str(R.string.models_effort_max);
            default: return effort;
        }
    }

    /** "agent · model · effort" for a chat's chip. */
    static String summary(Sessions.Session s) {
        int ctx = contextPercent(s); // only once it matters: past half full
        String eff = (s.effort == null || s.effort.isEmpty() || !supportsEffort(s.backend, effective(s))) ? ""
                : NnApp.str(R.string.models_chip_effort, effortLabel(s.effort).toLowerCase(Ui.locale(NnApp.context())));
        return s.brainName() + " · " + shownModel(s)
                + (eff.isEmpty() ? "" : " · " + eff)
                + (ctx >= 50 ? " · " + NnApp.str(R.string.models_chip_context, ctx) : "");
    }

    /** The API key service the chat's model comes from (its name), or null for the agent's own. */
    static String via(Sessions.Session s) {
        Providers.P p = Providers.of(effective(s));
        return p == null ? null : p.name;
    }

    /** The chat's concrete active model name for display (resolving legacy empty choices cleanly). */
    static String shownModel(Sessions.Session s) {
        String eff = effective(s);
        Providers.P p = Providers.of(eff);
        if (p != null) return NnApp.str(R.string.models_via, serviceModelName(p, Providers.model(eff)), p.name);
        if (!s.model.isEmpty()) return label(s.backend, s.model);
        if (!s.actualModel.isEmpty()) return nameOf(s.backend, s.actualModel);
        return label(s.backend, eff);
    }

    private static String serviceModelName(Providers.P p, String model) {
        org.json.JSONObject m = p.modelInfo(model);
        return m == null || m.optString("name").isEmpty() ? model : m.optString("name");
    }

    /** What the agent's default model is for display in hints. */
    static String defaultHint(Sessions.Session s) {
        return detail(s.backend, agentDefault(s.backend), "");
    }

    /** What the agent's own default is. */
    static String defaultHint(String backend) {
        return detail(backend, agentDefault(backend), "");
    }

    /** A model id the agent reported, by its name in the model list if it's there. */
    static String nameOf(String backend, String id) {
        String l = label(backend, id);
        return l.equals(id) ? pretty(id) : l;
    }

    /** "claude-opus-5-5-20261001" → "Opus 5.5"; other ids as they are. */
    static String pretty(String id) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("claude-(opus|sonnet|haiku|fable)-(\\d+)-(\\d+)").matcher(id);
        if (m.find()) return Character.toUpperCase(m.group(1).charAt(0)) + m.group(1).substring(1) + " " + m.group(2) + "." + m.group(3);
        return id;
    }

    static String label(String backend, String model) {
        if (model == null || model.isEmpty()) model = agentDefault(backend);
        if (model.isEmpty()) return NnApp.str(R.string.models_default_model);
        Providers.P p = Providers.of(model);
        if (p != null) return NnApp.str(R.string.models_via, serviceModelName(p, Providers.model(model)), p.name);
        for (String[] m : forBackend(backend)) if (m[0].equals(model)) return m[1];
        String pName = pretty(model);
        return pName.equals(model) ? model : pName;
    }
}
