const NAME_SEPARATORS = new Set([' ', '·', '・', '‧', '-', '‐', '‑', "'", '’'])
const ID_WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
const ID_CHECK_DIGITS = '10X98765432'
const PROFILE_FIELDS = ['display_name', 'username', 'id_number']

export function normalizeDisplayName(value) {
  return String(value ?? '').normalize('NFC').trim()
}

export function normalizeNationalId(value) {
  return String(value ?? '').trim().replace(/x/g, 'X')
}

export function validateDisplayName(value) {
  const name = normalizeDisplayName(value)
  const chars = Array.from(name)
  if (chars.length < 1 || chars.length > 50) return '姓名长度须为 1–50 个字符。'
  if (!chars.some((char) => /\p{L}/u.test(char)) || !chars.every((char) => /\p{L}/u.test(char) || NAME_SEPARATORS.has(char))) {
    return '姓名仅允许 Unicode 字母及常见分隔符（空格、中点、连字符和撇号）。'
  }
  return ''
}

export function validatePhone(value) {
  return /^[0-9]{11}$/.test(String(value ?? '').trim()) ? '' : '手机号须为 11 位数字。'
}

export function validateNationalId(value) {
  const idNumber = normalizeNationalId(value)
  if (!/^[0-9]{17}[0-9X]$/.test(idNumber)) return '身份证号须为 18 位，末位为数字或 X。'

  const year = Number(idNumber.slice(6, 10))
  const month = Number(idNumber.slice(10, 12))
  const day = Number(idNumber.slice(12, 14))
  const birthday = new Date(0)
  birthday.setUTCHours(0, 0, 0, 0)
  birthday.setUTCFullYear(year, month - 1, day)
  if (birthday.getUTCFullYear() !== year || birthday.getUTCMonth() !== month - 1 || birthday.getUTCDate() !== day) {
    return '身份证号中的出生日期无效。'
  }

  const checksum = [...idNumber.slice(0, 17)].reduce((sum, digit, index) => sum + Number(digit) * ID_WEIGHTS[index], 0)
  if (ID_CHECK_DIGITS[checksum % 11] !== idNumber[17]) return '身份证号校验码不正确。'
  return ''
}

export function getProfileFieldErrors(error) {
  const details = error?.response?.data?.data
  if (!details || typeof details !== 'object' || Array.isArray(details)) return {}

  return Object.fromEntries(PROFILE_FIELDS.flatMap((field) => {
    if (!(field in details)) return []
    const values = Array.isArray(details[field]) ? details[field].flat(Infinity) : [details[field]]
    const message = values.filter((value) => typeof value === 'string' && value.trim()).join('；')
    return message ? [[field, message]] : []
  }))
}
