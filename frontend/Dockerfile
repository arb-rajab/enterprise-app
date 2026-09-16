# syntax=docker/dockerfile:1

### Build stage ###
FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npx ng build --configuration production

### Runtime stage ###
FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# The stock image's nginx user already exists; just make it own the paths it needs to write to
# so nginx can run fully unprivileged, and move off the privileged port 80.
RUN touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/cache/nginx /var/run/nginx.pid /usr/share/nginx/html /etc/nginx/conf.d

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
