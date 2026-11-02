# StreamLens

Paste a Java 8 Stream pipeline, get a verified step-by-step explanation of how the data
flows through it — not a guess from an LLM, but real intermediate values produced by
actually compiling and running an instrumented version of your code in a sandboxed
subprocess. An optional Gemini call then rephrases those verified facts into
beginner-friendly English.

## MVP scope (day 1)

- Exactly one Stream pipeline per request, started as `someVariable.stream()...` on a
  `List`/`Set` of primitives or Strings declared earlier in the snippet.
- Supported intermediate ops: `filter`, `map`, `flatMap`, `sorted`, `distinct`, `limit`,
  `skip`, `peek`.
- Supported terminal ops: `collect`, `forEach`, `reduce`, `count`, `anyMatch`, `allMatch`,
  `noneMatch`, `findFirst`, `findAny`, `min`, `max`.
- No custom classes, no multi-pipeline snippets, no click-to-explain yet — see Roadmap.

## Run locally

Requires a JDK 17+ (a full JDK, not a JRE — the app shells out to `javac`/`java` from
its own `java.home` to compile and run the sandboxed snippet) and Maven.

```bash
mvn spring-boot:run
```

Open http://localhost:8080 — a minimal static page is served directly from the backend
(no separate frontend build needed for day 1).

### Enable AI explanations (optional)

Get a free API key at https://aistudio.google.com/apikey (no credit card required), then:

```bash
export GEMINI_API_KEY=your-key-here
mvn spring-boot:run
```

Without a key, the endpoint still works and returns the fully verified, deterministic
explanation — the AI layer is additive, not required.

## Test it

```bash
curl -s -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{"code": "List<String> names = Arrays.asList(\"Alice\", \"Bob\", \"Charlie\", \"Dave\");\nList<String> result = names.stream().filter(n -> n.length() > 3).map(String::toUpperCase).collect(Collectors.toList());"}' \
  | python3 -m json.tool
```

Or just use the web page at http://localhost:8080 — it has a few built-in examples.

## Deploy for free

Backend needs a real JVM host (it shells out to `javac`/`java`), not a serverless
function.

1. Push this repo to GitHub.
2. Create a free Render web service (render.com), connect the repo.
   - Build command: `mvn clean package -DskipTests`
   - Start command: `java -jar target/streamlens-0.1.0.jar`
   - **Important**: Render's default Java environment must provide a full JDK (not
     JRE-only) since the app invokes `javac` at runtime. If using a custom Docker
     deploy instead, base the image on `eclipse-temurin:17-jdk`, not a `-jre` variant.
   - Add the `GEMINI_API_KEY` environment variable if you want AI explanations live.
3. Render gives you a free `https://<your-app>.onrender.com` URL. Free tier sleeps after
   ~15 min idle; first request after sleep takes ~30-50s to wake up — normal, not broken.

## Known limitations (documented on purpose, not hidden)

- Intermediate value "snapshots" are produced by re-running each prefix of the pipeline
  with a fresh `.collect(Collectors.toList())`, not by streaming peek() calls — simpler
  and more correct for showing stage-by-stage data, at the cost of re-executing the
  pipeline prefix N times. Fine for snippet-sized data.
- Output parsing assumes `List.toString()` formatting (`[a, b, c]`) and splits naively on
  `", "` — will misparse if element values themselves contain literal commas.
- Sandbox isolation is process-level (separate OS process, timeout, capped heap,
  blocklist on dangerous APIs in copied declaration code) — appropriate for a low-traffic
  portfolio demo, not hardened multi-tenant isolation. A production version would add
  OS-level sandboxing (containers/seccomp per run).

## Roadmap

- Phase 2: proper React frontend (Monaco editor) replacing the static page, same backend.
- Phase 3: custom classes, more operations, flow diagram, tests, polish.
- Phase 4: click-to-explain any token, follow-up Q&A, blog posts per Stream operator.
