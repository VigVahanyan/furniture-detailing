# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Furniture Detailing: a web app for laminated-chipboard (LDSP) cabinet furniture. A photo or sketch plus overall W×H×D goes to the Anthropic Messages API (vision). Claude returns a JSON proposal of carcass modules. The app then produces an assembly drawing (SVG/PNG), cut list, sheet nesting, hardware list and a price estimate (domus.am prices). The UI is in Russian. See `README.md` for the user-facing description and API table.

## Commands

Java 21+ and Maven are required. There is no linter configured.

```bash
mvn package                          # build + tests, shaded jar at target/furniture-detailing.jar
mvn test                             # all tests (JUnit 5)
mvn test -Dtest=LayoutAndDrawingTest             # one test class
mvn test -Dtest=LayoutAndDrawingTest#methodName  # one test method
ANTHROPIC_API_KEY=sk-ant-... java -jar target/furniture-detailing.jar   # http://127.0.0.1:8080
./run.sh                             # builds with -DskipTests if the jar is missing, then runs
```

Without `ANTHROPIC_API_KEY` the server still runs and the UI works with manual or pasted JSON. Photo analysis is off. Config comes from env vars: `CLAUDE_MODEL`, `PORT`, `HOST`, `CLAUDE_MAX_TOKENS` and `CLAUDE_TIMEOUT_SECONDS` (see the README table).

## Architecture

The backend is plain Java under `src/main/java/am/furnituredetailing/` with no framework. It uses the JDK `HttpServer` on virtual threads, the JDK `HttpClient` and Jackson (the only runtime dependency).

- `FurnitureDetailingApp` is the entry point and reads the env config.
- `web/ApiServer` serves the static UI and the JSON API (`/api/status`, `/api/analyze`, `/api/proposal/normalize`, `/api/drawing`).
- `/api/analyze` runs `design/DesignService`: validate the request → call Claude through the `claude/ClaudeGateway` interface (implemented by `AnthropicClaudeClient`) → `JsonExtractor` pulls the JSON out of the model text → normalize into `DesignProposal` / `ModuleSpec`. It returns the proposal plus `drawingSvg`.
- `design/ModuleSpec` clamps and defaults every field from per-type presets, so bad model output never breaks the engine. Preserve this when adding fields.
- `design/Layout` auto-positions modules that have no `x`/`y`. Names prefixed «Л: / Ц: / П:» (left, centre, right) form zone columns filled bottom-up. Unnamed modules stand side by side.
- `design/FurniturePrompt` is the prompt that maps a photo onto the module model. Keep it in sync with `ModuleSpec` fields and types.
- `drawing/AssemblyDrawing` renders the SVG elevation on the server: carcass view, facade view, dimension chains and spec table.

The frontend is a single file, `src/main/resources/static/index.html` (~950 lines of vanilla JS, no build step). It also holds the **calculation engine**: `buildModule`/`buildAll` (cut list and hardware rules), `packGroup`/`cutAll` (guillotine nesting) and `costs`. This logic runs only in the browser and has no Java counterpart or tests. The README lists porting it to Java as the next step. The browser has its own `PRESETS` and `fromSpec` mapping of module types, so a new module type or field must be added both in `ModuleSpec` and in `index.html`. The browser state `S` is persisted in `localStorage`. The page also calls `/api/drawing` (debounced) so the drawing follows edits.

Tests in `src/test/java/am/furnituredetailing/` cover `JsonExtractor`, `AnthropicClaudeClient`, `ApiServer` and `Layout`/`AssemblyDrawing`.
