import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8000'
export const ACCESS_TOKEN_KEY = 'pandora_admin_access_token'
export const REFRESH_TOKEN_KEY = 'pandora_admin_refresh_token'

export const http = axios.create({
  baseURL: `${API_BASE_URL.replace(/\/$/, '')}/api/v1`,
  timeout: 10000,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((config) => {
  const accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

export function unwrapResponse(response) {
  return response.data?.data
}

export async function fetchAllPages(request, params = {}) {
  const results = []
  let page = 1
  let count = Infinity
  while (results.length < count) {
    const response = await request({ ...params, page, page_size: 100 })
    const pageData = unwrapResponse(response)
    results.push(...(pageData?.results ?? []))
    count = pageData?.count ?? results.length
    if (!pageData?.next) break
    page += 1
  }
  return results
}

export function getApiErrorMessage(error) {
  const body = error.response?.data
  const details = body?.data
  const fieldMessages = details && typeof details === 'object'
    ? Object.entries(details).flatMap(([field, messages]) => {
        const values = Array.isArray(messages) ? messages : [messages]
        return values.map((message) => `${field === 'non_field_errors' ? '' : `${field}: `}${message}`)
      })
    : []
  if (fieldMessages.length) return fieldMessages.join('；')
  return body?.message || details?.detail || error.message || '请求失败，请稍后重试。'
}

export const adminApi = {
  login: (credentials) => http.post('/admin/auth/token/', credentials),
  refresh: (refresh) => axios.post(`${API_BASE_URL.replace(/\/$/, '')}/api/v1/admin/auth/token/refresh/`, { refresh }, { timeout: 10000 }),
  me: () => http.get('/admin/me/'),
  departments: (params = {}) => http.get('/admin/departments/', { params: { page_size: 100, ...params } }),
  department: (id) => http.get(`/admin/departments/${id}/`),
  updateDepartment: (id, data) => http.patch(`/admin/departments/${id}/`, data),
  createDepartment: (data) => http.post('/admin/departments/', data),
  teams: (params = {}) => http.get('/admin/teams/', { params: { page_size: 100, ...params } }),
  team: (id) => http.get(`/admin/teams/${id}/`),
  updateTeam: (id, data) => http.patch(`/admin/teams/${id}/`, data),
  createTeam: (data) => http.post('/admin/teams/', data),
  users: (params = {}) => http.get('/admin/users/', { params: { page_size: 100, ...params } }),
  user: (id) => http.get(`/admin/users/${id}/`),
  updateUser: (id, data) => http.patch(`/admin/users/${id}/`, data),
  createUser: (data) => http.post('/admin/users/', data),
  dashboard: () => http.get('/admin/dashboard/'),
  auditLogs: (params = {}) => http.get('/admin/audit-logs/', { params: { page_size: 100, ...params } }),
}

let refreshPromise
http.interceptors.response.use((response) => response, async (error) => {
  const request = error.config
  if (error.response?.status !== 401 || !request || request._retry || request.url?.includes('/auth/token')) {
    return Promise.reject(error)
  }
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!refreshToken) {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    return Promise.reject(error)
  }
  request._retry = true
  try {
    refreshPromise ??= adminApi.refresh(refreshToken).then((response) => response.data.data.access).finally(() => { refreshPromise = null })
    const accessToken = await refreshPromise
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    request.headers.Authorization = `Bearer ${accessToken}`
    return http(request)
  } catch (refreshError) {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    window.dispatchEvent(new Event('pandora:auth-expired'))
    return Promise.reject(refreshError)
  }
})
