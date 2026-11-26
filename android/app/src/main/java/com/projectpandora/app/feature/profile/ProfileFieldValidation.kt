package com.projectpandora.app.feature.profile

import java.text.Normalizer

internal data class ProfileFieldErrors(
    val name: String? = null,
    val phone: String? = null,
    val idNumber: String? = null,
) {
    val hasErrors: Boolean get() = name != null || phone != null || idNumber != null
}

internal fun normalizeProfileName(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)

internal fun normalizeProfileIdNumber(value: String): String = value.trim().uppercase()

internal fun validateProfileFields(name: String?, phone: String?, idNumber: String?): ProfileFieldErrors {
    val normalizedName = name?.let(::normalizeProfileName)
    val normalizedId = idNumber?.let(::normalizeProfileIdNumber)
    return ProfileFieldErrors(
        name = normalizedName?.takeIf { !isValidProfileName(it) }
            ?.let { "姓名须为 1–50 个字符，仅允许 Unicode 字母及常见姓名分隔符。" },
        phone = phone?.takeUnless { it.matches(Regex("[0-9]{11}")) }?.let { "手机号须为 11 位数字。" },
        idNumber = normalizedId?.takeIf { !isValidChineseIdNumber(it) }?.let { "身份证号格式、出生日期或校验码不正确。" },
    )
}

private val NAME_SEPARATORS = setOf(' ', '·', '・', '‧', '-', '‐', '‑', '\'', '’')
private val ID_WEIGHTS = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
private const val ID_CHECK_DIGITS = "10X98765432"

private fun isValidProfileName(value: String): Boolean {
    val count = value.codePointCount(0, value.length)
    if (count !in 1..50) return false
    var hasLetter = false
    var index = 0
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        if (Character.isLetter(codePoint)) hasLetter = true
        else if (codePoint > Char.MAX_VALUE.code || codePoint.toChar() !in NAME_SEPARATORS) return false
        index += Character.charCount(codePoint)
    }
    return hasLetter
}

private fun isValidChineseIdNumber(value: String): Boolean {
    if (!value.matches(Regex("[0-9]{17}[0-9X]"))) return false
    val year = value.substring(6, 10).toInt()
    val month = value.substring(10, 12).toInt()
    val day = value.substring(12, 14).toInt()
    if (year !in 1..9999 || month !in 1..12) return false
    val daysInMonth = when (month) {
        2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    if (day !in 1..daysInMonth) return false
    val checksum = value.take(17).mapIndexed { index, digit -> digit.digitToInt() * ID_WEIGHTS[index] }.sum()
    return ID_CHECK_DIGITS[checksum % 11] == value.last()
}
