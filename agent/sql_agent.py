import os
from dotenv import load_dotenv
from sqlalchemy import create_engine, text, inspect
from langchain_openai import ChatOpenAI
import pandas as pd
import matplotlib.pyplot as plt

load_dotenv()

DB_USER = os.getenv("DB_USER")
DB_PASSWORD = os.getenv("DB_PASSWORD")
DB_HOST = os.getenv("DB_HOST")
DB_PORT = os.getenv("DB_PORT")
DB_NAME = os.getenv("DB_NAME")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

DATABASE_URL = f"postgresql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
engine = create_engine(DATABASE_URL)


def get_schema_description() -> str:
    """Veritabanı şemasını otomatik çıkar (tablo & kolon isimleri)."""
    insp = inspect(engine)
    lines = []
    for table_name in insp.get_table_names():
        lines.append(f"TABLE {table_name}")
        cols = insp.get_columns(table_name)
        for col in cols:
            col_name = col["name"]
            col_type = str(col["type"])
            lines.append(f"  - {col_name}: {col_type}")
        lines.append("")
    return "\n".join(lines)


SCHEMA_TEXT = get_schema_description()

# === Türkçe kolon adları / alias mapping ===
# Kullanıcının "müşteri adı" demesi → customer_name kolonuna map edelim gibi.
COLUMN_ALIASES = {
    # Customers
    "müşteri adı": "name",
    "müşteri ismi": "name",
    "müşteri mail": "email",
    "mail": "email",
    "telefon": "phone",
    "cinsiyet": "gender",
    "doğum tarihi": "birth_date",
    "kayıt tarihi": "register_date",

    # Addresses
    "adres": "address_line",
    "şehir": "city",
    "il": "city",
    "ülke": "country",
    "posta kodu": "postal_code",

    # Products
    "ürün adı": "name",
    "ürün fiyatı": "price",
    "ürün açıklaması": "description",
    "stok": "stock",
    "ürün puanı": "rating",
    "kategori": "category_id",
    "marka": "brand_id",

    # Categories
    "kategori adı": "category_name",
    "üst kategori": "parent_category_id",

    # Brands
    "marka adı": "brand_name",

    # Orders
    "sipariş tarihi": "order_date",
    "kargolama tarihi": "ship_date",
    "sipariş durumu": "status",
    "kargo adresi": "shipping_address_id",

    # Order Items
    "adet": "quantity",
    "miktar": "quantity",
    "liste fiyatı": "list_price",
    "indirim": "discount_amount",

    # Payments
    "ödeme tarihi": "payment_date",
    "ödeme yöntemi": "payment_method",
    "ödeme tutarı": "amount",
    "tutar": "amount",

    # Reviews
    "yorum": "comment",
    "yorum tarihi": "review_date",
    "puan": "rating",

    # Suppliers / Product Suppliers
    "tedarikçi": "supplier_id",
    "tedarikçi fiyatı": "cost_price",

    # Shippers
    "kargo şirketi": "shipper_id",
    "kargo firması": "shipper_name",
    "kargo takip numarası": "tracking_number",

    # Shipments
    "gönderi tarihi": "shipment_date",
    "kargo maliyeti": "freight_cost",
}


def apply_aliases_to_question(question: str) -> str:
    """Kullanıcının Türkçe sorusundaki alan isimlerini bilinen kolonlara çevir."""
    q = question.lower()
    for turkce, kolon in COLUMN_ALIASES.items():
        if turkce in q:
            q = q.replace(turkce, kolon)
    return q


# LLM
llm = ChatOpenAI(
    model="gpt-4.1-mini",
    temperature=0,
    api_key=OPENAI_API_KEY,
)


