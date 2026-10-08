import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.tsx'
import { NoticeProvider } from './lib/notice'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <NoticeProvider>
        <App />
      </NoticeProvider>
    </BrowserRouter>
  </StrictMode>,
)
