import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./lib/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        surface: {
          50: "#fafafa",
          100: "#f4f4f5",
          200: "#e4e4e7",
          300: "#d4d4d8",
          400: "#a1a1aa",
          500: "#71717a",
          600: "#52525b",
          700: "#3f3f46",
          800: "#27272a",
          850: "#1e1e22",
          900: "#18181b",
          950: "#09090b",
        },
        state: {
          ready: {
            bg: "rgba(16, 185, 129, 0.1)",
            border: "rgba(16, 185, 129, 0.3)",
            text: "#34d399",
            solid: "#10b981",
          },
          blocked: {
            bg: "rgba(239, 68, 68, 0.1)",
            border: "rgba(239, 68, 68, 0.3)",
            text: "#f87171",
            solid: "#ef4444",
          },
          warning: {
            bg: "rgba(245, 158, 11, 0.1)",
            border: "rgba(245, 158, 11, 0.3)",
            text: "#fbbf24",
            solid: "#f59e0b",
          },
          success: {
            bg: "rgba(34, 197, 94, 0.1)",
            border: "rgba(34, 197, 94, 0.3)",
            text: "#4ade80",
            solid: "#22c55e",
          },
          neutral: {
            bg: "rgba(161, 161, 170, 0.1)",
            border: "rgba(161, 161, 170, 0.2)",
            text: "#a1a1aa",
            solid: "#71717a",
          },
        },
      },
      fontFamily: {
        sans: ["Inter", "-apple-system", "BlinkMacSystemFont", "Segoe UI", "Roboto", "sans-serif"],
        mono: ["JetBrains Mono", "SFMono-Regular", "Menlo", "Monaco", "Consolas", "monospace"],
      },
      letterSpacing: {
        tighter: "-0.04em",
        tight: "-0.02em",
      },
    },
  },
  plugins: [],
};

export default config;
