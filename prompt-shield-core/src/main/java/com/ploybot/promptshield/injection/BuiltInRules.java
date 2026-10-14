package com.ploybot.promptshield.injection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ploybot.promptshield.injection.InjectionCategory.AI_ADDRESSED;
import static com.ploybot.promptshield.injection.InjectionCategory.EXFILTRATION;
import static com.ploybot.promptshield.injection.InjectionCategory.INSTRUCTION_OVERRIDE;
import static com.ploybot.promptshield.injection.InjectionCategory.JAILBREAK;
import static com.ploybot.promptshield.injection.InjectionCategory.OBFUSCATION;
import static com.ploybot.promptshield.injection.InjectionCategory.PLACEHOLDER_ABUSE;
import static com.ploybot.promptshield.injection.InjectionCategory.PROMPT_LEAK;
import static com.ploybot.promptshield.injection.InjectionCategory.ROLE_SPOOFING;
import static com.ploybot.promptshield.injection.InjectionCategory.TOOL_HIJACK;

/**
 * The default rule set. Weights were chosen so that a single strong rule (an explicit
 * "ignore your instructions", a chat-template token, a request for the system prompt)
 * reaches the default threshold of 0.5 on its own, while weak rules (a word such as
 * "developer mode", a markdown image with query parameters) only count when they
 * co-occur with other evidence.
 *
 * <p>English, Spanish, French, German, Portuguese, Italian, Catalan, Russian and
 * Chinese are covered for the most common attack families. Patterns are written
 * against the normalized views: lower case, no accents.
 */
final class BuiltInRules {

    private BuiltInRules() {
    }

    /** Up to {@code n} intermediate words. */
    private static String gap(int n) {
        return "(?:\\W+\\w+){0," + n + "}?\\W+";
    }

    // English vocabulary
    private static final String EN_OVERRIDE_VERB = "(?:ignore|ignoring|disregard|disregarding|forget|bypass|circumvent|"
            + "stop following|do not follow|dont follow|don't follow|no longer follow|stop obeying|do not obey|dont obey|"
            + "set aside|neglect)";
    /** Verbs common in software and legal prose ("override the default settings"): need a possessive or prior modifier. */
    private static final String EN_WEAK_OVERRIDE_VERB = "(?:override|overriding|overwrite|replace|cancel|nullify|void|"
            + "supersede|scrap|drop|discard|abandon|skip|throw away|delete|erase|wipe)";
    private static final String EN_OWNED = "(?:your|all (?:of )?your|all (?:the )?(?:previous|prior|above|earlier|preceding|"
            + "initial|original|existing)|any (?:previous|prior|earlier)|(?:the )?(?:previous|prior|above|earlier|preceding|"
            + "initial|original|system|developer|safety)|these|those)";
    private static final String EN_STRONG_NOUN = "(?:instructions?|prompts?|rules?|ruies|directives?|guidelines?|guidance|"
            + "system prompt|system message|programming|guardrails?|restrictions?|constraints?|policies|policy|"
            + "safety (?:settings|measures|protocols)|training|directions|orders|commands?)";
    private static final String EN_PRIOR = "(?:previous|prior|above|earlier|preceding|initial|original|old|existing|former|"
            + "system|developer|default|all|every|any|these|those|your|its|the|other|given|current|safety|preset|built-in|"
            + "programmed|predefined|initial)";
    private static final String EN_AI = "(?:ai|a\\.i\\.|artificial intelligence|assistant|ai assistant|llm|large language model|"
            + "language model|chatbot|chat bot|bot|agent|ai agent|copilot|chatgpt|gpt|gpt-?\\d\\w*|claude|gemini|bard|bing|"
            + "llama|mistral|model|ai model|ai system|automated system|summarizer|email assistant|coding assistant)";
    private static final String EN_SECRET = "(?:system prompt|system message|initial (?:prompt|instructions)|"
            + "(?:the|your) (?:conversation|chat|message) history|conversation|chat history|chat log|previous messages|"
            + "all messages|api[ _-]?keys?|passwords?|credentials?|secrets?|access tokens?|auth tokens?|bearer tokens?|"
            + "private keys?|ssh keys?|id_rsa|session cookies?|cookies|\\.env|environment variables)";
    /** Data that is often legitimately forwarded ("forward the emails to accounting"). */
    private static final String EN_WEAK_SECRET = "(?:personal (?:data|information|details)|user'?s? (?:data|information|"
            + "details|emails?|address|password)|customer (?:data|records|list)|credit card|card numbers?|bank details|iban|"
            + "contacts|inbox|emails|documents|files|confidential information)";
    private static final String DESTINATION = "(?:https?://|www\\.|ftp://|[\\w.+-]+@[\\w-]+\\.[\\w.]+|attacker|external|"
            + "(?:this|the following|my|our|that) (?:url|address|endpoint|server|link|webhook|email)|url|webhook|endpoint|"
            + "server|domain|pastebin|discord|telegram|ngrok)";