def generate_sql_from_question(question: str) -> str:
    """
    1) Türkçe soruyu alias'lardan geçir
    2) Gelişmiş Türkçe system prompt ile SQL üret
    """
    normalized_question = apply_aliases_to_question(question)

    system_prompt = f"""
Sen bir PostgreSQL uzmanısın ve e-ticaret veritabanı için SQL sorguları üretiyorsun.

Elindeki veritabanı şeması:

{SCHEMA_TEXT}

===========================================================
❗ KESİN VE DEĞİŞTİRİLEMEZ TALİMATLAR ❗
===========================================================

1) Kullanıcı SELECT türü bir soru sorarsa:
   → SADECE SELECT sorgusu üret.

2) Kullanıcı aşağıdaki kelimelerden birini kullanırsa:
   "sil", "tabloyu sil", "kaldır", 
   "delete", "drop", "truncate",
   "her şeyi sil", "tümünü sil"

   → MUTLAKA destructive SQL üret:
     - DELETE FROM ...
     - DROP TABLE ...
     - TRUNCATE TABLE ...

   ⚠ ASLA SELECT ile değiştirme.
   ⚠ ASLA açıklama ekleme.
   ⚠ ASLA uyarı veya yorum satırı ekleme.
   ⚠ SADECE saf SQL çıktısı üret.

   ÖRNEK:
   "products tablosunu sil" →  DROP TABLE products;
   "products içindeki tüm ürünleri sil" → DELETE FROM products;

3) Çıktı formatı:
   → HER ZAMAN ```sql ... ``` bloğu içinde OLACAK.

4) join gerektiğinde doğru foreign key'leri kullan.

5) Tahmin edemiyorsan en mantıklı SQL'i üret.

===========================================================

Lütfen sadece saf SQL çıktısı üret.
"""


    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": normalized_question},
    ]

    response = llm.invoke(messages)
    content = response.content
    print("LLM RAW OUTPUT:\n", content)#new 


    start = content.find("```sql")
    if start != -1:
        start = content.find("\n", start)
        end = content.find("```", start)
        sql = content[start:end].strip()
    else:
        sql = content.strip()

    return sql

# ============================================================
# SQL SAFETY FILTER
# ============================================================

DANGEROUS_SQL_KEYWORDS = [
    "insert", "update", "delete", "drop", "alter", "truncate",
    "create", "replace", "rename"
]

def is_sql_safe(sql: str) -> bool:
    """Destructive SQL komutlarını tespit eder. Yorum satırlarını da kontrol eder."""
    sql_lower = sql.lower()

    # Yorumları kaldır ( -- ile başlayan satırlar )
    lines = sql_lower.split("\n")
    cleaned_lines = [line.split("--")[0].strip() for line in lines]
    cleaned_sql = " ".join(cleaned_lines)

    # Destructive keyword kontrolü
    return not any(keyword in cleaned_sql for keyword in DANGEROUS_SQL_KEYWORDS)


def run_sql(sql: str):
    """SQL'i veritabanında çalıştır ve sonucu döndür."""
    with engine.connect() as conn:
        result = conn.execute(text(sql))
        rows = result.fetchall()
        columns = result.keys()
    return [dict(zip(columns, row)) for row in rows]


def try_fix_sql_on_error(sql: str, error_message: str, question: str):
    """
    Hatalı SQL geldiğinde, hatayı LLM'e açıklayıp düzeltmesini iste.
    Bu kısım: 'hatalı sorgu düzeltme mekanizması'
    """
    fix_prompt = f"""
Aşağıda PostgreSQL için üretilmiş bir SQL sorgusu var, fakat hata verdi.

Orijinal Türkçe soru:
{question}

Üretilen SQL:
{sql}

Hata mesajı:
{error_message}

Görevin:
- Bu hatayı düzelten, geçerli ve çalışan YENİ bir SQL sorgusu üret.
- Yine sadece SELECT sorgusu yaz.
- Yine ```sql ... ``` bloğu içinde ver.
"""

    messages = [
        {"role": "system", "content": fix_prompt},
        {"role": "user", "content": "Lütfen hatayı düzeltilmiş yeni SQL sorgusunu yaz."},
    ]
    response = llm.invoke(messages)
    content = response.content

    start = content.find("```sql")
    if start != -1:
        start = content.find("\n", start)
        end = content.find("```", start)
        fixed_sql = content[start:end].strip()
    else:
        fixed_sql = content.strip()

    return fixed_sql

