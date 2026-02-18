from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Any, Optional, List, Dict
import traceback

from agent.sql_agent import generate_sql_from_question

app = FastAPI(title="NL2SQL AI Service", version="1.0")


class SQLRequest(BaseModel):
    question: str
    language: Optional[str] = "tr"
    schema: Optional[List[Dict[str, Any]]] = None
    context: Optional[Dict[str, Any]] = None


class SQLResponse(BaseModel):
    sql: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/generate-sql", response_model=SQLResponse)
def generate_sql(req: SQLRequest):
    # ✅ backend memory context geldiyse prompt'a ekle
    q = req.question

    if req.context:
        last_q = req.context.get("last_question")
        last_sql = req.context.get("last_sql")
        last_err = req.context.get("last_error")
        last_preview = req.context.get("last_rows_preview")

        context_block = "ÖNCEKİ BAĞLAM:\n"
        if last_q:
            context_block += f"- last_question: {last_q}\n"
        if last_sql:
            context_block += f"- last_sql: {last_sql}\n"
        if last_err:
            context_block += f"- last_error: {last_err}\n"
        if last_preview:
            context_block += f"- last_rows_preview:\n{last_preview}\n"

        q = context_block + "\nŞİMDİKİ SORU:\n" + req.question

    sql = generate_sql_from_question(q)
    return {"sql": sql}

