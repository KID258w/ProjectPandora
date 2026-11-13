import { h } from 'vue'
import { ElMessageBox } from 'element-plus'

function splitErrorDetails(message) {
  return String(message || '请求未能完成，请稍后重试。')
    .split(/[；;。\n]+/)
    .map((part) => part.trim())
    .filter(Boolean)
}

export function showOperationError(title, message) {
  const details = splitErrorDetails(message)

  return ElMessageBox({
    title,
    message: h('div', { class: 'operation-feedback' }, [
      h('div', { class: 'operation-feedback__heading' }, [
        h('span', { class: 'operation-feedback__icon', 'aria-hidden': 'true' }, '!'),
        h('div', { class: 'operation-feedback__heading-copy' }, [
          h('p', { class: 'operation-feedback__eyebrow' }, '操作未完成'),
          h('h2', { class: 'operation-feedback__title' }, title),
        ]),
      ]),
      h('section', { class: 'operation-feedback__reason', 'aria-label': '失败原因' }, [
        h('p', { class: 'operation-feedback__reason-label' }, '失败原因'),
        h('ul', { class: 'operation-feedback__details' }, details.map((detail) =>
          h('li', { class: 'operation-feedback__detail' }, [
            h('span', { class: 'operation-feedback__bullet', 'aria-hidden': 'true' }),
            h('p', detail),
          ]),
        )),
      ]),
      h('p', { class: 'operation-feedback__hint' }, '请根据以上提示处理后重试。'),
    ]),
    customClass: 'operation-feedback-dialog',
    modalClass: 'operation-feedback-mask',
    showClose: true,
    showConfirmButton: true,
    showCancelButton: false,
    confirmButtonText: '我知道了',
    confirmButtonType: 'primary',
    confirmButtonClass: 'operation-feedback__confirm',
    closeOnClickModal: false,
    closeOnPressEscape: true,
    center: false,
    lockScroll: true,
  }).catch(() => undefined)
}
