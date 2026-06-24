# llms.txt Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add sphinx-llms-txt to the Tapir Sphinx build so that `llms.txt` and `llms-full.txt` are generated alongside the HTML docs.

**Architecture:** The `sphinx-llms-txt` Sphinx extension is added to the existing build. It reads the toctree and emits two plain-text files into the HTML output directory. No new build steps, no new infrastructure — it hooks into `sphinx-build` automatically once the extension is in `conf.py`. Because mdoc copies `doc/conf.py` and `doc/requirements.txt` into `generated-doc/out/` on every sbt run, both locations are updated together to keep them in sync.

**Tech Stack:** Python, Sphinx 7.3.7, sphinx-llms-txt, myst-parser

## Global Constraints

- Do not change any pinned version numbers in `requirements.txt` other than adding the new package unpinned
- `sphinx-llms-txt` is added without a version pin (latest compatible) — pin it after verifying locally if the CI requires it
- The `.venv` directory lives inside `generated-doc/out/` and must not be committed

---

### Task 1: Wire in sphinx-llms-txt and verify locally

**Files:**
- Modify: `doc/requirements.txt`
- Modify: `doc/conf.py:44` (extensions), `doc/conf.py:89` (exclude_patterns)
- Modify: `generated-doc/out/requirements.txt`
- Modify: `generated-doc/out/conf.py:44` (extensions), `generated-doc/out/conf.py:89` (exclude_patterns)

**Interfaces:**
- Produces: `generated-doc/out/_build/html/llms.txt` and `generated-doc/out/_build/html/llms-full.txt` after a local `sphinx-build` run

- [ ] **Step 1: Add sphinx-llms-txt to both requirements files**

In `doc/requirements.txt`, replace:
```
sphinx_rtd_theme==2.0.0
sphinx==7.3.7
sphinx-autobuild==2024.4.16
myst-parser==2.0.0
```
with:
```
sphinx_rtd_theme==2.0.0
sphinx==7.3.7
sphinx-autobuild==2024.4.16
myst-parser==2.0.0
sphinx-llms-txt
```

Apply the identical change to `generated-doc/out/requirements.txt`.

- [ ] **Step 2: Update extensions list in both conf.py files**

In `doc/conf.py` line 44, replace:
```python
extensions = ['myst_parser', 'sphinx_rtd_theme']
```
with:
```python
extensions = ['myst_parser', 'sphinx_rtd_theme', 'sphinx_llms_txt']
```

Apply the identical change to `generated-doc/out/conf.py` line 44.

- [ ] **Step 3: Add llms.txt config block in both conf.py files**

In `doc/conf.py`, after line 46 (`myst_enable_extensions = ['attrs_block']`), insert:
```python
llms_txt_title = "Tapir"
llms_txt_summary = "Declarative, type-safe web endpoints library for Scala"
llms_txt_full_file = True
```

Apply the identical change to `generated-doc/out/conf.py` after its line 46.

- [ ] **Step 4: Add .venv to exclude_patterns in both conf.py files**

In `doc/conf.py` line 89, replace:
```python
exclude_patterns = ['_build', 'Thumbs.db', '.DS_Store']
```
with:
```python
exclude_patterns = ['_build', 'Thumbs.db', '.DS_Store', '.venv']
```

Apply the identical change to `generated-doc/out/conf.py` line 89.

- [ ] **Step 5: Install the new dependency into the local venv**

```bash
cd generated-doc/out
source .venv/bin/activate   # if venv doesn't exist yet: python -m venv .venv && source .venv/bin/activate
pip install sphinx-llms-txt
```

Expected: pip installs `sphinx-llms-txt` and its dependencies without errors.

- [ ] **Step 6: Run sphinx-build and verify outputs**

```bash
cd generated-doc/out
source .venv/bin/activate
sphinx-build -b html . _build/html
```

Expected: build completes with `build succeeded`. No `.venv`-related "document isn't included in any toctree" warnings (the `.venv` exclusion from Step 4 eliminates them).

Then verify the output files exist:
```bash
ls _build/html/llms.txt _build/html/llms-full.txt
```

Expected:
```
_build/html/llms.txt
_build/html/llms-full.txt
```

Also spot-check the content of `llms.txt`:
```bash
head -20 _build/html/llms.txt
```

Expected: starts with `# Tapir`, followed by the summary line, then a list of section links. Locally, `READTHEDOCS_CANONICAL_URL` is not set so `html_baseurl` is empty — links will be relative paths, not full `https://tapir.softwaremill.com/...` URLs. That's expected and correct; absolute URLs appear only in the ReadTheDocs build.

- [ ] **Step 7: Commit**

```bash
git add doc/requirements.txt doc/conf.py generated-doc/out/requirements.txt generated-doc/out/conf.py
git commit -m "Add sphinx-llms-txt to generate llms.txt and llms-full.txt"
```

Do not commit `generated-doc/out/_build/` or `generated-doc/out/.venv/`.
