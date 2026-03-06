import os
import re
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

# Debug log (ENV: SQL_AGENT_DEBUG=1)
DEBUG = os.getenv("SQL_AGENT_DEBUG", "0") == "1"


# === Türkçe kolon adları / alias mapping ===
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


def schema_to_text(schema) -> str:
    """
    schema (FastAPI request'ten gelir):
    [
      {"name": "customers", "columns": ["customer_id", "name", ...]},
      ...
    ]
    """
    if not schema:
        return "SCHEMA NOT PROVIDED"

    lines = []
    for t in schema:
        name = t.get("name") if isinstance(t, dict) else getattr(t, "name", "")
        cols = t.get("columns") if isinstance(t, dict) else getattr(t, "columns", [])
        lines.append(f"TABLE {name}")
        for c in cols or []:
            lines.append(f"  - {c}")
        lines.append("")
    return "\n".join(lines)


def apply_aliases_to_question(question: str) -> str:
    """
    Kullanıcının Türkçe sorusundaki alan ifadelerini kolon adlarına çevirir.
    Basit replace kullanır.
    """
    q = (question or "").lower()
    for turkce, kolon in COLUMN_ALIASES.items():
        if turkce in q:
            q = q.replace(turkce, kolon)
    return q


# LLM (LangChain)
llm = ChatOpenAI(
    model="gpt-4.1-mini",
    temperature=0,
    api_key=OPENAI_API_KEY,
)


def _extract_sql_from_llm_output(content: str) -> str:
    """
    LLM çıktısından SQL'i çıkarır:
    - ```sql ... ``` varsa onu alır
    - yoksa tüm içeriği alır
    Ayrıca:
    - baştaki/sondaki boşlukları temizler
    - en sonda tek bir ';' bırakacak şekilde normalize eder
    - çoklu statement riskini azaltmak için ilk ';' sonrası kırpar (defense-in-depth)
    """
    if not content:
        return ""

    # Code block içini çek
    idx = content.find("```sql")
    if idx != -1:
        start = content.find("\n", idx)
        end = content.find("```", start)
        sql = content[start:end].strip() if start != -1 and end != -1 else content.strip()
    else:
        sql = content.strip()

    # Çoklu statement varsa ilk ';' sonrası at (backend zaten engelliyor ama extra koruma)
    # (Sondaki ; hariç) -> ilk ; bulunduğunda sadece ilk statement kalsın.
    semi = sql.find(";")
    if semi != -1:
        sql = sql[:semi + 1].strip()

    # Eğer hiç ';' yoksa ekleme (backend limit eklerken kendisi ; ekliyor)
    # Ama tek statement normalize etmek için sonuna ; eklemek de sorun değil.
    # Backend'in semicolon check'i: "sondaki ; hariç ; varsa hata"
    # -> burada sadece tek ';' bırakıyoruz.
    if not sql.endswith(";"):
        sql = sql.strip()
        # Yalnızca SELECT ise ; ekle
        if sql.lower().startswith("select"):
            sql = sql + ";"

    return sql.strip()


def _ensure_select_only(sql: str) -> str:
    """
    Backend zaten kontrol ediyor; ama AI service tarafında da uyumluluk için:
    - SQL'in SELECT ile başlamasını zorunlu kıl
    - Başta BOM/whitespace gibi şeyler varsa temizle
    """
    if not sql:
        return ""

    # Başta BOM vs temizle
    sql = sql.lstrip("\ufeff").strip()

    # Bazı LLM'ler 'SQL:' gibi prefix koyabilir; onu ayıkla
    sql = re.sub(r"^\s*(sql\s*:\s*)", "", sql, flags=re.IGNORECASE).strip()

    # Eğer hala select ile başlamıyorsa, boş dönelim (backend 502/400 verebilir)
    if not sql.lower().startswith("select"):
        return ""

    return sql


def generate_sql_from_question(
    question: str,
    schema=None,
    language: str = "tr",
    context=None
) -> str:
    """
    FastAPI /generate-sql endpoint'inin çağırdığı ana fonksiyon.
    - DB'ye bağlanmaz
    - Sadece SELECT SQL üretir
    - schema: backend'den gelen tablo/kolon listesi
    - context: şimdilik opsiyonel ( backend şu an null gönderiyor)
    """
    normalized_question = apply_aliases_to_question(question)
    schema_text = schema_to_text(schema)

    # Context şimdilik opsiyonel (ileride backend context gönderirse kullanılır)
    context_text = ""
    if context:
        context_text = f"\nÖNCEKİ BAĞLAM:\n{context}\n"

    system_prompt = f"""
Sen bir PostgreSQL uzmanısın ve e-ticaret veritabanı için SQL sorguları üretiyorsun.

Veritabanı şeması:
{schema_text}

===========================================================
KESİN TALİMATLAR
===========================================================
1) SADECE SELECT sorgusu üret. (INSERT/UPDATE/DELETE/DROP vb. ASLA yazma)
2) Açıklama yazma, sadece SQL döndür.
3) Çıktı HER ZAMAN ```sql ... ``` bloğu içinde olsun.
4) JOIN gerekiyorsa uygun foreign key kolonlarını kullan.
5) LIMIT yoksa uygun bir LIMIT ekle (örn: 10/50/100).
6) Şemada olmayan kolonları KULLANMA.
7) Tek statement üret. (Birden fazla sorgu yazma.)
"""

    user_prompt = f"""{context_text}
SORU ({language}):
{normalized_question}
"""

    messages = [
        {"role": "system", "content": system_prompt.strip()},
        {"role": "user", "content": user_prompt.strip()},
    ]

    response = llm.invoke(messages)
    content = getattr(response, "content", "")

    if DEBUG:
        print("LLM RAW OUTPUT:\n", content)

    sql = _extract_sql_from_llm_output(content)
    sql = _ensure_select_only(sql)

    # Son güvenlik: boşsa boş döndür
    return sql
