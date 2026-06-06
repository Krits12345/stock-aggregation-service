"""API errors and the handlers that turn them into JSON responses."""
from __future__ import annotations

from flask import Flask, jsonify


class ApiError(Exception):
    status_code = 500
    error = "internal_error"

    def __init__(self, message, status_code=None, error=None):
        super().__init__(message)
        self.message = message
        if status_code is not None:
            self.status_code = status_code
        if error is not None:
            self.error = error

    def to_dict(self):
        return {"error": self.error, "message": self.message, "status": self.status_code}


class BadRequestError(ApiError):
    status_code = 400
    error = "bad_request"


class NotFoundError(ApiError):
    status_code = 404
    error = "not_found"


class UnauthorizedError(ApiError):
    status_code = 401
    error = "unauthorized"


class ConflictError(ApiError):
    status_code = 409
    error = "conflict"


def register_error_handlers(app: Flask) -> None:
    @app.errorhandler(ApiError)
    def _api_error(exc: ApiError):
        return jsonify(exc.to_dict()), exc.status_code

    @app.errorhandler(404)
    def _not_found(_exc):
        return jsonify(error="not_found", message="Resource not found", status=404), 404

    @app.errorhandler(405)
    def _method_not_allowed(_exc):
        return jsonify(error="method_not_allowed", message="Method not allowed", status=405), 405

    @app.errorhandler(Exception)
    def _unexpected(exc: Exception):
        app.logger.exception("unhandled error: %s", exc)
        return jsonify(error="internal_error", message="An unexpected error occurred", status=500), 500
