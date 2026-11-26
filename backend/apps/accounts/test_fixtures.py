def synthetic_identity(sequence):
    """Build a check-digit-valid test ID with a synthetic area code."""
    prefix = f"99000020000229{sequence:03d}"
    weights = (7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    checksum = sum(int(digit) * weight for digit, weight in zip(prefix, weights))
    return prefix + "10X98765432"[checksum % 11]
