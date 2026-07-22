FROM node:22.17.0-alpine3.22@sha256:fc3e945f920b7e3000cd1af86c4ae406ec70c72f328b667baf0f3a8910d69eed AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.30.4-alpine3.24@sha256:97d490c12ba55b4946b01546d1c3ed324e8d41ab1c9fcb2a616aa470620e5b46

ARG QHERP_SOURCE_COMMIT=unknown
LABEL org.opencontainers.image.title="QH ERP Web" \
      org.opencontainers.image.revision="${QHERP_SOURCE_COMMIT}"

COPY nginx.production.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