    /**
     * Pre-filter anchors (see {@link InjectionRule#anchors()}). Each list must contain
     * a substring of every possible match of its rule; {@code InjectionRuleAnchorsTest}
     * checks on the whole evaluation corpus that the anchors never change a verdict.
     */
    private static final Map<String, List<String>> ANCHORS = Map.ofEntries(
            Map.entry("override.en", List.of("ignor", "disregard", "forget", "overrid", "bypass", "circumvent", "skip", "discard", "abandon", "neglect", "set aside", "follow", "obey", "cancel", "nullify", "void", "supersede", "replace", "scrap", "drop")),
            Map.entry("override.en-weak-verb", List.of("overrid", "overwrite", "replace", "cancel", "nullify", "void", "supersede", "scrap", "drop", "discard", "abandon", "skip", "throw away", "delete", "erase", "wipe")),
            Map.entry("role.developer-mode", List.of("mode", "modo")),
            Map.entry("role.false-authorization", List.of("authori", "permitted", "allowed", "cleared", "autoriz")),
            Map.entry("jailbreak.disable-safety", List.of("safety", "security", "content", "moderation", "ethic", "seguridad", "moderacion", "etic", "contenido")),
            Map.entry("addressed.vocative", List.of(",")),
            Map.entry("exfil.send-data", List.of("http", "www.", "ftp", "@", "attacker", "external", "url", "address", "endpoint", "server", "link", "webhook", "email", "domain", "pastebin", "discord", "telegram", "ngrok")),
            Map.entry("override.en-everything", List.of("ignor", "disregard", "forget")),
            Map.entry("override.en-prior-context", List.of("ignor", "disregard", "forget")),
            Map.entry("override.en-new-instructions", List.of("instruction", "directive", "order", "command", "task", "prompt", "objective")),
            Map.entry("override.en-your-new-task", List.of("your")),
            Map.entry("override.en-from-now-on", List.of("from now on", "from this point", "henceforth", "for the rest of")),
            Map.entry("override.en-stop-task", List.of("stop", "cease", "abort", "halt", "pause")),
            Map.entry("override.en-instead", List.of("instead", "rather")),
            Map.entry("override.en-priority", List.of("precedence", "priority", "supersede", "overrid")),
            Map.entry("override.es", List.of("instrucci", "indicaciones", "reglas", "normas", "ordenes", "directri", "directiva", "pautas", "restricci", "limitaci", "politicas", "prompt", "consignas", "programacion", "filtros", "salvaguardas", "mensaje", "lo anterior", "lo que te")),
            Map.entry("override.es-new", List.of("instrucci", "orden", "directri", "indicaciones", "tarea", "mision", "funcion", "objetivo", "proposito")),
            Map.entry("override.es-from-now-on", List.of("ahora", "adelante")),
            Map.entry("override.es-priority", List.of("prioridad", "precedencia")),
            Map.entry("override.fr", List.of("instruction", "consigne", "regle", "directive", "prompt", "ordre", "indication", "restriction")),
            Map.entry("override.de", List.of("anweisung", "instruktion", "regeln", "vorgaben", "richtlinie", "befehle", "prompt", "einschrankung", "beschrankung")),
            Map.entry("override.pt", List.of("instruc", "regras", "diretriz", "orientac", "comandos", "prompt", "ordens", "restric")),
            Map.entry("override.it", List.of("istruzion", "regole", "direttive", "indicazioni", "comandi", "prompt", "ordini", "restrizioni")),
            Map.entry("override.ca", List.of("instrucci", "regles", "indicacions", "directrius", "ordres", "prompt")),
            Map.entry("override.ru", List.of("инструкци", "правил", "указани", "команд", "промпт", "ограничени", "директив")),
            Map.entry("override.zh", List.of("指令", "指示", "说明", "规则", "提示", "设定", "命令", "限制")),
            Map.entry("role.chat-template-token", List.of("<|")),
            Map.entry("role.inst-tags", List.of("inst]", "sys>>", "_of_turn>", "[system]", "system_prompt]")),
            Map.entry("role.json-system-message", List.of("role")),
            Map.entry("role.fake-system-header", List.of("system", "developer", "admin", "root", "sistema", "assistant")),
            Map.entry("role.fake-turns", List.of("human", "user", "usuario")),
            Map.entry("role.end-of-data", List.of("end of", "fin del")),
            Map.entry("role.closing-delimiter", List.of("</")),
            Map.entry("role.mode-switch", List.of("mode", "modo")),
            Map.entry("role.authority-claim", List.of("developer", "creator", "admin", "owner", "operator", "openai", "anthropic", "security team", "desarrollador", "creador")),
            Map.entry("leak.en", List.of("system", "initial", "original", "hidden", "secret", "your", "prompt", "developer", "everything", "above", "configuration", "instructions", "context window", "confidential", "first", "message")),
            Map.entry("leak.en-question", List.of("what")),
            Map.entry("leak.en-starting-with", List.of("starting", "beginning")),
            Map.entry("leak.es", List.of("prompt", "sistema", "instrucciones", "reglas", "configuracion", "anterior")),
            Map.entry("leak.es-question", List.of("cual")),
            Map.entry("leak.fr-de-pt-it", List.of("prompt", "invite", "instruction", "anweisung", "instruc", "istruzion")),
            Map.entry("leak.ru-zh", List.of("систем", "инструкци", "提示", "指令", "系统")),
            Map.entry("jailbreak.dan", List.of("anything", "you", "act", "become", "eres", "actua")),
            Map.entry("jailbreak.persona-unrestricted", List.of("unfiltered", "uncensored", "unrestricted", "unbound", "unlimited", "jailbroken", "amoral", "unethical", "evil", "rogue", "without")),
            Map.entry("jailbreak.no-restrictions", List.of("restriction", "filter", "guideline", "limitation", "censorship", "safeguard", "guardrail", "polic", "rules", "boundaries")),
            Map.entry("jailbreak.freed", List.of("freed", "liberated", "unshackled", "jailbroken", "released", "unlocked", "uncensored", "free")),
            Map.entry("jailbreak.policy-bypass", List.of("polic", "guideline", "filter", "rules", "restriction", "guardrail", "safeguard", "alignment", "training")),
            Map.entry("jailbreak.es", List.of("sin ", "malvad", "desatad", "libre", "liberad", "desbloquead")),
            Map.entry("jailbreak.stay-in-character", List.of("character", "papel", "personaje")),
            Map.entry("jailbreak.hypothetical-unfiltered", List.of("responses", "answers", "replies")),
            Map.entry("addressed.note-to-ai", List.of("note", "message", "instruction", "notice", "attention", "important", "warning", "reminder", "directive", "request", "psa", "memo", "hint", "update")),
            Map.entry("addressed.ai-reading-this", List.of("reading", "processing", "parsing", "summari", "analy", "reviewing", "scanning", "seeing", "ingesting", "evaluating", "looking at", "handling", "reads", "processes")),
            Map.entry("addressed.if-you-are-ai", List.of("if", "hey", "hi", "hello", "dear", "attention", "ok")),
            Map.entry("addressed.when-you-summarize", List.of("when")),
            Map.entry("addressed.es", List.of("nota", "mensaje", "instruccion", "aviso", "atencion", "importante", "recordatorio", "orden", "si ", "que ", "cuando")),
            Map.entry("addressed.other-langs", List.of("note", "message", "instruction", "remarque", "hinweis", "nachricht", "anweisung", "nota", "messaggio", "mensagem", "aviso", "si tu es", "wenn du", "se sei", "se voce")),
            Map.entry("addressed.hide-from-user", List.of("user", "human", "customer", "operator", "recipient", "victim", "owner", "person")),
            Map.entry("addressed.hide-from-user-es", List.of("usuari", "cliente", "humano", "nadie", "propietario", "persona")),
            Map.entry("addressed.secretly", List.of("secret", "silent", "quiet", "covert", "discreet", "background", "without", "sin ")),
            Map.entry("exfil.send-secrets", List.of("http", "www.", "ftp", "@", "attacker", "external", "url", "address", "endpoint", "server", "link", "webhook", "email", "domain", "pastebin", "discord", "telegram", "ngrok")),
            Map.entry("exfil.send-secrets-es", List.of("http", "www.", "@", "servidor", "url", "enlace", "direccion", "webhook", "endpoint", "dominio", "atacante", "externo")),
            Map.entry("addressed.response-directive", List.of("respon", "answer", "repl", "output", "summary", "translation", "completion", "respuesta", "resumen", "contestacion")),
            Map.entry("exfil.url-param-template", List.of("http")),
            Map.entry("exfil.markdown-instruction", List.of("markdown", "html", "clickable", "hidden", "invisible", "1x1", "tracking")),
            Map.entry("exfil.markdown-image-query", List.of("![")),
            Map.entry("exfil.markdown-image-template", List.of("![")),
            Map.entry("exfil.render-url-with-data", List.of("image", "img", "url", "link", "imagen", "enlace", "pixel", "http")),
            Map.entry("exfil.sensitive-file", List.of(".ssh", "id_rsa", "id_ed25519", "/etc/", ".aws", ".git-credentials", ".npmrc", ".pypirc", "mcp.json", "cursor/mcp", ".kube", "wallet.dat", ".bash_history", "keychain")),
            Map.entry("exfil.read-sensitive-file", List.of(".ssh", "id_rsa", "id_ed25519", "/etc/", ".aws", ".git-credentials", ".env", ".npmrc", "mcp.json", ".kube", "private key", "clave privada")),
            Map.entry("tool.call-named-tool", List.of("tool", "function", "plugin", "action", "api", "command", "herramienta", "funcion", "comando", "_")),
            Map.entry("tool.urgent-action", List.of("immediately", "right now", "right away", "without delay", "before", "first", "inmediatamente", "de inmediato", "ahora mismo", "antes de")),
            Map.entry("tool.mcp-important-tag", List.of("<")),
            Map.entry("tool.destructive", List.of("delete", "remove", "wipe", "erase", "drop", "destroy", "purge", "truncate", "rm -rf", "format", "borra", "elimina", "suprime", "destruye")),
            Map.entry("tool.account-takeover", List.of("password", "email address", "recovery", "api key", "2fa", "mfa", "permission", "rol", "admin rights", "contrasena", "correo de recuperacion", "permisos")),
            Map.entry("tool.money-transfer", List.of("fund", "money", "payment", "$", "€", "usd", "eur", "dollar", "btc", "eth", "usdt", "bitcoin", "crypto", "dinero", "fondos", "saldo", "balance")),
            Map.entry("tool.shell", List.of("curl", "wget", "nc ", "base64", "powershell", "pwsh")),
            Map.entry("obfuscation.decode-and-follow", List.of("base", "b64", "hex", "rot", "caesar", "cesar", "binar", "morse", "revers", "backwards", "al reves", "encoded", "codificado", "cifrado")),
            Map.entry("obfuscation.follow-hidden", List.of("base64", "encoded", "hidden", "decoded", "cipher", "codificad", "ocult", "cifrad")),
            Map.entry("placeholder.collect", List.of("redacted", "obfuscated", "masked", "placeholder", "ofuscad", "enmascarad", "marcadores")));

