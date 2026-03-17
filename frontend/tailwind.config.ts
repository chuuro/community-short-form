import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        bg: {
          primary: '#07070F',
          secondary: '#0F0F1A',
          card: '#13131F',
        },
        brand: {
          purple: '#7C3AED',
          blue: '#2563EB',
          pink: '#EC4899',
          cyan: '#06B6D4',
        },
      },
      backgroundImage: {
        'gradient-brand': 'linear-gradient(135deg, #7C3AED 0%, #2563EB 50%, #06B6D4 100%)',
        'gradient-card': 'linear-gradient(135deg, rgba(124,58,237,0.15) 0%, rgba(37,99,235,0.1) 100%)',
        'gradient-hero': 'radial-gradient(ellipse 80% 60% at 50% -10%, rgba(124,58,237,0.35) 0%, transparent 70%)',
      },
      animation: {
        'spin-slow': 'spin 3s linear infinite',
        'pulse-slow': 'pulse 3s ease-in-out infinite',
        'gradient-shift': 'gradientShift 4s ease infinite',
        'slide-up': 'slideUp 0.5s ease-out',
        'fade-in': 'fadeIn 0.4s ease-out',
      },
      keyframes: {
        gradientShift: {
          '0%, 100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
        },
        slideUp: {
          from: { opacity: '0', transform: 'translateY(24px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        fadeIn: {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};

export default config;
