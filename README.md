# NL2SQL Automatic E-Commerce Data Analysis and Visualization

## Project Overview

This project is an AI-supported Natural Language to SQL (NL2SQL) system developed for e-commerce data analysis and visualization.

The system enables users to query a relational database using natural language in both Turkish and English without requiring SQL knowledge.

Natural language questions are automatically converted into SQL queries using a Large Language Model (LLM). Query results are then retrieved from the database and presented through tabular and graphical visualizations.

---

## Technologies Used

### Backend

* Java Spring Boot
* Maven

### AI Service

* Python
* FastAPI
* LangChain
* OpenAI GPT-4.1-mini

### Database

* PostgreSQL

### Frontend

* React
* Vite
* Tailwind CSS

---

## System Architecture

The system consists of four main layers:

1. Frontend Interface
2. Spring Boot Backend
3. AI Service (FastAPI + LangChain)
4. PostgreSQL Database

Workflow:

User Question → Frontend → Backend → AI Service → SQL Generation → Database Query → Result Visualization

---

## Features

* Natural language querying
* Turkish and English language support
* Automatic SQL generation
* Schema-aware query generation
* SQL security validation
* Visualization of query results
* E-commerce dataset support

---

## Security Mechanisms

To prevent harmful database operations:

* Only SELECT statements are allowed.
* INSERT, UPDATE, DELETE and DROP operations are blocked.
* Generated SQL queries are validated before execution.
* Multiple statement execution is prevented.

---

## Project Structure

```text
nl2sql-tez/

├── agent/
├── ai_service/
├── backend/
├── frontend/
├── database/
├── data/
├── notebooks/
├── src/
├── main.py
├── requirements.txt
```

---

## Installation

### AI Service

Install Python dependencies:

```bash
pip install -r requirements.txt
```

Create a .env file:

```env
OPENAI_API_KEY=your_api_key
```

Run FastAPI service:

```bash
uvicorn main:app --reload
```

---

### Backend

Navigate to backend directory:

```bash
cd backend
```

Run Spring Boot application:

```bash
./mvnw spring-boot:run
```

---

### Frontend

Navigate to frontend directory:

```bash
cd frontend
```

Install dependencies:

```bash
npm install
```

Run development server:

```bash
npm run dev
```

---

## Author

Gökçe Soylu

Computer Engineering Department

Aydın Adnan Menderes University

2026
