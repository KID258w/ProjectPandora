from functools import lru_cache

from cryptography.fernet import Fernet, InvalidToken
from django.conf import settings
from django.core.exceptions import ImproperlyConfigured
from django.db import models


@lru_cache(maxsize=4)
def _fernet_for_key(key):
    try:
        return Fernet(key.encode("ascii"))
    except (UnicodeEncodeError, TypeError, ValueError) as exc:
        raise ImproperlyConfigured("IDENTITY_NUMBER_ENCRYPTION_KEY must be a URL-safe base64 Fernet key.") from exc


class EncryptedTextField(models.TextField):
    """Store text as an authenticated Fernet token and expose plaintext to model code."""

    description = "Authenticated encrypted text"

    def _fernet(self):
        return _fernet_for_key(settings.IDENTITY_NUMBER_ENCRYPTION_KEY)

    def from_db_value(self, value, expression, connection):
        if value is None:
            return None
        try:
            return self._fernet().decrypt(value.encode("ascii")).decode("utf-8")
        except (InvalidToken, UnicodeDecodeError, UnicodeEncodeError) as exc:
            raise ImproperlyConfigured(
                "Stored identity-number data cannot be decrypted; verify the encryption key."
            ) from exc

    def get_prep_value(self, value):
        value = super().get_prep_value(value)
        if value is None:
            return None
        return self._fernet().encrypt(value.encode("utf-8")).decode("ascii")
