# --- [공통 스테이지] ---
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .

ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL

# --- [1. 개발용 스테이지: development] ---
FROM builder AS development
EXPOSE 8060
CMD ["npm", "run", "dev", "--", "--host", "--port", "8060"]

# --- [2. 배포용 스테이지] ---
# FROM builder AS build-stage
# RUN npm run build

# FROM nginx:stable-alpine AS production
# COPY --from=build-stage /app/dist /usr/share/nginx/html
# EXPOSE 80
# CMD ["nginx", "-g", "daemon off;"]