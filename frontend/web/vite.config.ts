import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발 서버: /api · /go 요청은 Spring Boot(im010-api, 8080)로 넘긴다.
// 운영에서는 nginx가 같은 역할을 한다 (deploy/nginx/im010.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/go': 'http://localhost:8080',
    },
  },
})
