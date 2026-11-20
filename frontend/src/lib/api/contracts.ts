export type SmartCityRole = 'ADMIN' | 'CITY_MANAGER' | 'LOCAL_STAFF' | 'RESIDENT' | 'SUPERVISOR'

export type LoginRequest = {
  email: string
  password: string
}

export type CurrentUser = {
  id: string
  displayName: string
  email: string
  roles: SmartCityRole[]
}

export type CsrfResponse = {
  token: string
}

export type HealthStatus = {
  status: string
  service: string
}

export type BackendError = {
  error?: string
  code?: string
}
