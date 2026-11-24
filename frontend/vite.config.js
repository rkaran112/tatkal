import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// While developing, any request starting with /api is passed on to the Spring Boot backend.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
