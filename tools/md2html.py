# Конвертер docs/*.md → *.html со стилями (светлая/тёмная тема)
# Запуск: python tools/md2html.py docs/design.md
import io, re, sys
import markdown

CSS = """
:root{--bg:#fbfaf7;--fg:#1f2328;--muted:#5d646d;--line:#e2ded6;--accent:#b4532a;--code:#f1eee8;--note:#fff4e5}
@media (prefers-color-scheme: dark){:root:not([data-theme="light"]){--bg:#17181a;--fg:#e6e3de;--muted:#9a9fa6;--line:#33353a;--accent:#e8875a;--code:#22242a;--note:#2a2218}}
:root[data-theme="dark"]{--bg:#17181a;--fg:#e6e3de;--muted:#9a9fa6;--line:#33353a;--accent:#e8875a;--code:#22242a;--note:#2a2218}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:860px;margin:0 auto;padding:32px 16px 64px}
h1{font-size:2rem;margin:0 0 .25em}
h2{font-size:1.3rem;margin:2.2em 0 .6em;padding-top:.6em;border-top:1px solid var(--line);color:var(--accent)}
h1+p{color:var(--muted);margin-top:0}
table{border-collapse:collapse;width:100%;margin:1em 0;font-size:.93rem;display:block;overflow-x:auto}
th,td{border:1px solid var(--line);padding:6px 10px;text-align:left;vertical-align:top}
th{background:var(--code)}
code{background:var(--code);padding:1px 5px;border-radius:4px;font-size:.9em}
pre{background:var(--code);padding:14px;border-radius:8px;overflow-x:auto}
pre code{padding:0;background:none}
blockquote{margin:1em 0;padding:10px 16px;background:var(--note);border-left:4px solid var(--accent);border-radius:4px}
blockquote p{margin:0}
"""

for src in sys.argv[1:]:
    text = io.open(src, encoding="utf-8").read()
    title = re.search(r"^# (.+)$", text, re.M).group(1)
    body = markdown.markdown(text, extensions=["tables", "fenced_code"])
    html = (f'<!doctype html>\n<html lang="ru"><head><meta charset="utf-8">'
            f'<meta name="viewport" content="width=device-width,initial-scale=1">'
            f'<title>{title}</title><style>{CSS}</style></head><body><main>\n{body}\n</main></body></html>\n')
    io.open(src[:-3] + ".html", "w", encoding="utf-8").write(html)
    print("ok", src)
