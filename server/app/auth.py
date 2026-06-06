"""Signup/login: password hashing, token issuing, and the auth routes."""
from __future__ import annotations

import logging
import re
from datetime import datetime, timedelta, timezone
from functools import wraps

import jwt
from flask import Blueprint, jsonify, request
from werkzeug.security import check_password_hash, generate_password_hash

from .errors import BadRequestError, ConflictError, UnauthorizedError
from .users import UserRepository

log = logging.getLogger("auth")

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
MIN_PASSWORD_LEN = 6


class AuthService:
    def __init__(self, users: UserRepository, jwt_secret: str, ttl_hours: int = 12):
        self._users = users
        self._secret = jwt_secret
        self._ttl = timedelta(hours=ttl_hours)

    def signup(self, email: str, password: str) -> str:
        email, password = self._validate(email, password)
        created = self._users.create(email, generate_password_hash(password))
        if not created:
            raise ConflictError(f"Email already registered: {email}")
        log.info("new user %s", email)
        return self._make_token(email)

    def login(self, email: str, password: str) -> str:
        email, password = self._validate(email, password)
        user = self._users.find(email)
        if user is None or not check_password_hash(user["password_hash"], password):
            raise UnauthorizedError("Invalid email or password")
        return self._make_token(email)

    def verify(self, token: str) -> str:
        """Return the email in a valid token, or raise UnauthorizedError."""
        try:
            payload = jwt.decode(token, self._secret, algorithms=["HS256"])
            return payload["sub"]
        except jwt.ExpiredSignatureError:
            raise UnauthorizedError("Token expired")
        except (jwt.InvalidTokenError, KeyError):
            raise UnauthorizedError("Invalid token")

    def _make_token(self, email: str) -> str:
        now = datetime.now(timezone.utc)
        return jwt.encode({"sub": email, "iat": now, "exp": now + self._ttl},
                          self._secret, algorithm="HS256")

    @staticmethod
    def _validate(email, password):
        if not email or not EMAIL_RE.match(email.strip()):
            raise BadRequestError("A valid email is required")
        if not password or len(password) < MIN_PASSWORD_LEN:
            raise BadRequestError(f"Password must be at least {MIN_PASSWORD_LEN} characters")
        return email.strip().lower(), password


def bearer_token() -> str | None:
    header = request.headers.get("Authorization", "")
    if header.startswith("Bearer "):
        return header[len("Bearer "):].strip()
    return None


def require_token(auth: AuthService):
    """Decorator that rejects requests without a valid bearer token."""
    def decorator(fn):
        @wraps(fn)
        def wrapper(*args, **kwargs):
            token = bearer_token()
            if not token:
                raise UnauthorizedError("Missing bearer token")
            auth.verify(token)
            return fn(*args, **kwargs)
        return wrapper
    return decorator


def build_auth_blueprint(auth: AuthService) -> Blueprint:
    bp = Blueprint("auth", __name__, url_prefix="/api/v1/auth")

    @bp.post("/signup")
    def signup():
        """Register a new user and return a token.
        ---
        tags: [auth]
        consumes: [application/json]
        parameters:
          - in: body
            name: body
            required: true
            schema:
              type: object
              required: [email, password]
              properties:
                email: {type: string, example: user@example.com}
                password: {type: string, example: secret123}
        responses:
          200: {description: Created, returns a token}
          400: {description: Invalid email/password}
          409: {description: Email already registered}
        """
        body = request.get_json(silent=True) or {}
        token = auth.signup(body.get("email"), body.get("password"))
        return jsonify(token=token, email=(body.get("email") or "").strip().lower())

    @bp.post("/login")
    def login():
        """Log in and return a token.
        ---
        tags: [auth]
        consumes: [application/json]
        parameters:
          - in: body
            name: body
            required: true
            schema:
              type: object
              required: [email, password]
              properties:
                email: {type: string, example: user@example.com}
                password: {type: string, example: secret123}
        responses:
          200: {description: OK, returns a token}
          400: {description: Invalid input}
          401: {description: Wrong credentials}
        """
        body = request.get_json(silent=True) or {}
        token = auth.login(body.get("email"), body.get("password"))
        return jsonify(token=token, email=(body.get("email") or "").strip().lower())

    return bp
