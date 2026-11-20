import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { loadTlsConfiguration } from './config/tls.mjs'

export default defineConfig(({ command, mode }) => {
  const base = {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  }

  if (command !== 'serve') return base

  const environment = {
    ...loadEnv(mode, process.cwd(), 'SMARTCITY360_'),
    ...process.env,
  }
  const tls = loadTlsConfiguration(environment)

  return {
    ...base,
    server: {
      host: 'localhost',
      port: 5173,
      strictPort: true,
      https: {
        cert: tls.viteCertificate,
        key: tls.vitePrivateKey,
      },
      proxy: {
        '/smartcity360/api': {
          target: 'https://localhost:8181',
          agent: tls.payaraAgent,
          changeOrigin: false,
          secure: true,
          ws: false,
        },
      },
    },
  }
})
