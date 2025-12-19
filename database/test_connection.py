from db_config import engine
from sqlalchemy import text

def test_connection():
    try:
        with engine.connect() as conn:
            result = conn.execute(text("SELECT 'Bağlantı başarılı! Docker çalışıyor.'"))
            print(result.fetchone()[0])
    except Exception as e:
        print("Bağlantı hatası:", e)

if __name__ == "__main__":
    test_connection()
