from fastapi import FastAPI
from api import router

from app_logging import log_requests
from handlers import (
    AppException,
    app_exception_handler,
    validation_exception_handler,
    generic_exception_handler
)

from fastapi.exceptions import RequestValidationError

app = FastAPI(title="My FastAPI App")

# Register middleware
app.middleware("http")(log_requests)

# Register exception handlers
app.add_exception_handler(AppException, app_exception_handler)
app.add_exception_handler(RequestValidationError, validation_exception_handler)
app.add_exception_handler(Exception, generic_exception_handler)

app.include_router(router)

@app.get("/")
def root():
    return {"message": "API is running"}