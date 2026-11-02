import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './styles/global.css'
import './styles/pages.css'
import './styles/plan-list.css'
import './styles/ai.css'
import App from './App.tsx'
import EnvBadge from './components/EnvBadge'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <EnvBadge />
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
