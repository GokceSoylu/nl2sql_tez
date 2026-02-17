from fastapi import FastAPI
from pydantic import BaseModel
from agent.sql_agent import generate_sql_from_question  # sadece SQL üretimi
# NOT: Tez-2 mimarisinde DB çalıştırma Spring Boot'ta olacak.

app = FastAPI(title="NL2SQL AI Service", version="1.0")

class SQLRequest(BaseModel):
    question: str
    context: dict | None = None  # opsiyonel (memory için)

class SQLResponse(BaseModel):
    sql: str

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/generate-sql", response_model=SQLResponse)
def generate_sql(req: SQLRequest):
    # context şu an opsiyonel; tez-2’de genişletilebilir
    sql = generate_sql_from_question(req.question)
    return {"sql": sql}
