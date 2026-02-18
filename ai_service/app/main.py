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
    try:
        sql = generate_sql_from_question(
            question=req.question,
            schema=req.schema,
            language=req.language,
            context=req.context
        )
        return {"sql": sql}
    except Exception as e:
        traceback.print_exc()  # terminalde full hata
        raise HTTPException(status_code=500, detail=str(e))