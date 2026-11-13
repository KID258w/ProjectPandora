import { computed, ref } from 'vue'
import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, adminApi, unwrapResponse } from './http'

const currentAdmin = ref(null)
export const isAdminAuthenticated = computed(() => Boolean(localStorage.getItem(ACCESS_TOKEN_KEY)))

export async function signInAdmin(credentials) {
  const { data } = await adminApi.login(credentials)
  localStorage.setItem(ACCESS_TOKEN_KEY, data.data.access)
  localStorage.setItem(REFRESH_TOKEN_KEY, data.data.refresh)
  try {
    const me = await adminApi.me()
    currentAdmin.value = unwrapResponse(me)
    return currentAdmin.value
  } catch (error) {
    signOutAdmin()
    throw error
  }
}

export function setCurrentAdmin(admin) {
  currentAdmin.value = admin
}

export function signOutAdmin() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  currentAdmin.value = null
}

export function getCurrentAdmin() {
  return currentAdmin.value
}
