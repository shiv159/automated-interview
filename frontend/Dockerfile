FROM node:24.18.0 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY angular.json proxy.conf.json tsconfig.json tsconfig.app.json ./
COPY src src
RUN npm run build

FROM nginx:1.29-alpine
RUN apk add --no-cache gettext
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/automated-interview/browser /usr/share/nginx/html
COPY docker-entrypoint.sh /docker-entrypoint.d/40-backend-upstream.sh
RUN chmod +x /docker-entrypoint.d/40-backend-upstream.sh
EXPOSE 8080
