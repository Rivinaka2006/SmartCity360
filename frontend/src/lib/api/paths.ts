export const API_BASE_PATH = '/smartcity360/api/v1'

export const apiPaths = {
  health: `${API_BASE_PATH}/health`,
  dashboard: {
    summary: `${API_BASE_PATH}/dashboard/summary`,
  },
  auth: {
    csrf: `${API_BASE_PATH}/auth/csrf`,
    login: `${API_BASE_PATH}/auth/login`,
    logout: `${API_BASE_PATH}/auth/logout`,
    me: `${API_BASE_PATH}/auth/me`,
  },
} as const
