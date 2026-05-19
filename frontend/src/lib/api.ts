import axios from 'axios'

export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Interceptor de request — injeta o token JWT em toda requisição autenticada
api.interceptors.request.use((config) => {
  // localStorage só existe no browser — checa antes de acessar
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('domus:token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

// Interceptor de response — redireciona para login se o token expirar
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && typeof window !== 'undefined') {
      localStorage.removeItem('domus:token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)