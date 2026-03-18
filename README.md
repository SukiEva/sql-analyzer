# SQL Analyzer (DataGrip Plugin)

A DataGrip-focused query plan viewer inspired by [pev2](https://github.com/dalibo/pev2).

## What changed

This plugin now focuses on three layers:

1. **DataGrip integration**: capture the current editor statement, execute `EXPLAIN (FORMAT JSON)` against the active DataGrip connection, and open the result in the analyzer.
2. **JCEF plan viewer**: render a pev2-inspired dark execution-plan UI with search, node details, settings, and metrics inside the IDE.
3. **Structured parser/model**: parse PostgreSQL `EXPLAIN (FORMAT JSON)` into a typed tree instead of rendering raw `JsonNode` values directly.

## Usage

1. Open a PostgreSQL SQL file or DataGrip console attached to a live connection.
2. Put the caret inside a statement or select a query.
3. Run **Open in SQL Analyzer** from the editor context menu.
4. The plugin will execute `EXPLAIN (FORMAT JSON)` automatically and render the plan in the **SQL Analyzer** tool window.
5. You can still paste JSON manually and click **Analyze JSON** for offline inspection.

<!-- Plugin description -->
SQL Analyzer brings a pev2-inspired PostgreSQL plan viewer into DataGrip.
It captures SQL from the active editor, executes `EXPLAIN (FORMAT JSON)` through the current DataGrip connection, parses the result into a structured model, and renders the plan in a JCEF-based tool window with search and node details.
<!-- Plugin description end -->

## Status

Current scope is intentionally focused on PostgreSQL JSON plans in DataGrip-connected editors. The next iteration can improve statement detection, richer PostgreSQL telemetry, and packaging polish.
