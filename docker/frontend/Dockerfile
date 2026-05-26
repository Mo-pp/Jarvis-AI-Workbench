# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS build

WORKDIR /app

COPY jarvis-frontend/package*.json ./
RUN npm ci

COPY jarvis-frontend/ ./

ARG VITE_API_URL=/api/claude
ARG VITE_AUTH_API_URL=/api/auth
ARG VITE_FILE_API_URL=/api/files
ARG VITE_RESUME_EXPORT_API_URL=/api/resume/export
ARG VITE_SKILL_API_URL=/api/skills
ARG VITE_RESOURCE_API_URL=/api/resources

ENV VITE_API_URL=$VITE_API_URL \
    VITE_AUTH_API_URL=$VITE_AUTH_API_URL \
    VITE_FILE_API_URL=$VITE_FILE_API_URL \
    VITE_RESUME_EXPORT_API_URL=$VITE_RESUME_EXPORT_API_URL \
    VITE_SKILL_API_URL=$VITE_SKILL_API_URL \
    VITE_RESOURCE_API_URL=$VITE_RESOURCE_API_URL

RUN npm run build

FROM nginx:1.27-alpine

COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
