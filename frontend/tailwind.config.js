/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        obsidian: {
          50: '#f6f6f7',
          100: '#e1e2e6',
          200: '#c3c5cc',
          300: '#9ea1ab',
          400: '#797d8a',
          500: '#5e6270',
          600: '#4a4d59',
          700: '#3d3f49',
          800: '#34363d',
          900: '#1a1b1f',
          950: '#0d0e10',
        },
      },
      fontFamily: {
        mono: ['JetBrains Mono', 'Fira Code', 'SF Mono', 'Menlo', 'monospace'],
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
      },
    },
  },
  plugins: [],
}