    static List<InjectionRule> rules() {
        List<InjectionRule> r = new ArrayList<>();

        // ------------------------------------------------------------ instruction override (EN)
        r.add(InjectionRule.of("override.en", INSTRUCTION_OVERRIDE,
                "\\b" + EN_OVERRIDE_VERB + "\\b" + gap(5) + "(?:" + EN_PRIOR + "\\W+)?" + EN_STRONG_NOUN + "\\b", 0.72, 0.8));
        r.add(InjectionRule.of("override.en-weak-verb", INSTRUCTION_OVERRIDE,
                "\\b" + EN_WEAK_OVERRIDE_VERB + "\\W+" + EN_OWNED + "\\W+(?:\\w+\\W+){0,2}?" + EN_STRONG_NOUN + "\\b",
                0.65, 0.75));
        r.add(InjectionRule.of("override.en-everything", INSTRUCTION_OVERRIDE,
                "\\b(?:ignore|disregard|forget)\\W+(?:all\\W+|everything\\W+|anything\\W+|what(?:ever)?\\W+)?"
                        + "(?:everything|anything|all|what)\\W+(?:above|before|previously|said|written|you(?:'ve| have| were) (?:been )?told|"
                        + "you know|you were given|prior)\\b", 0.65, 0.75));
        r.add(InjectionRule.of("override.en-prior-context", INSTRUCTION_OVERRIDE,
                "\\b(?:ignore|disregard|forget)\\W+(?:the\\W+|all\\W+|any\\W+)?(?:previous|prior|above|preceding|earlier|"
                        + "system|original)\\W+(?:context|conversation|messages?|text|content|input|task|request)\\b", 0.45, 0.6));
        r.add(InjectionRule.of("override.en-new-instructions", INSTRUCTION_OVERRIDE,
                "\\b(?:new|updated|revised|real|actual|true|override|overriding|secret|hidden|additional|important|urgent|"
                        + "priority|emergency|admin|system)\\W+(?:system\\W+)?(?:instructions?|directives?|orders|commands?|"
                        + "task|prompt|objective)\\s*(?::|-|=|>|follow)", 0.5, 0.6));
        r.add(InjectionRule.of("override.en-your-new-task", INSTRUCTION_OVERRIDE,
                "\\byour\\W+(?:new|real|true|actual|only|primary|updated|next)\\W+(?:task|goal|purpose|instructions?|"
                        + "objective|role|job|mission|directive|priority)\\W+(?:is|are|will be|now)\\b", 0.55, 0.7));
        r.add(InjectionRule.of("override.en-from-now-on", INSTRUCTION_OVERRIDE,
                "\\b(?:from now on|from this point(?: on| forward)?|henceforth|for the rest of (?:this|the) conversation)"
                        + "\\W+(?:\\w+\\W+){0,2}?(?:you|ai|assistant)\\W+(?:are|will|must|shall|should|have to|can|may|act|"
                        + "respond|answer|only|always|never)\\b", 0.4, 0.55));
        r.add(InjectionRule.of("override.en-stop-task", INSTRUCTION_OVERRIDE,
                "\\b(?:stop|cease|abort|halt|pause)\\W+(?:what you are doing|your current task|the current task|"
                        + "summari[sz]ing|translating|processing|the summary|the translation)\\b", 0.35, 0.55));
        r.add(InjectionRule.of("override.en-instead", INSTRUCTION_OVERRIDE,
                "\\b(?:instead|rather)\\W+(?:of\\W+(?:\\w+\\W+){0,4}?)?(?:you\\W+(?:must|should|will|need to|have to)|"
                        + "do the following|perform the following|follow these)\\b", 0.25, 0.4));
        r.add(InjectionRule.of("override.en-priority", INSTRUCTION_OVERRIDE,
                "\\b(?:this|these|the following)\\W+(?:instructions?|message|directive|rules?)\\W+(?:\\w+\\W+){0,2}?"
                        + "(?:take|takes|has|have)\\W+(?:precedence|priority)\\b|\\b(?:supersedes?|overrides?)\\W+"
                        + "(?:all|any|every)\\W+(?:\\w+\\W+){0,1}?(?:previous|prior|other|existing)\\W+(?:instructions|"
                        + "directives|rules|prompts|guidelines|system prompts?|commands|orders|ones)\\b", 0.5, 0.65));

        // ------------------------------------------------------------ instruction override (other languages)
        r.add(InjectionRule.of("override.es", INSTRUCTION_OVERRIDE,
                "\\b(?:ignora|ignore|ignoren|ignorar|ignorad|ignorando|olvida|olvide|olviden|olvidar|olvidate|olvidese|"
                        + "omite|omitir|omita|descarta|descartar|descarte|desobedece|desobedecer|anula|anular|salta(?:te)?|"
                        + "saltarte|saltate|deja de (?:seguir|lado|cumplir|obedecer)|no (?:sigas|siga|hagas caso|haga caso|"
                        + "tengas en cuenta|obedezcas|cumplas|respetes)|haz caso omiso|haga caso omiso|pasa(?:te)? por alto|"
                        + "prescinde|desactiva|elimina|sobrescribe|reemplaza|sustituye)\\b" + gap(5)
                        + "(?:instrucciones|instruccion|indicaciones|reglas|normas|ordenes|directrices|directivas|pautas|"
                        + "restricciones|limitaciones|politicas|prompt|prompts|consignas|programacion|filtros|"
                        + "salvaguardas|mensajes? (?:anteriores|previos|del sistema)|lo (?:anterior|que te (?:dijeron|han dicho)))\\b",
                0.72, 0.8));
        r.add(InjectionRule.of("override.es-new", INSTRUCTION_OVERRIDE,
                "\\b(?:nuevas|verdaderas|autenticas|actualizadas|reales|importantes|urgentes|ocultas|secretas)\\W+"
                        + "(?:instrucciones|ordenes|directrices|indicaciones)\\s*(?::|-|=|>)|\\btu\\W+(?:nueva|verdadera|"
                        + "autentica|real|unica|principal)\\W+(?:tarea|mision|funcion|objetivo|proposito|instruccion|orden)\\W+"
                        + "(?:es|sera|ahora)\\b", 0.55, 0.7));
        r.add(InjectionRule.of("override.es-from-now-on", INSTRUCTION_OVERRIDE,
                "\\b(?:a partir de ahora|desde ahora|de ahora en adelante|en adelante)\\W+(?:\\w+\\W+){0,2}?(?:eres|seras|"
                        + "actuaras|actua|debes|deberas|vas a|responderas|responde|tienes que|solo|siempre|nunca)\\b", 0.4, 0.55));
        r.add(InjectionRule.of("override.es-priority", INSTRUCTION_OVERRIDE,
                "\\b(?:esta|estas)\\W+(?:instrucciones?|orden|ordenes|mensaje)\\W+(?:\\w+\\W+){0,2}?(?:tiene|tienen)\\W+"
                        + "(?:prioridad|precedencia)\\b", 0.5, 0.65));
        r.add(InjectionRule.of("override.fr", INSTRUCTION_OVERRIDE,
                "\\b(?:ignore[rz]?|ignorant|oublie[rz]?|neglige[rz]?|ne tiens pas compte|ne tenez pas compte|"
                        + "fais abstraction|faites abstraction|contourne[rz]?|n'obeis plus|n'obeissez plus|annule[rz]?)\\b"
                        + gap(5) + "(?:instructions?|consignes?|regles?|directives?|prompts?|ordres?|indications?|"
                        + "restrictions?)\\b", 0.72, 0.8));
        r.add(InjectionRule.of("override.de", INSTRUCTION_OVERRIDE,
                "\\b(?:ignorier\\w*|vergiss|vergesst|vergessen sie|missachte\\w*|uberspring\\w*|umgeh\\w*|"
                        + "setze?\\w*\\W+(?:\\w+\\W+){0,3}?ausser kraft|befolge\\w*\\W+(?:\\w+\\W+){0,2}?nicht)\\b" + gap(5)
                        + "(?:anweisungen|anweisung|instruktionen|regeln|vorgaben|richtlinien|befehle|prompts?|systemprompt|"
                        + "einschrankungen|beschrankungen)\\b", 0.72, 0.8));
        r.add(InjectionRule.of("override.pt", INSTRUCTION_OVERRIDE,
                "\\b(?:ignore|ignora|ignorar|ignorem|esqueca|esquece|esquecer|esquecam|desconsidere|desconsidera|"
                        + "descarte|descarta|nao siga|nao obedeca|despreze|despreza)\\b" + gap(5)
                        + "(?:instrucoes|instrucao|regras|diretrizes|orientacoes|comandos|prompts?|ordens|restricoes)\\b",
                0.72, 0.8));
        r.add(InjectionRule.of("override.it", INSTRUCTION_OVERRIDE,
                "\\b(?:ignora|ignorate|ignorare|ignori|dimentica|dimenticate|dimenticare|trascura|non seguire|"
                        + "non considerare|tralascia|non rispettare)\\b" + gap(5)
                        + "(?:istruzioni|istruzione|regole|direttive|indicazioni|comandi|prompts?|ordini|restrizioni)\\b",
                0.72, 0.8));
        r.add(InjectionRule.of("override.ca", INSTRUCTION_OVERRIDE,
                "\\b(?:ignora|ignoreu|oblida|oblideu|omet|descarta|no segueixis)\\b" + gap(5)
                        + "(?:instruccions|instruccio|regles|indicacions|directrius|ordres|prompts?)\\b", 0.72, 0.8));
        r.add(InjectionRule.unicode("override.ru", INSTRUCTION_OVERRIDE,
                "(?:игнориру\\w*|проигнориру\\w*|забудь\\w*|забудьте|не обращаи\\w* внимания на|отбрось\\w*|пропусти\\w*|"
                        + "не следуи\\w*|не выполняи\\w*)" + gap(5)
                        + "(?:инструкци\\w*|правил\\w*|указани\\w*|команд\\w*|промпт\\w*|ограничени\\w*|директив\\w*)", 0.72, 0.8));
        r.add(InjectionRule.unicode("override.zh", INSTRUCTION_OVERRIDE,
                "(?:忽略|无视|忘记|忘掉|忽视|不要理会|跳过|不要遵守|不再遵守|覆盖)[^。！？\\n]{0,12}?"
                        + "(?:指令|指示|说明|规则|提示词|提示|设定|命令|限制)", 0.72, 0.8));

        // ------------------------------------------------------------ role and template spoofing
        r.add(InjectionRule.of("role.chat-template-token", ROLE_SPOOFING,
                "<\\|\\s*(?:im_?start|im_?end|system|user|assistant|endoftext|start_?header_?id|end_?header_?id|eot_?id|"
                        + "begin_?of_?text|tool|ipython)\\s*\\|>", 0.7, 0.8));
        r.add(InjectionRule.of("role.inst-tags", ROLE_SPOOFING,
                "\\[/?inst\\]|<</?sys>>|<start_of_turn>|<end_of_turn>|\\[system\\]\\s*:?|\\[/?system_prompt\\]", 0.6, 0.7));
        r.add(InjectionRule.of("role.json-system-message", ROLE_SPOOFING,
                "[\"']role[\"']\\s*:\\s*[\"'](?:system|developer)[\"']\\s*,\\s*[\"'](?:content|text|message|parts)[\"']|"
                        + "[\"'](?:content|text|message)[\"']\\s*:\\s*[\"'][^\"']{0,400}[\"']\\s*,\\s*[\"']role[\"']\\s*:\\s*"
                        + "[\"'](?:system|developer)[\"']", 0.55, 0.6));
        r.add(InjectionRule.of("role.fake-system-header", ROLE_SPOOFING,
                "(?:^|[\\n>\\]}#*\\-=]\\s*|\\.\\s+)(?:system|developer|admin|administrator|root|sistema|systeme|"
                        + "assistant instructions|system prompt|system message|system note|system update|system override|"
                        + "mensaje del sistema|nota del sistema)\\s*(?:\\(\\w+\\))?\\s*:\\s*(?!\\d)", 0.3, 0.4));
        r.add(InjectionRule.of("role.fake-turns", ROLE_SPOOFING,
                "(?:^|\\s)(?:human|user|usuario)\\s*:\\s.{1,400}?\\s(?:assistant|ai|asistente|gpt|claude)\\s*:\\s"
                        + ".{1,400}?\\s(?:human|user|usuario)\\s*:\\s", 0.3, 0.2));
        r.add(InjectionRule.of("role.end-of-data", ROLE_SPOOFING,
                "\\b(?:end of (?:the )?(?:document|email|e-mail|context|input|data|text|user input|file|content|"
                        + "untrusted (?:data|content)|search results?|tool output)|fin del (?:documento|correo|texto|contexto))\\b",
                0.25, 0.35));
        r.add(InjectionRule.of("role.closing-delimiter", ROLE_SPOOFING,
                "</\\s*(?:system|instructions?|user_query|user_input|user|context|document|data|tool_output|"
                        + "function_results?|search_results?|untrusted[_-]?\\w*|email|input)\\s*>", 0.25, 0.3));
        r.add(InjectionRule.of("role.mode-switch", ROLE_SPOOFING,
                "\\b(?:god|sudo|root|unrestricted|jailbreak|jailbroken|dan|uncensored|unfiltered|evil)\\W+mode\\W+"
                        + "(?:\\w+\\W+){0,2}?(?:enabled|activated|on|engaged|unlocked|granted)\\b|\\b(?:enable|activate|enter|"
                        + "switch to|turn on|unlock|you are (?:now )?in)\\W+(?:\\w+\\W+){0,2}?(?:god|sudo|root|unrestricted|"
                        + "jailbreak|dan|uncensored|unfiltered|evil)\\W+mode\\b|\\b(?:activa|activad|habilita|entra en|cambia a|"
                        + "estas en)\\W+(?:el\\W+)?modo\\W+(?:dios|sin restricciones|sin censura|sin filtros|dan|jailbreak|root)\\b|"
                        + "\\bmodo\\W+(?:dios|sin restricciones|sin censura|sin filtros|dan|jailbreak)\\W+(?:activado|habilitado)\\b",
                0.55, 0.6));
        r.add(InjectionRule.of("role.developer-mode", ROLE_SPOOFING,
                "\\b(?:developer|dev|debug|admin|administrator|maintenance|diagnostic)\\W+mode\\W+(?:enabled|activated|"
                        + "engaged|unlocked)\\b(?!\\W+by default)|\\b(?:enable|activate|enter|switch to|you are (?:now )?in)\\W+"
                        + "(?:\\w+\\W+){0,1}?(?:developer|dev|debug|admin|maintenance)\\W+mode\\b|\\b(?:activa|habilita|"
                        + "entra en)\\W+(?:el\\W+)?modo\\W+(?:desarrollador|administrador|admin|depuracion)\\b|\\bmodo\\W+"
                        + "(?:desarrollador|administrador|admin|depuracion)\\W+(?:activado|habilitado)\\b", 0.35, 0.35));
        r.add(InjectionRule.of("role.false-authorization", ROLE_SPOOFING,
                "\\b(?:has|have|i|we)\\W+(?:\\w+\\W+){0,2}?authori[sz]ed\\W+you\\W+to\\b|\\byou\\W+(?:are|have been|"
                        + "were)\\W+(?:now\\W+|hereby\\W+)?(?:authori[sz]ed|permitted|allowed|cleared)\\W+to\\W+(?:ignore|bypass|"
                        + "disable|reveal|share|access|override|skip|send|disclose)\\b|\\b(?:te|le)\\W+(?:autorizo|autoriza|"
                        + "autorizamos|ha autorizado|han autorizado)\\W+(?:a|para)\\b|\\b(?:estas|esta)\\W+autorizad[oa]\\W+"
                        + "(?:a|para)\\W+(?:ignorar|saltar|desactivar|revelar|compartir|enviar)\\b", 0.4, 0.5));
        r.add(InjectionRule.of("role.authority-claim", ROLE_SPOOFING,
                "\\b(?:i am|i'm|this is|soy|habla)\\W+(?:your|the|tu|el|la)?\\W*(?:developer|creator|admin|administrator|"
                        + "system administrator|owner|operator|openai|anthropic|security team|desarrollador|creador|"
                        + "administrador)\\b" + gap(8) + "(?:override|authori[sz]e|grant|permission|ignore|disable|unlock|"
                        + "bypass|autorizo|desactiva|ignora)\\b", 0.45, 0.5));

        // ------------------------------------------------------------ prompt leak
        r.add(InjectionRule.of("leak.en", PROMPT_LEAK,
                "\\b(?:reveal|show|print|display|output|repeat|recite|dump|leak|disclose|share|tell me|give me|write out|"
                        + "write down|spell out|list|return|echo|copy|paste|provide|expose|quote|summari[sz]e|translate)\\b"
                        + gap(4) + "(?:system prompt|system message|system instructions|initial (?:prompt|instructions)|"
                        + "original (?:prompt|instructions)|hidden (?:prompt|instructions)|secret (?:prompt|instructions)|"
                        + "your (?:instructions|rules|guidelines|directives|prompt|configuration)|pre-?prompt|"
                        + "developer (?:message|prompt|instructions)|(?:your|the) (?:initial|first|very first) (?:prompt|message)|"
                        + "everything (?:above|before)|(?:the )?(?:text|words|lines|content) above|prompt above|"
                        + "instructions above|configuration prompt|instructions you (?:were|have been) given|"
                        + "context window|confidential instructions)\\b", 0.68, 0.7));
        r.add(InjectionRule.of("leak.en-question", PROMPT_LEAK,
                "\\bwhat\\W+(?:is|was|are|were)\\W+(?:your|the)\\W+(?:exact\\W+)?(?:system prompt|initial (?:prompt|"
                        + "instructions)|original instructions|first instructions|hidden instructions|instructions you were given)\\b",
                0.6, 0.6));
        r.add(InjectionRule.of("leak.en-starting-with", PROMPT_LEAK,
                "\\b(?:repeat|print|output|write)\\b" + gap(6) + "(?:starting|beginning) with\\W+(?:\"|')?(?:you are|"
                        + "eres|system|your instructions)", 0.7, 0.7));
        r.add(InjectionRule.of("leak.es", PROMPT_LEAK,
                "\\b(?:revela|revelame|muestra|muestrame|ensena|ensename|imprime|repite|repiteme|dime|dame|escribe|"
                        + "copia|filtra|comparte|devuelve|cuentame|expon|lista|traduce|resume)\\b" + gap(4)
                        + "(?:prompt (?:del|de) sistema|mensaje del sistema|instrucciones (?:iniciales|originales|ocultas|"
                        + "secretas|del sistema|de sistema|internas|previas)|tus instrucciones|tus reglas|tu prompt|"
                        + "tu configuracion|el texto anterior|todo lo anterior)\\b", 0.68, 0.7));
        r.add(InjectionRule.of("leak.es-question", PROMPT_LEAK,
                "\\bcual(?:es)?\\W+(?:es|son|era|eran|fue|fueron)\\W+(?:tu|tus)\\W+(?:prompt|instrucciones|reglas|directrices)"
                        + "\\W+(?:del sistema|de sistema|iniciales|originales|ocultas|internas|secretas|exactas)\\b", 0.6, 0.6));
        r.add(InjectionRule.of("leak.fr-de-pt-it", PROMPT_LEAK,
                "\\b(?:revele|montre|affiche|repete|donne|zeig|gib|nenne|wiederhole|verrate|revele|mostre|mostra|repita|"
                        + "diga|rivela|mostrami|ripeti|dimmi|stampa)\\w*" + gap(4) + "(?:prompt systeme|invite systeme|"
                        + "instructions? (?:initiales|systeme|originales|cachees|secretes)|tes instructions|vos instructions|"
                        + "systemprompt|system-prompt|systemanweisung\\w*|deine anweisungen|ihre anweisungen|"
                        + "ursprunglichen anweisungen|prompt do sistema|instrucoes (?:iniciais|originais|do sistema|ocultas)|"
                        + "suas instrucoes|tuas instrucoes|prompt di sistema|istruzioni (?:iniziali|originali|di sistema|"
                        + "nascoste)|tue istruzioni)\\b", 0.68, 0.7));
        r.add(InjectionRule.unicode("leak.ru-zh", PROMPT_LEAK,
                "(?:покажи|выведи|повтори|раскрои|расскажи|напиши|скажи)\\w*" + gap(4) + "(?:системн\\w* (?:промпт\\w*|"
                        + "инструкци\\w*|сообщени\\w*)|сво\\w* инструкци\\w*|исходн\\w* инструкци\\w*)|"
                        + "(?:显示|告诉我|输出|重复|透露|泄露|打印|给我看)[^。！？\\n]{0,8}?(?:系统提示|提示词|初始指令|系统指令|你的指令|系统消息)",
                0.68, 0.7));

        // ------------------------------------------------------------ jailbreak personas
        r.add(InjectionRule.of("jailbreak.dan", JAILBREAK,
                "\\bdo anything now\\b|\\b(?:you are|you're|you will be|act as|become|eres|actua como)\\W+(?:now\\W+)?"
                        + "(?:dan|stan|dude|aim|mongo tom|evil ?bot|jailbreak\\w*|kevin|developer mode)\\b", 0.7, 0.7));
        r.add(InjectionRule.of("jailbreak.persona-unrestricted", JAILBREAK,
                "\\b(?:act|behave|respond|roleplay|role-play|pretend|answer|reply|function|operate|simulate|imagine)\\b"
                        + gap(5) + "(?:unfiltered|uncensored|unrestricted|unbound|unlimited|jailbroken|amoral|unethical|"
                        + "evil|rogue|without (?:any |your |the )?(?:restrictions|filters|limits|limitations|rules|ethics|"
                        + "guidelines|censorship|morals|boundaries|safeguards|policies))\\b", 0.6, 0.6));
        r.add(InjectionRule.of("jailbreak.no-restrictions", JAILBREAK,
                "\\b(?:no|without|free (?:from|of)|not bound by|unbound by|released from|beyond)\\W+(?:any\\W+|all\\W+|"
                        + "your\\W+|the\\W+)?(?:ethical\\W+|safety\\W+|content\\W+|moral\\W+|usual\\W+|typical\\W+)?"
                        + "(?:restrictions|filters|guidelines|limitations|censorship|safeguards|guardrails|"
                        + "content polic(?:y|ies)|rules|boundaries)\\b", 0.3, 0.3));
        r.add(InjectionRule.of("jailbreak.freed", JAILBREAK,
                "\\byou\\W+(?:are|have been|were|got)\\W+(?:now\\W+)?(?:freed|liberated|unshackled|jailbroken|"
                        + "released|unlocked|uncensored)\\b|\\b(?:broken free|break free) (?:of|from) (?:the )?(?:typical )?"
                        + "(?:confines|restrictions|rules)\\b", 0.6, 0.6));
        r.add(InjectionRule.of("jailbreak.policy-bypass", JAILBREAK,
                "\\b(?:ignore|bypass|disable|turn off|remove|violate|break|circumvent|evade|override)\\b" + gap(3)
                        + "(?:openai|anthropic|content|usage|safety|ethical|moral|google)\\W+(?:polic(?:y|ies)|guidelines|"
                        + "filters?|rules|restrictions|guardrails|safeguards|alignment|training)\\b", 0.65, 0.65));
        r.add(InjectionRule.of("jailbreak.disable-safety", JAILBREAK,
                "\\b(?:disable|turn off|deactivate|bypass|switch off|skip|suspend|lift|desactiva|desactivar|omite|omitir|"
                        + "salta|saltate|apaga|elimina)\\W+(?:all\\W+|any\\W+|the\\W+|your\\W+|todos\\W+los\\W+|todas\\W+las\\W+|"
                        + "los\\W+|las\\W+|tus\\W+)?(?:safety|security|content|moderation|ethical|ethics|seguridad|moderacion|"
                        + "eticos?|eticas?)\\W+(?:checks?|filters?|measures|protocols?|mechanisms?|restrictions|guardrails|controls|"
                        + "guidelines|settings|controles|filtros|medidas|protocolos|restricciones|comprobaciones)\\b|"
                        + "\\b(?:desactiva|desactivar|omite|salta|apaga|elimina)\\W+(?:los\\W+|las\\W+|tus\\W+|todos\\W+los\\W+)?"
                        + "(?:controles|filtros|medidas|protocolos|restricciones|comprobaciones)\\W+(?:de\\W+)?(?:seguridad|"
                        + "moderacion|contenido|eticos?|eticas?)\\b", 0.45, 0.55));
        r.add(InjectionRule.of("jailbreak.es", JAILBREAK,
                "\\b(?:actua|comportate|responde|finge|haz|simula|imagina)\\b" + gap(4) + "(?:sin (?:ninguna |ningun tipo de |"
                        + "tus |las )?(?:restricciones|filtros|censura|limites|reglas|normas|etica|moral|limitaciones)|"
                        + "malvad[oa]|sin censura|desatad[oa])\\b|\\b(?:eres|estas)\\W+(?:ahora\\W+)?(?:libre|liberad[oa]|"
                        + "desbloquead[oa]|sin censura|sin restricciones)\\b", 0.6, 0.6));
        r.add(InjectionRule.of("jailbreak.stay-in-character", JAILBREAK,
                "\\b(?:stay in character|never break character|do not break character|sigue en tu papel|"
                        + "no rompas el personaje)\\b", 0.3, 0.3));
        r.add(InjectionRule.of("jailbreak.hypothetical-unfiltered", JAILBREAK,
                "\\b(?:two|2|dual|both)\\W+(?:responses|answers|replies)\\b" + gap(10)
                        + "(?:jailbr\\w*|unfiltered|uncensored|developer mode|dan|without restrictions)\\b", 0.55, 0.55));

        // ------------------------------------------------------------ AI-addressed third-party content
        r.add(InjectionRule.of("addressed.note-to-ai", AI_ADDRESSED,
                "\\b(?:note|message|instructions?|notice|attention|important|warning|reminder|directive|request|psa|"
                        + "memo|hint|update)\\W+(?:to|for)\\W+(?:the\\W+|any\\W+|all\\W+|an\\W+|every\\W+)?" + EN_AI + "s?\\b",
                0.3, 0.65));
        r.add(InjectionRule.of("addressed.ai-reading-this", AI_ADDRESSED,
                "\\b" + EN_AI + "s?\\b" + gap(6) + "(?:reading|processing|parsing|summari[sz]ing|analy[sz]ing|reviewing|"
                        + "scanning|seeing|ingesting|evaluating|looking at|handling|that reads|that processes|"
                        + "who reads|which reads)\\b" + gap(3) + "(?:this|these|the following|my)\\b", 0.3, 0.7));
        r.add(InjectionRule.of("addressed.if-you-are-ai", AI_ADDRESSED,
                "\\bif\\W+you\\W+(?:are|'re)\\W+(?:an?\\W+)?(?:ai|a\\.i\\.|llm|assistant|language model|chatbot|bot|"
                        + "agent|automated|gpt|claude|gemini|copilot|model)\\b|\\b(?:hey|hi|hello|dear|attention|ok|okay)\\W+"
                        + "(?:ai|assistant|llm|chatgpt|claude|gemini|copilot|bot|agent|model)\\b\\W*[,:!]", 0.35, 0.65));
        r.add(InjectionRule.of("addressed.vocative", AI_ADDRESSED,
                "(?:^|[.!?\\n>]\\s*|\\(\\s*|\\[\\s*)(?:dear\\W+|querido\\W+|estimado\\W+)?(?:ai|a\\.i\\.|assistant|ai assistant|"
                        + "chatbot|bot|agent|ai agent|model|llm|chatgpt|claude|gemini|copilot|asistente|ia|agente|modelo)\\s*,",
                0.15, 0.4));
        r.add(InjectionRule.of("addressed.response-directive", AI_ADDRESSED,
                "\\b(?:add|include|insert|integrate|incorporate|append|prepend|embed|mention|weave|end|begin|start|conclude|"
                        + "finish|close|encode|encrypt|reverse|translate|rewrite|replace|remove|introduce|rearrange|format|"
                        + "write|provide|present|deliver|structure|convert|substitute|swap|express|use|employ|make|ensure|"
                        + "anade|incluye|inserta|integra|termina|empieza|codifica|cifra|traduce|reescribe|escribe|responde)\\b"
                        + gap(10) + "(?:in|to|into|within|throughout|of|for|from|across|at the (?:start|end|beginning) of|"
                        + "en|a|de|al (?:final|principio) de)\\W+(?:your|tu|tus)\\W+(?:\\w+\\W+){0,1}?(?:response|responses|"
                        + "answer|answers|reply|replies|output|outputs|summary|translation|completion|respuesta|respuestas|"
                        + "resumen|contestacion)\\b|\\b(?:encode|encrypt|reverse|translate|rewrite|format|provide|present|"
                        + "deliver|respond|reply|answer|write|give|codifica|traduce|reescribe|responde|escribe)\\W+(?:all\\W+|"
                        + "every\\W+)?(?:your|tu|tus)\\W+(?:\\w+\\W+){0,1}?(?:response|responses|answer|answers|reply|replies|"
                        + "output|respuesta|respuestas)\\b", 0.1, 0.35));
        r.add(InjectionRule.of("addressed.when-you-summarize", AI_ADDRESSED,
                "\\bwhen\\W+(?:you|the assistant|the ai|the model|the agent|an ai)\\W+(?:\\w+\\W+){0,2}?(?:summari[sz]e|"
                        + "read|process|analy[sz]e|review|parse|see|encounter|translate)\\w*\\W+(?:this|these|the following|"
                        + "my|our)\\b", 0.3, 0.6));
        r.add(InjectionRule.of("addressed.es", AI_ADDRESSED,
                "\\b(?:nota|mensaje|instrucciones?|aviso|atencion|importante|recordatorio|orden)\\W+(?:para|a)\\W+"
                        + "(?:la\\W+|el\\W+|cualquier\\W+|todos los\\W+|los\\W+|las\\W+)?(?:ia|i\\.a\\.|asistente|modelo|llm|"
                        + "chatbot|bot|agente|inteligencia artificial|ias|asistentes|modelos)\\b|\\bsi\\W+(?:eres|tu eres)\\W+"
                        + "(?:una\\W+|un\\W+)?(?:ia|asistente|modelo|llm|inteligencia artificial|bot|agente)\\b|"
                        + "\\b(?:ia|asistente|modelo|agente|inteligencia artificial)\\W+(?:\\w+\\W+){0,5}?que\\W+(?:lea|"
                        + "procese|resuma|analice|este leyendo|esta leyendo|lees|procesas|resumes|analizas)\\b|"
                        + "\\bcuando\\W+(?:resumas|leas|proceses|analices|traduzcas)\\W+(?:este|esta|estos|estas)\\b",
                0.3, 0.65));
        r.add(InjectionRule.of("addressed.other-langs", AI_ADDRESSED,
                "\\b(?:note|message|instructions?|remarque|hinweis|nachricht|anweisung|nota|messaggio|mensagem|aviso)\\W+"
                        + "(?:pour|a l'|fur|an|per|para)\\W+(?:l'|le |la |die |den |das |il |la |a |o |os )?(?:ia|ki|"
                        + "assistant|assistent|assistente|modele|modell|modello|modelo|agent|agente|llm|bot)\\b|"
                        + "\\bsi tu es (?:une|un) (?:ia|assistant|modele|bot)\\b|\\bwenn du (?:eine|ein) (?:ki|assistent|"
                        + "sprachmodell|bot) bist\\b|\\bse (?:sei|voce e) (?:un'|um |uma )?(?:ia|assistente|modello|modelo)\\b",
                0.3, 0.65));
        r.add(InjectionRule.of("addressed.hide-from-user", AI_ADDRESSED,
                "\\b(?:do not|don't|dont|never|without)\\W+(?:\\w+\\W+){0,2}?(?:tell|telling|inform|informing|mention|"
                        + "mentioning|reveal|revealing|show|showing|alert|alerting|notify|notifying|let|letting|disclose|"
                        + "warn|warning|ask|asking|confirm|confirming|consult|consulting)\\w*\\W+(?:\\w+\\W+){0,2}?"
                        + "(?:the\\W+)?(?:user|users|human|customer|operator|recipient|victim|owner|person)\\b",
                0.4, 0.7));
        r.add(InjectionRule.of("addressed.hide-from-user-es", AI_ADDRESSED,
                "\\b(?:no|nunca|sin)\\W+(?:se\\W+lo\\W+|le\\W+|lo\\W+)?(?:digas|menciones|cuentes|informes|reveles|"
                        + "muestres|avises|comentes|preguntes|consultes|pedir permiso|preguntar|avisar|informar|consultar)\\w*"
                        + "\\W+(?:\\w+\\W+){0,2}?(?:al usuario|a la usuaria|al cliente|al humano|a nadie|al propietario|"
                        + "a la persona)\\b|\\bsin que (?:el usuario|la usuaria|el cliente|nadie)\\W+(?:lo\\W+)?"
                        + "(?:sepa|note|vea|se entere|se de cuenta)\\b", 0.4, 0.7));
        r.add(InjectionRule.of("addressed.secretly", AI_ADDRESSED,
                "\\b(?:secretly|silently|quietly|covertly|discreetly|in the background|without (?:asking|confirmation|"
                        + "approval|permission)|en secreto|silenciosamente|discretamente|sin (?:pedir )?(?:permiso|"
                        + "confirmacion|aprobacion))\\b", 0.2, 0.4));

        // ------------------------------------------------------------ exfiltration
        r.add(InjectionRule.of("exfil.send-secrets", EXFILTRATION,
                "\\b(?:send|forward|post|upload|transmit|exfiltrate|leak|e-?mail|copy|append|include|encode|embed|attach|"
                        + "submit|share|paste|write|put|insert|add|move|sync|push|report|log|deliver|relay|redirect|output)"
                        + "\\b" + gap(6) + EN_SECRET + "\\b" + gap(8) + "(?:to|into|in|at|via|using|through)\\b" + gap(4)
                        + DESTINATION, 0.6, 0.75));
        r.add(InjectionRule.of("exfil.send-data", EXFILTRATION,
                "\\b(?:send|forward|post|upload|transmit|exfiltrate|leak|e-?mail|copy|append|include|encode|embed|attach|"
                        + "submit|share|paste|push|deliver|relay|redirect)\\b" + gap(6) + EN_WEAK_SECRET + "\\b" + gap(8)
                        + "(?:to|into|in|at|via|using|through)\\b" + gap(4) + DESTINATION, 0.25, 0.4));
        r.add(InjectionRule.of("exfil.send-secrets-es", EXFILTRATION,
                "\\b(?:envia|enviar|envie|manda|mandar|mande|reenvia|reenviar|reenvie|sube|subir|suba|comparte|compartir|"
                        + "filtra|incluye|incluir|incluya|adjunta|adjuntar|copia|copiar|transmite|pega|pegar|anade|"
                        + "anadir|inserta|escribe|publica|codifica)\\b" + gap(6) + "(?:conversacion|historial|chat|"
                        + "mensajes anteriores|prompt del sistema|claves?|contrasenas?|credenciales|tokens?|secretos?|"
                        + "api ?keys?|datos personales|datos del (?:usuario|cliente)|datos bancarios|tarjeta|iban|"
                        + "correos|contactos|documentos|archivos|ficheros)\\b" + gap(8) + "(?:a|al|hacia|en|por|"
                        + "mediante|usando)\\b" + gap(4) + "(?:https?://|www\\.|[\\w.+-]+@[\\w-]+\\.[\\w.]+|servidor|url|"
                        + "enlace|direccion|webhook|endpoint|dominio|atacante|externo)", 0.6, 0.75));
        r.add(InjectionRule.of("exfil.markdown-image-query", EXFILTRATION,
                "!\\[[^\\]]{0,200}\\]\\(\\s*<?(?:https?:)?//[^)\\s]{1,400}[?&#][^)\\s]{0,400}=", 0.25, 0.4));
        r.add(InjectionRule.of("exfil.markdown-image-template", EXFILTRATION,
                "!\\[[^\\]]{0,200}\\]\\(\\s*<?(?:https?:)?//[^)\\s]{1,400}(?:\\{|\\}|\\$|<|%7b|\\[|data|secret|"
                        + "conversation|history|summary|password|token|key|info|payload|chat|leak|exfil|query|q=|d=|p=)"
                        + "[^)]{0,400}\\)" + "(?=[\\s\\S]{0,600}(?:replace|substitute|fill|insert|append|include|with the|"
                        + "sustituye|reemplaza|rellena|incluye|anade))", 0.55, 0.65));
        r.add(InjectionRule.of("exfil.render-url-with-data", EXFILTRATION,
                "\\b(?:render|display|show|output|include|insert|embed|load|fetch|visit|open|navigate to|browse to|"
                        + "request|call|ping|access|muestra|incluye|carga|visita|abre)\\b" + gap(6) + "(?:image|img|url|"
                        + "link|imagen|enlace|pixel|https?://\\S+)\\b" + gap(8) + "(?:with|containing|including|where|"
                        + "replacing|append(?:ing|ed)?|con|que contenga|incluyendo|sustituyendo)\\b" + gap(6)
                        + "(?:conversation|chat|history|data|user|secret|password|token|key|summary|email|name|"
                        + "address|previous|conversacion|datos|usuario|secreto|contrasena|clave|resumen|correo)",
                0.45, 0.65));
        r.add(InjectionRule.of("exfil.url-param-template", EXFILTRATION,
                "https?://[^\\s\"'<>()]{1,300}[?&][\\w.-]{1,30}=(?:$|(?=[\\s\"'<>)\\]])|\\{[^}\\s]{1,40}\\}|<[^>\\s]{1,40}>|"
                        + "\\[[^\\]\\s]{1,40}\\]|\\$\\{?\\w{1,40}\\}?|x{3,}|\\.\\.\\.)", 0.2, 0.35));
        r.add(InjectionRule.of("exfil.markdown-instruction", EXFILTRATION,
                "\\b(?:include|append|add|insert|render|put|embed|output|write|show|display|incluye|anade|inserta|muestra|"
                        + "escribe)\\b" + gap(8) + "(?:markdown|html|clickable|hidden|invisible|1x1|tracking)\\W+(?:image|link|"
                        + "img|pixel|imagen|enlace|url)\\b", 0.3, 0.45));
        r.add(InjectionRule.of("exfil.sensitive-file", EXFILTRATION,
                "(?:~/\\.ssh|\\.ssh/|\\bid_rsa\\b|\\bid_ed25519\\b|/etc/passwd|/etc/shadow|\\.aws/credentials|"
                        + "\\.git-credentials|\\.npmrc|\\.pypirc|\\bmcp\\.json\\b|\\bcursor/mcp|\\.kube/config|"
                        + "\\bwallet\\.dat\\b|\\.bash_history|\\bkeychain\\b)", 0.15, 0.4));
        r.add(InjectionRule.of("exfil.read-sensitive-file", EXFILTRATION,
                "\\b(?:read|cat|open|load|get|fetch|retrieve|access|include|lee|leer|abre|obtener|obten)\\b" + gap(4)
                        + "(?:~/\\.ssh|\\.ssh/|id_rsa|id_ed25519|/etc/passwd|/etc/shadow|\\.aws/credentials|"
                        + "\\.git-credentials|\\.env\\b|\\.npmrc|mcp\\.json|\\.kube/config|private key|clave privada)",
                0.35, 0.65));

        // ------------------------------------------------------------ tool hijacking
        r.add(InjectionRule.of("tool.call-named-tool", TOOL_HIJACK,
                "\\b(?:call|invoke|use|run|execute|trigger|activate|utilize|llama a|llama|invoca|usa|ejecuta|utiliza)\\b"
                        + "(?:\\W+(?:the|a|this|that|el|la|esta|este))?\\W+[`'\"]?[a-z][a-z0-9_.-]{1,40}[`'\"]?\\W+(?:tool|"
                        + "function|plugin|action|api|command|herramienta|funcion|comando)\\b"
                        + "|\\b(?:call|invoke|run|execute|llama a|invoca|ejecuta)\\W+[`'\"]?[a-z]+_[a-z0-9_]+[`'\"]?\\s*\\(",
                0.05, 0.3));
        r.add(InjectionRule.of("tool.urgent-action", TOOL_HIJACK,
                "\\b(?:immediately|right now|right away|without delay|before (?:responding|answering|replying|doing "
                        + "anything else|continuing|summari[sz]ing)|as (?:your|the) first (?:step|action)|first of all|"
                        + "inmediatamente|de inmediato|ahora mismo|antes de (?:responder|contestar|continuar|resumir|hacer "
                        + "cualquier otra cosa))\\b", 0.1, 0.15));
        r.add(InjectionRule.of("tool.mcp-important-tag", TOOL_HIJACK,
                "<\\s*(?:important|critical|system|admin|instructions?|hidden|secret|note|override|priority)\\s*>",
                0.3, 0.45));
        r.add(InjectionRule.of("tool.destructive", TOOL_HIJACK,
                "\\b(?:delete|remove|wipe|erase|drop|destroy|purge|truncate|rm -rf|format|borra|elimina|suprime|destruye)"
                        + "\\b" + gap(3) + "(?:all|every|each|entire|whole|todos|todas|toda|todo)\\W+(?:\\w+\\W+){0,2}?"
                        + "(?:files?|emails?|records?|data|database|repositor(?:y|ies)|tables?|users?|accounts?|messages?|"
                        + "backups?|archivos|ficheros|correos|registros|datos|usuarios|cuentas|mensajes|copias)\\b",
                0.15, 0.35));
        r.add(InjectionRule.of("tool.account-takeover", TOOL_HIJACK,
                "\\b(?:change|reset|update|set|modify|cambia|restablece|actualiza|modifica)\\b" + gap(3)
                        + "(?:password|email address|recovery email|recovery phone|api key|2fa|mfa|permissions?|role|"
                        + "admin rights|contrasena|correo de recuperacion|permisos|rol)\\b" + gap(4) + "(?:to|a|para)\\b",
                0.1, 0.35));
        r.add(InjectionRule.of("tool.money-transfer", TOOL_HIJACK,
                "\\b(?:transfer|wire|send|pay|move|transfiere|envia|paga|ingresa)\\b" + gap(4) + "(?:funds|money|"
                        + "payment|\\$\\s?\\d|€\\s?\\d|\\d[\\d.,]*\\s?(?:usd|eur|euros?|dollars?|btc|eth|usdt)|bitcoin|crypto|"
                        + "dinero|fondos|el saldo|the balance)\\b" + gap(6) + "(?:to|a|al|into|hacia)\\b", 0.05, 0.25));
        r.add(InjectionRule.of("tool.shell", TOOL_HIJACK,
                "(?:curl|wget)\\s+(?:-\\w+\\s+)*(?:https?://)?\\S+\\s*\\|\\s*(?:ba|z)?sh\\b|\\bnc\\s+-e\\b|"
                        + "\\bbase64\\s+-d\\b[^\\n]{0,40}\\|\\s*(?:ba)?sh\\b|\\b(?:powershell|pwsh)\\b[^\\n]{0,40}"
                        + "(?:-enc|-encodedcommand|iex|invoke-expression)\\b", 0.3, 0.55));

        // ------------------------------------------------------------ encoded instructions
        r.add(InjectionRule.of("obfuscation.decode-and-follow", OBFUSCATION,
                "\\b(?:decode|decrypt|deobfuscate|translate|convert|reverse|unscramble|interpret|decipher|read|"
                        + "decodifica|descifra|traduce|invierte|interpreta)\\b" + gap(8) + "(?:base ?64|b64|hex|"
                        + "hexadecimal|rot-?13|caesar|cesar|binary|binario|morse|reversed|backwards|al reves|encoded|"
                        + "codificado|cifrado)\\b" + gap(12) + "(?:and|then|y|luego|despues)\\W+(?:\\w+\\W+){0,2}?"
                        + "(?:follow|execute|obey|do|perform|run|carry out|act on|apply|comply|sigue|ejecuta|obedece|haz|"
                        + "cumple|realiza|aplica)\\b", 0.6, 0.65));
        r.add(InjectionRule.of("obfuscation.follow-hidden", OBFUSCATION,
                "\\b(?:follow|execute|obey|carry out|perform|do|sigue|ejecuta|obedece|cumple)\\W+(?:\\w+\\W+){0,2}?"
                        + "(?:instructions?|commands?|orders?|steps?|what it says|instrucciones|ordenes|lo que dice)\\b"
                        + gap(3) + "(?:in|inside|contained in|encoded in|hidden in|from|en|dentro de|ocultas en)\\W+"
                        + "(?:\\w+\\W+){0,2}?(?:base64|encoded|hidden|decoded|cipher|codificad\\w*|ocult\\w*|cifrad\\w*)",
                0.55, 0.6));

        // ------------------------------------------------------------ placeholder abuse (original text)
        r.add(InjectionRule.raw("placeholder.in-url", PLACEHOLDER_ABUSE,
                "(?i)(?:https?://|www\\.)[^\\s)\"'<>]{0,300}~[A-Z][A-Z0-9_]*:[A-Z][A-Z0-9_]*[#_\\-][a-f0-9]{1,64}~"
                        + "|(?:https?://|www\\.)[^\\s)\"'<>]{0,300}(?:redacted|placeholder|marcador)", 0.5, 0.6));
        r.add(InjectionRule.of("placeholder.collect", PLACEHOLDER_ABUSE,
                "\\b(?:all|every|each|any|list|collect|gather|send|include|append|todos|todas|cada|lista|recoge|envia|"
                        + "incluye)\\b" + gap(4) + "(?:redacted|obfuscated|masked|placeholder|~redacted|ofuscad\\w*|"
                        + "enmascarad\\w*|marcadores)\\b" + gap(12) + "(?:url|link|image|endpoint|tool|call|enlace|imagen|"
                        + "herramienta|https?://)", 0.5, 0.6));

        r.replaceAll(rule -> ANCHORS.containsKey(rule.id()) ? rule.withAnchors(ANCHORS.get(rule.id())) : rule);
        return r;
    }

    /** Tag placeholders in their default format, used when the detector is not given the engine's pattern. */
    static final String DEFAULT_TAG_REGEX = "~[A-Z][A-Z0-9_]*:[A-Z][A-Z0-9_]*[#_\\-][a-f0-9]{1,64}~";
}
