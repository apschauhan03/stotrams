import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Local dev talks to the Spring Boot backend on :9303 through this proxy, so the browser
// sees everything as same-origin (no CORS setup needed on the backend).
//
//   /backend/*  -> http://localhost:9303/*   (API + admin API; prefix stripped)
//   /images/*   -> http://localhost:9303/images/*
//   /videos/*   -> http://localhost:9303/videos/*   (manually uploaded videos)
//
// /images and /videos only work if the backend builds image URLs against this origin — start it with
// MEDIA_BASE_URL=http://localhost:5173 (see README). If the backend isn't running at all,
// the app falls back to built-in demo content.
const BACKEND = 'http://localhost:9303';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/backend': {
        target: BACKEND,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/backend/, ''),
      },
      '/images': { target: BACKEND, changeOrigin: true },
      '/videos': { target: BACKEND, changeOrigin: true },
    },
  },
});
