# llms.txt Generation for Tapir Docs

**Date:** 2026-06-24  
**Status:** Approved

## Goal

Generate `llms.txt` and `llms-full.txt` files as part of the Tapir Sphinx documentation build, making the docs consumable by LLMs via the [llmstxt.org](https://llmstxt.org) standard. The files will be served alongside the existing HTML docs at `https://tapir.softwaremill.com/en/latest/`.

## Background

The Tapir documentation pipeline:

1. Source markdown lives in `doc/` (with mdoc directives for Scala code execution)
2. sbt + mdoc processes `doc/` → `generated-doc/out/`, executing Scala snippets and copying static files (including `conf.py` and `requirements.txt`)
3. ReadTheDocs runs Sphinx on `generated-doc/out/` using `generated-doc/out/conf.py` and `generated-doc/out/requirements.txt`

Because mdoc copies non-markdown files from `doc/` to `generated-doc/out/`, `doc/conf.py` and `doc/requirements.txt` are the canonical sources. Changes there propagate on the next mdoc run.

## Tool

[sphinx-llms-txt](https://github.com/jdillard/sphinx-llms-txt) — a Sphinx extension that hooks into the normal HTML build and emits `llms.txt` (an index with section titles and links) and `llms-full.txt` (all doc content concatenated) into the HTML output directory.

## File Changes

Four files are modified. `doc/` files are the canonical sources; `generated-doc/out/` files are mirrors updated at the same time so local testing works without running a full sbt+mdoc build.

### `doc/requirements.txt` and `generated-doc/out/requirements.txt`

Add:
```
sphinx-llms-txt
```

### `doc/conf.py` and `generated-doc/out/conf.py`

Two changes:

**1. Add the extension and configuration:**
```python
extensions = ['myst_parser', 'sphinx_rtd_theme', 'sphinx_llms_txt']

llms_txt_title = "Tapir"
llms_txt_summary = "Declarative, type-safe web endpoints library for Scala"
llms_txt_full_file = True
```

**2. Add `.venv` to `exclude_patterns`** to prevent Sphinx from scanning the local virtualenv as source files:
```python
exclude_patterns = ['_build', 'Thumbs.db', '.DS_Store', '.venv']
```

## Local Testing Workflow

```bash
cd generated-doc/out
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
sphinx-build -b html . _build/html
```

Verify outputs:
- `generated-doc/out/_build/html/llms.txt`
- `generated-doc/out/_build/html/llms-full.txt`

## ReadTheDocs Integration

No changes to `.readthedocs.yaml` are needed. ReadTheDocs already installs from `generated-doc/out/requirements.txt` and runs Sphinx on `generated-doc/out/conf.py`. Adding `sphinx-llms-txt` to requirements.txt is sufficient for it to be included in the ReadTheDocs build.

The base URL for links in `llms.txt` is read automatically from `html_baseurl`, which `conf.py` already sets from the `READTHEDOCS_CANONICAL_URL` environment variable — so links will resolve to `https://tapir.softwaremill.com/en/latest/` in production without any extra config.

The generated files will be served at:
- `https://tapir.softwaremill.com/en/latest/llms.txt`
- `https://tapir.softwaremill.com/en/latest/llms-full.txt`

## Out of Scope

- Customizing which pages are included/excluded from llms.txt (use defaults for now)
- Adding a link to llms.txt from the HTML docs
- Version-specific llms.txt files
