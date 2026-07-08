import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
      '@style': resolve(__dirname, './src/style'),
      '@sdui': resolve(__dirname, './src/sdui'),
      '@ui': resolve(__dirname, './src/ui'),
    },
  },
  server: {
    port: 3000,
    open: true,
  },
})
