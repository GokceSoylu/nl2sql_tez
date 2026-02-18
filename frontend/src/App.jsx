import { useEffect, useMemo, useState } from "react";

// Proxy kullanıyorsan boş bırak:
// const API = "";
// Proxy yoksa 8081:
const API = import.meta.env.VITE_API_URL || "http://localhost:8081";

const STR = {
  tr: {
    title: "NL2SQL Akıllı Sorgu Sistemi",
    subtitle: "Doğal dil → SQL → Sonuç",
    question: "Doğal Dil Sorgusu",
    placeholder: "Örn: İlk 3 müşteriyi customer_id’e göre sırala",
    gen: "SQL Üret",
    run: "Çalıştır",
    sql: "Üretilen SQL",
    results: "Sonuçlar",
    schema: "Veritabanı Şeması",
    tables: "Tablolar",
    columns: "Kolonlar",
    tips: "Örnek Sorgular",
    loading: "Yükleniyor...",
    empty: "Henüz sonuç yok.",
    errTitle: "Hata",
    errHint:
      "İpucu: AI bazen olmayan kolon kullanabilir. Daha net yaz veya schema desteğiyle tekrar dene.",
  },
  en: {
    title: "NL2SQL Intelligent Query System",
    subtitle: "Natural language → SQL → Results",
    question: "Natural Language Query",
    placeholder: "e.g., List the first 3 customers ordered by customer_id",
    gen: "Generate SQL",
    run: "Run",
    sql: "Generated SQL",
    results: "Results",
    schema: "Database Schema",
    tables: "Tables",
    columns: "Columns",
    tips: "Example Queries",
    loading: "Loading...",
    empty: "No results yet.",
    errTitle: "Error",
    errHint:
      "Tip: The AI may reference a non-existing column. Try a clearer query or retry with schema.",
  },
};

function toCSV(rows) {
  if (!rows || rows.length === 0) return "";
  const cols = Object.keys(rows[0]);

  const esc = (v) => {
    const s = String(v ?? "");
    if (s.includes('"') || s.includes(",") || s.includes("\n")) {
      return `"${s.replaceAll('"', '""')}"`;
    }
    return s;
  };

  const header = cols.map(esc).join(",");
  const body = rows
    .map((r) => cols.map((c) => esc(r[c])).join(","))
    .join("\n");

  return `${header}\n${body}`;
}

