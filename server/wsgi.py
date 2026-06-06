"""Entry point.

    dev:  python wsgi.py
    prod: gunicorn -w 4 -b 0.0.0.0:8080 wsgi:app
"""
from __future__ import annotations

import os

from app import create_app

app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "8080")),
            debug=os.getenv("FLASK_DEBUG") == "1")
