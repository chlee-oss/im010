import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 백오피스 화면 개발 서버: /admin/api 요청은 im010-admin(8081)으로 넘긴다.
// 운영에서는 nginx가 같은 역할을 한다 (deploy/nginx/im010-admin.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/admin/api': process.env.IM010_ADMIN_API ?? 'http://localhost:8081',
    },
  },
})