function downloadText(filename, text, mime = "text/plain;charset=utf-8") {
  const blob = new Blob([text], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

async function copyToClipboard(text) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const ta = document.createElement("textarea");
  ta.value = text;
  ta.style.position = "fixed";
  ta.style.left = "-9999px";
  document.body.appendChild(ta);
  ta.select();
  document.execCommand("copy");
  ta.remove();
}

function Pill({ children }) {
  return (
    <span className="inline-flex items-center rounded-full border border-slate-700 bg-slate-900 px-3 py-1 text-xs text-slate-200">
      {children}
    </span>
  );
}

function ResultsTable({ rows }) {
  if (!rows || rows.length === 0) return null;
  const cols = Object.keys(rows[0]);

  return (
    <div className="overflow-auto rounded-2xl border border-slate-800 bg-slate-950">
      <table className="min-w-full text-sm">
        <thead className="sticky top-0 bg-slate-900/80 backdrop-blur border-b border-slate-800">
          <tr>
            {cols.map((c) => (
              <th
                key={c}
                className="px-4 py-3 text-left font-semibold text-slate-200 whitespace-nowrap"
              >
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="odd:bg-slate-950 even:bg-slate-950/50">
              {cols.map((c) => (
                <td
                  key={c}
                  className="px-4 py-3 text-slate-100 whitespace-nowrap"
                >
                  {String(r[c] ?? "")}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function App() {
  const [lang, setLang] = useState("tr");
  const t = useMemo(() => STR[lang], [lang]);

  const [schema, setSchema] = useState(null);
  const [selectedTable, setSelectedTable] = useState("");

  const [question, setQuestion] = useState("");
  const [sql, setSql] = useState("");
  const [rows, setRows] = useState([]);

  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        setErr("");
        const res = await fetch(`${API}/api/v1/schema`);
        const data = await res.json();
        if (!res.ok)
          throw new Error(data?.error || data?.message || `HTTP ${res.status}`);
        setSchema(data);
        if (data?.tables?.length) setSelectedTable(data.tables[0].name);
      } catch (e) {
        setErr(String(e.message || e));
      }
    })();
  }, []);

  const selected = useMemo(() => {
    const tables = schema?.tables || [];
    return tables.find((x) => x.name === selectedTable) || null;
  }, [schema, selectedTable]);

  async function post(endpoint) {
    setBusy(true);
    setErr("");

    try {
      const body = {
        question,
        language: lang,
        schema: schema?.tables || [],
      };

      const res = await fetch(`${API}${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      const data = await res.json().catch(() => ({}));

      if (data?.sql) setSql(data.sql);

      if (!res.ok) {
        const msg = data?.error || data?.message || `HTTP ${res.status}`;
        throw new Error(msg);
      }

      setRows(Array.isArray(data.rows) ? data.rows : []);
      setErr(data?.error || "");
    } catch (e) {
      setRows([]);
      setErr(String(e.message || e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      {/* Header */}
      <div className="border-b border-slate-800 bg-slate-950/70 backdrop-blur">
        <div className="mx-auto max-w-7xl px-6 py-5 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="h-11 w-11 rounded-2xl bg-gradient-to-br from-indigo-500 to-cyan-400" />
            <div>
              <div className="text-xl font-bold">{t.title}</div>
              <div className="text-sm text-slate-400">{t.subtitle}</div>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Pill>PostgreSQL</Pill>
            <Pill>Spring Boot</Pill>
            <Pill>AI</Pill>
            <select
              value={lang}
              onChange={(e) => setLang(e.target.value)}
              className="rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
            >
              <option value="tr">TR</option>
              <option value="en">EN</option>
            </select>
          </div>
        </div>
      </div>

      {/* Body */}
      <div className="mx-auto max-w-7xl px-6 py-6 grid grid-cols-12 gap-6">
        {/* Left */}
        <div className="col-span-12 lg:col-span-8 space-y-6">
          {/* Query */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/40 p-6">
            <div className="text-sm font-semibold text-slate-200 mb-2">
              {t.question}
            </div>

            <div className="flex flex-col md:flex-row gap-3">
              <input
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                placeholder={t.placeholder}
                className="flex-1 rounded-xl bg-slate-950 border border-slate-800 px-4 py-3 text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/60"
              />
              <button
                disabled={busy || !question.trim()}
                onClick={() => post("/api/v1/nl2sql")}
                className="rounded-xl px-4 py-3 text-sm font-semibold bg-slate-100 text-slate-950 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {busy ? t.loading : t.gen}
              </button>
              <button
                disabled={busy || !question.trim()}
                onClick={() => post("/api/v1/ask")}
                className="rounded-xl px-4 py-3 text-sm font-semibold bg-indigo-500 hover:bg-indigo-400 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {busy ? t.loading : t.run}
              </button>
            </div>

            {/* Buton açıklamaları */}
            <div className="mt-2 flex flex-col md:flex-row gap-3 text-xs text-slate-400">
              <div className="flex-1" />
              <div className="w-full md:w-auto md:min-w-[110px] text-left md:text-center">
                Sadece SQL üretir
              </div>
              <div className="w-full md:w-auto md:min-w-[110px] text-left md:text-center">
                SQL üretir + DB’de çalıştırır
              </div>
            </div>

            {/* Hata kutusu */}
            {err && (
              <div className="mt-4 rounded-2xl border border-red-900/40 bg-red-950/30 px-4 py-3">
                <div className="text-sm font-semibold text-red-200">
                  {t.errTitle}
                </div>
                <div className="text-sm text-red-100/90 mt-1 break-words">
                  {err}
                </div>
                <div className="text-xs text-red-200/70 mt-2">
                  {t.errHint}
                </div>
              </div>
            )}
          </div>

          {/* SQL */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/40 p-6">
            <div className="flex items-center justify-between mb-2">
              <div className="text-sm font-semibold text-slate-200">{t.sql}</div>

              <div className="flex items-center gap-2">
                {copied && (
                  <span className="text-xs text-emerald-300">Kopyalandı ✅</span>
                )}
                <button
                  disabled={!sql}
                  onClick={async () => {
                    try {
                      await copyToClipboard(sql);
                      setCopied(true);
                      setTimeout(() => setCopied(false), 1200);
                    } catch (e) {
                      setErr("Kopyalama başarısız: " + String(e?.message || e));
                    }
                  }}
                  className="rounded-xl border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 hover:bg-slate-900 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Kopyala
                </button>
              </div>
            </div>

            <pre className="rounded-xl bg-slate-950 border border-slate-800 p-4 text-sm overflow-auto min-h-[120px]">
              {sql || "-"}
            </pre>
          </div>

          {/* Results */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/40 p-6">
            <div className="flex items-center justify-between mb-3">
              <div className="text-sm font-semibold text-slate-200">
                {t.results}
              </div>

              <div className="flex items-center gap-2">
                <button
                  disabled={!rows?.length}
                  onClick={() => {
                    const csv = toCSV(rows);
                    const ts = new Date().toISOString().replaceAll(":", "-");
                    downloadText(
                      `nl2sql_results_${ts}.csv`,
                      csv,
                      "text/csv;charset=utf-8"
                    );
                  }}
                  className="rounded-xl border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 hover:bg-slate-900 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  CSV indir
                </button>

                <Pill>{rows?.length ? `${rows.length} rows` : "0 rows"}</Pill>
              </div>
            </div>

            {rows?.length ? (
              <ResultsTable rows={rows} />
            ) : (
              <div className="text-sm text-slate-400">{t.empty}</div>
            )}
          </div>
        </div>

        {/* Right */}
        <div className="col-span-12 lg:col-span-4 space-y-6">
          {/* Schema */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/40 p-6">
            <div className="text-sm font-semibold text-slate-200 mb-3">
              {t.schema}
            </div>

            {!schema ? (
              <div className="text-sm text-slate-400">{t.loading}</div>
            ) : (
              <div className="grid gap-4">
                <div>
                  <div className="text-xs font-semibold text-slate-400 mb-2">
                    {t.tables}
                  </div>
                  <div className="max-h-56 overflow-auto rounded-xl border border-slate-800 bg-slate-950">
                    {(schema.tables || []).map((tb) => {
                      const active = tb.name === selectedTable;
                      return (
                        <button
                          key={tb.name}
                          onClick={() => setSelectedTable(tb.name)}
                          className={
                            "w-full text-left px-4 py-2 text-sm border-b border-slate-900 " +
                            (active
                              ? "bg-indigo-500/15 text-indigo-200"
                              : "hover:bg-slate-900/60 text-slate-200")
                          }
                        >
                          {tb.name}
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div>
                  <div className="text-xs font-semibold text-slate-400 mb-2">
                    {t.columns}
                  </div>
                  <div className="rounded-xl border border-slate-800 bg-slate-950 p-3 max-h-64 overflow-auto">
                    {selected ? (
                      <div className="flex flex-wrap gap-2">
                        {selected.columns.map((c) => (
                          <span
                            key={c}
                            className="text-xs rounded-full border border-slate-800 bg-slate-900 px-2.5 py-1 text-slate-200"
                          >
                            {c}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <div className="text-sm text-slate-400">-</div>
                    )}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Tips */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/40 p-6">
            <div className="text-sm font-semibold text-slate-200 mb-3">
              {t.tips}
            </div>
            <ul className="text-sm text-slate-400 space-y-2 list-disc pl-5">
              <li>İlk 3 müşteriyi customer_id’e göre sırala</li>
              <li>En yüksek puanlı 5 ürünü getir</li>
              <li>Son 5 siparişi listele</li>
              <li>Top 3 supplier’ı listele</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
