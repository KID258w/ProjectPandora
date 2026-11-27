import test from 'node:test'
import assert from 'node:assert/strict'
import {
  getProfileFieldErrors,
  normalizeDisplayName,
  normalizeNationalId,
  validateDisplayName,
  validateNationalId,
  validatePhone,
} from './profileValidation.js'

test('姓名校验接受中英文 Unicode 姓名和常见分隔符', () => {
  assert.equal(validateDisplayName('李 · O’Neil-Éva'), '')
  assert.equal(validateDisplayName(' 阿布·都沙拉木 '), '')
  assert.equal(normalizeDisplayName('  e\u0301  '), 'é')
})

test('姓名校验限制规范化后的长度并拒绝数字、标点和 emoji', () => {
  assert.equal(validateDisplayName('名'), '')
  assert.equal(validateDisplayName('名'.repeat(50)), '')
  assert.match(validateDisplayName('名'.repeat(51)), /1–50/)
  for (const value of ['', ' ', '李1', '安🙂', 'A@B', '---']) {
    assert.notEqual(validateDisplayName(value), '', `应拒绝姓名 ${value}`)
  }
})

test('手机号只接受去除首尾空格后的 11 位 ASCII 数字', () => {
  assert.equal(validatePhone('01234567890'), '')
  assert.equal(validatePhone(' 01234567890 '), '')
  for (const value of ['', '1234567890', '012345678901', '０１２３４５６７８９０', '1380000000a']) {
    assert.notEqual(validatePhone(value), '', `应拒绝手机号 ${value}`)
  }
})

test('身份证号验证长度、日期、校验码并规范化小写 x', () => {
  const valid = '99000020000229001X'
  assert.equal(validateNationalId(valid), '')
  assert.equal(validateNationalId(` ${valid.toLowerCase()} `), '')
  assert.equal(normalizeNationalId(` ${valid.toLowerCase()} `), valid)
  assert.match(validateNationalId('990000200102290017'), /出生日期无效/)
  assert.match(validateNationalId('990000200002290010'), /校验码不正确/)
  assert.notEqual(validateNationalId('99000020000229001'), '')
})

test('后端个人资料错误映射到对应字段，非字段错误保留通用提示', () => {
  const error = { response: { data: { data: {
    display_name: ['姓名格式不合法。'],
    username: '该手机号已被使用。',
    id_number: ['身份证号校验码不正确。', '请检查后重试。'],
    business_role: ['角色无效'],
  } } } }
  assert.deepEqual(getProfileFieldErrors(error), {
    display_name: '姓名格式不合法。',
    username: '该手机号已被使用。',
    id_number: '身份证号校验码不正确。；请检查后重试。',
  })
  assert.deepEqual(getProfileFieldErrors(new Error('network error')), {})
})