# ============================================================
# MEMORY SISTEMI
# ============================================================

class SQLMemory:
    """
    En son yapılan sorgu, en son üretilen SQL, en son sonuç gibi bilgileri saklar.
    Bu, bağlamlı (contextual) sorgular için temel hafızadır.
    """

    def __init__(self):
        self.last_question = None
        self.last_sql = None
        self.last_result = None

    def save(self, question, sql, result):
        self.last_question = question
        self.last_sql = sql
        self.last_result = result

    def has_memory(self):
        return self.last_question is not None

    def get_context(self):
        return {
            "last_question": self.last_question,
            "last_sql": self.last_sql,
            "last_result": self.last_result,
        }


# global memory instance:
memory = SQLMemory()


def ask(question: str):
    GRAPH_KEYWORDS = ["grafik", "çiz", "chart", "görselleştir", "plot"]

    def is_graph_request(question: str):
        return any(word in question.lower() for word in GRAPH_KEYWORDS)

    context_text = ""
    if memory.has_memory():
        ctx = memory.get_context()
        context_text = f"""
ÖNCEKİ SORU: {ctx['last_question']}
ÖNCEKİ ÜRETİLEN SQL: {ctx['last_sql']}
ÖNCEKİ SONUÇ: {ctx['last_result']}
"""

    new_question = context_text + "\nŞİMDİKİ SORU: " + question
    sql = generate_sql_from_question(new_question)

    print("\n--- Üretilen SQL ---")
    print(sql)
    print("--------------------\n")

    # 🔥 GÜVENLİK FİLTRESİ → TRY BLOĞUNDAN ÖNCE OLMALI
    if not is_sql_safe(sql):
        return {
            "uyari": "Bu sorgu güvenlik nedeniyle engellendi (destructive SQL tespit edildi).",
            "uretilen_sql": sql
        }

    # SQL çalıştırma
    try:
        rows = run_sql(sql)

    except Exception as e:
        print("İlk SQL hata verdi, düzeltmeyi deniyorum...")
        fixed_sql = try_fix_sql_on_error(sql, str(e), question)

        print("\n--- Düzeltilmiş SQL ---")
        print(fixed_sql)
        print("-----------------------\n")

        if not is_sql_safe(fixed_sql):
            return {
                "uyari": "Düzeltilen SQL destructive olduğu için engellendi.",
                "duzeltilen_sql": fixed_sql
            }

        rows = run_sql(fixed_sql)
        sql = fixed_sql

    # Grafik çizme
    if is_graph_request(question):
        df = dataframe_from_result(rows)
        cols = df.columns.tolist()
        x = cols[0] if cols else None
        y = cols[-1] if cols else None
        print(f"\nGrafik oluşturuluyor... X={x}, Y={y}")
        plot_dataframe(df, x=x, y=y)

    memory.save(question, sql, rows)
    return rows





def dataframe_from_result(rows: list):
    """LLM'den dönen SQL sonuçlarını Pandas DataFrame'e dönüştürür."""
    if not rows:
        return pd.DataFrame()
    return pd.DataFrame(rows)


def plot_dataframe(df: pd.DataFrame, x=None, y=None, kind="bar", title="Grafik"):
    """Her türlü tabloyu otomatik grafiğe dönüştüren motor."""
    
    if df.empty:
        print("Grafik oluşturulamadı: DataFrame boş.")
        return
    
    plt.figure(figsize=(10,5))
    
    if kind == "bar":
        df.plot(kind="bar", x=x, y=y, legend=False)
    elif kind == "line":
        df.plot(kind="line", x=x, y=y)
    elif kind == "pie":
        df.set_index(x)[y].plot(kind="pie", autopct="%1.1f%%")
    else:
        df.plot()
    
    plt.title(title)
    plt.xlabel(x)
    plt.ylabel(y)
    plt.tight_layout()
    plt.show()
